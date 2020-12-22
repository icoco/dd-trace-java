import datadog.trace.agent.test.base.AbstractPromiseTest
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.basicSpan
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeScope
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan

abstract class ScalaPromiseTestBase extends AbstractPromiseTest<Promise<Boolean>, Future<String>> {

  @Shared
  PromiseUtils promiseUtils = new PromiseUtils(getExecutionContext())

  abstract protected ExecutionContext getExecutionContext()

  @Override
  Promise<Boolean> newPromise() {
    return promiseUtils.newPromise()
  }

  @Override
  Future<String> map(Promise<Boolean> promise, Closure<String> callback) {
    return promiseUtils.map(promise, callback) as Future<String>
  }

  @Override
  void onComplete(Future<String> promise, Closure callback) {
    promiseUtils.onComplete(promise, callback)
  }

  @Override
  void complete(Promise<Boolean> promise, boolean value) {
    promise.success(value)
  }

  @Override
  boolean get(Promise<Boolean> promise) {
    return promise.future().value().get().get()
  }

  @Override
  boolean picksUpCompletingScope() {
    return true
  }

  def "doesn't propagate non async completing scope"() {
    setup:
    def promise = newPromise()

    when:
    def mapped = map(promise) {
      runUnderTrace("mapped") {}
      "$it"
    }
    onComplete(mapped) {
      assert it == "$value"
      runUnderTrace("callback") {}
    }

    runUnderTrace("other") {
      activeScope().setAsyncPropagation(false)
      complete(promise, value)
    }

    then:
    get(promise) == value
    assertTraces(3) {
      trace(1,) {
        basicSpan(it, "other")
      }
      trace(1,) {
        basicSpan(it, "mapped")
      }
      trace(1, true) {
        basicSpan(it, "callback")
      }
    }

    where:
    value << [true, false]
  }

  def "reuses span from completed promise"() {
    setup:
    def promise = newPromise()

    when:
    def otherSpan = runUnderTrace("other") {
      complete(promise, value)
      activeSpan()
    }
    def mapped = map(promise) {
      runUnderTrace("mapped") {}
      "$it"
    }
    onComplete(mapped) {
      assert it == "$value"
      runUnderTrace("callback") {}
    }

    then:
    get(promise) == value
    assertTraces(2) {
      trace(1,) {
        basicSpan(it, "other")
      }
      trace(2, true) {
        basicSpan(it, "callback", otherSpan)
        basicSpan(it, "mapped", otherSpan)
      }
    }

    where:
    value << [true, false]
  }
}

abstract class ScalaPromiseCompletionPriorityTestBase extends ScalaPromiseTestBase {
  @Override
  void configurePreAgent() {
    super.configurePreAgent()

    injectSysConfig("dd.trace.integration.scala_promise_completion_priority.enabled", "true")
  }

  @Override
  boolean completingScopePriority() {
    return true
  }
}
