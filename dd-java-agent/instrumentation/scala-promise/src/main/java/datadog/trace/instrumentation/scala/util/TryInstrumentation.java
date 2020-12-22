package datadog.trace.instrumentation.scala.util;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.context.TraceScope;
import java.util.Collections;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.util.Try;

/**
 * A Scala {@code Promise} is always completed with a {@code Try}, so if we want the completing span
 * to take priority over any spans captured while adding computations to a {@code Future} associated
 * with a {@code Promise}, then we capture the active span when the {@code Try} is created.
 */
@AutoService(Instrumenter.class)
public class TryInstrumentation extends Instrumenter.Tracing {

  public TryInstrumentation() {
    super("scala_util_try", "scala_concurrent");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("scala.util.Try");
  }

  @Override
  public Map<String, String> contextStore() {
    return singletonMap("scala.util.Try", AgentSpan.class.getName());
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return Collections.singletonMap(isConstructor(), getClass().getName() + "$Init");
  }

  @Override
  public boolean isEnabled() {
    // Only enable this if integrations have been enabled and the extra "integration"
    // scala_promise_completion_priority has been enabled specifically
    return defaultEnabled
        && Config.get()
            .isIntegrationEnabled(
                Collections.singletonList("scala_promise_completion_priority"), false);
  }

  public static final class Init {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <T> void afterInit(@Advice.This Try<T> zis) {
      final TraceScope scope = activeScope();
      if (null != scope && scope.isAsyncPropagating()) {
        AgentSpan span;
        if (scope instanceof AgentScope) {
          span = ((AgentScope) scope).span();
        } else {
          span = activeSpan();
        }
        InstrumentationContext.get(Try.class, AgentSpan.class).put(zis, span);
      }
    }
  }
}
