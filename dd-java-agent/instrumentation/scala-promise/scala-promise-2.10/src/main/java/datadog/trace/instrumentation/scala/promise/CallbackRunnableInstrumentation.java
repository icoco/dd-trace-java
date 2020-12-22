package datadog.trace.instrumentation.scala.promise;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope;
import static datadog.trace.bootstrap.instrumentation.java.concurrent.ExcludeFilter.ExcludeType.RUNNABLE;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.ExcludeFilterProvider;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.ContextStore;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.java.concurrent.AdviceUtils;
import datadog.trace.bootstrap.instrumentation.java.concurrent.ExcludeFilter;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import datadog.trace.instrumentation.scala.PromiseHelper;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.impl.CallbackRunnable;
import scala.util.Try;

@AutoService(Instrumenter.class)
public class CallbackRunnableInstrumentation extends Instrumenter.Tracing
    implements ExcludeFilterProvider {

  public CallbackRunnableInstrumentation() {
    super("scala_concurrent");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("scala.concurrent.impl.CallbackRunnable");
  }

  @Override
  public Map<String, String> contextStore() {
    Map<String, String> contextStore = new HashMap<>();
    contextStore.put("scala.concurrent.impl.CallbackRunnable", State.class.getName());
    contextStore.put("scala.util.Try", AgentSpan.class.getName());
    return contextStore;
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<MethodDescription>, String> transformations = new HashMap<>(4);
    transformations.put(isConstructor(), getClass().getName() + "$Construct");
    transformations.put(isMethod().and(named("run")), getClass().getName() + "$Run");
    transformations.put(
        isMethod().and(named("executeWithValue")), getClass().getName() + "$ExecuteWithValue");
    return unmodifiableMap(transformations);
  }

  @Override
  public Map<ExcludeFilter.ExcludeType, ? extends Collection<String>> excludedClasses() {
    // force other instrumentations (e.g. Runnable) not to deal with this type
    return singletonMap(RUNNABLE, Collections.singleton("scala.concurrent.impl.CallbackRunnable"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {"datadog.trace.instrumentation.scala.PromiseHelper"};
  }

  /** Capture the scope when the promise is created */
  public static final class Construct {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> void onConstruct(@Advice.This CallbackRunnable<T> task) {
      final TraceScope scope = activeScope();
      if (scope != null) {
        State state = State.FACTORY.create();
        state.captureAndSetContinuation(scope);
        InstrumentationContext.get(CallbackRunnable.class, State.class).put(task, state);
      }
    }
  }

  public static final class Run {
    @Advice.OnMethodEnter
    public static <T> TraceScope before(@Advice.This CallbackRunnable<T> task) {
      return AdviceUtils.startTaskScope(
          InstrumentationContext.get(CallbackRunnable.class, State.class), task);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class)
    public static void after(@Advice.Enter TraceScope scope) {
      AdviceUtils.endTaskScope(scope);
    }
  }

  public static final class ExecuteWithValue {
    @Advice.OnMethodEnter
    public static <T> void beforeExecute(
        @Advice.This CallbackRunnable<T> task, @Advice.Argument(value = 0) Try<T> resolved) {
      // about to enter an ExecutionContext so capture the scope if necessary
      // (this used to happen automatically when the RunnableInstrumentation
      // was relied on, and happens anyway if the ExecutionContext is backed
      // by a wrapping Executor (e.g. FJP, ScheduledThreadPoolExecutor)
      ContextStore<CallbackRunnable, State> rStore =
          InstrumentationContext.get(CallbackRunnable.class, State.class);
      State state = rStore.get(task);
      if (PromiseHelper.completionPriority) {
        final AgentSpan span = InstrumentationContext.get(Try.class, AgentSpan.class).get(resolved);
        State oState = state;
        state = PromiseHelper.handleSpan(span, state);
        if (state != oState) {
          rStore.put(task, state);
        }
      }
      if (null == state) {
        final TraceScope scope = activeScope();
        if (scope != null) {
          state = State.FACTORY.create();
          state.captureAndSetContinuation(scope);
          rStore.put(task, state);
        }
      }
    }
  }
}
