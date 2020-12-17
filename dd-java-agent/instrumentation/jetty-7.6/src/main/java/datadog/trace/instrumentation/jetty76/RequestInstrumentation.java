package datadog.trace.instrumentation.jetty76;

import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.eclipse.jetty.server.AbstractHttpConnection;
import org.eclipse.jetty.server.Request;

@AutoService(Instrumenter.class)
public final class RequestInstrumentation extends Instrumenter.Tracing {

  public RequestInstrumentation() {
    super("jetty");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.eclipse.jetty.server.Request");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("setContextPath").and(takesArgument(0, String.class)),
        RequestInstrumentation.class.getName() + "$SetContextPathAdvice");
    transformers.put(
        named("setServletPath").and(takesArgument(0, String.class)),
        RequestInstrumentation.class.getName() + "$SetServletPathAdvice");
    return transformers;
  }

  // Because we are processing the initial request before the contextPath is set,
  // we must update it when it is actually set.
  public static class SetContextPathAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void updateContextPath(
        @Advice.This final Request req, @Advice.Argument(0) final String contextPath) {
      if (contextPath != null) {
        Object span = req.getAttribute(DD_SPAN_ATTRIBUTE);
        if (span instanceof AgentSpan) {
          ((AgentSpan) span).setTag("servlet.context", contextPath);
        }
      }
    }
  }

  // Because we are processing the initial request before the servletPath is set,
  // we must update it when it is actually set.
  public static class SetServletPathAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void updateServletPath(
        @Advice.This final Request req, @Advice.Argument(0) final String servletPath) {
      if (servletPath != null && !servletPath.isEmpty()) { // bypass cleanup
        Object span = req.getAttribute(DD_SPAN_ATTRIBUTE);
        if (span instanceof AgentSpan) {
          ((AgentSpan) span).setTag("servlet.path", servletPath);
        }
      }
    }

    private void muzzleCheck(AbstractHttpConnection connection) {
      connection.getGenerator();
    }
  }
}
