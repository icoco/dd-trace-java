package datadog.trace.instrumentation.scala;

import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.captureSpan;

import datadog.trace.api.Config;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.bootstrap.instrumentation.java.concurrent.State;
import datadog.trace.context.TraceScope;
import java.util.Collections;

public class PromiseHelper {
  public static final boolean completionPriority =
      Config.get()
          .isIntegrationEnabled(
              Collections.singletonList("scala_promise_completion_priority"), false);

  public static State handleSpan(final AgentSpan span, State state) {
    if (completionPriority && null != span) {
      TraceScope.Continuation continuation = captureSpan(span);
      if (null != state) {
        state.closeContinuation();
      } else {
        state = State.FACTORY.create();
      }
      state.setOrCancelContinuation(continuation);
    }
    return state;
  }
}
