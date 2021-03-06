package datadog.trace.instrumentation.trace_annotation;

import static datadog.trace.agent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static datadog.trace.agent.tooling.bytebuddy.matcher.DDElementMatchers.safeHasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isEquals;
import static net.bytebuddy.matcher.ElementMatchers.isFinalizer;
import static net.bytebuddy.matcher.ElementMatchers.isGetter;
import static net.bytebuddy.matcher.ElementMatchers.isHashCode;
import static net.bytebuddy.matcher.ElementMatchers.isSetter;
import static net.bytebuddy.matcher.ElementMatchers.isSynthetic;
import static net.bytebuddy.matcher.ElementMatchers.isToString;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.api.Trace;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * TraceConfig Instrumentation does not extend Default.
 *
 * <p>Instead it directly implements Instrumenter#instrument() and adds one default Instrumenter for
 * every configured class+method-list.
 *
 * <p>If this becomes a more common use case the building logic should be abstracted out into a
 * super class.
 */
@Slf4j
@AutoService(Instrumenter.class)
public class TraceConfigInstrumentation implements Instrumenter {

  static final String PACKAGE_CLASS_NAME_REGEX = "[\\w.\\$]+";
  private static final String METHOD_LIST_REGEX =
      "\\s*(?:\\*|(?:\\w+\\s*,)*\\s*(?:\\w+\\s*,?))\\s*";
  private static final String CONFIG_FORMAT =
      "(?:\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "\\]\\s*;)*\\s*"
          + PACKAGE_CLASS_NAME_REGEX
          + "\\["
          + METHOD_LIST_REGEX
          + "\\]";

  private final Map<String, Set<String>> classMethodsToTrace;

  private boolean validateConfigString(final String configString) {
    for (final String segment : configString.split(";")) {
      if (!segment.trim().matches(CONFIG_FORMAT)) {
        return false;
      }
    }
    return true;
  }

  public TraceConfigInstrumentation() {
    final String configString = Config.get().getTraceMethods();
    if (configString == null || configString.trim().isEmpty()) {
      classMethodsToTrace = Collections.emptyMap();

    } else if (!validateConfigString(configString)) {
      log.warn(
          "Invalid trace method config '{}'. Must match 'package.Class$Name[method1,method2];*' or 'package.Class$Name[*];*'.",
          configString);
      classMethodsToTrace = Collections.emptyMap();

    } else {
      final String[] classMethods = configString.split(";", -1);
      final Map<String, Set<String>> toTrace = new HashMap<>(classMethods.length);
      for (final String classMethod : classMethods) {
        if (classMethod.trim().isEmpty()) {
          continue;
        }
        final String[] splitClassMethod = classMethod.split("\\[", -1);
        final String className = splitClassMethod[0];
        final String method = splitClassMethod[1].trim();
        final String methodNames = method.substring(0, method.length() - 1);
        final String[] splitMethodNames = methodNames.split(",", -1);
        final Set<String> trimmedMethodNames = new HashSet<>();
        for (final String methodName : splitMethodNames) {
          final String trimmedMethodName = methodName.trim();
          if (!trimmedMethodName.isEmpty()) {
            trimmedMethodNames.add(trimmedMethodName);
          }
        }
        if (!trimmedMethodNames.isEmpty()) {
          toTrace.put(className.trim(), trimmedMethodNames);
        }
      }
      classMethodsToTrace = Collections.unmodifiableMap(toTrace);
    }
  }

  @Override
  public AgentBuilder instrument(AgentBuilder agentBuilder) {
    if (classMethodsToTrace.isEmpty()) {
      return agentBuilder;
    }

    for (final Map.Entry<String, Set<String>> entry : classMethodsToTrace.entrySet()) {
      final TracerClassInstrumentation tracerConfigClass =
          new TracerClassInstrumentation(entry.getKey(), entry.getValue());
      agentBuilder = tracerConfigClass.instrument(agentBuilder);
    }
    return agentBuilder;
  }

  @Override
  public boolean isApplicable(Set<TargetSystem> enabledSystems) {
    // don't care
    return true;
  }

  // Not Using AutoService to hook up this instrumentation
  public static class TracerClassInstrumentation extends Tracing {
    private final String className;
    private final Set<String> methodNames;

    /** No-arg constructor only used by muzzle and tests. */
    public TracerClassInstrumentation() {
      this(Trace.class.getName(), Collections.singleton("noop"));
    }

    public TracerClassInstrumentation(final String className, final Set<String> methodNames) {
      super("trace", "trace-config");
      this.className = className;
      this.methodNames = methodNames;
    }

    @Override
    public ElementMatcher<ClassLoader> classLoaderMatcher() {
      // Optimization for expensive typeMatcher.
      return hasClassesNamed(className);
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return safeHasSuperType(named(className));
    }

    @Override
    public String[] helperClassNames() {
      return new String[] {
        packageName + ".TraceDecorator",
      };
    }

    @Override
    public Map<ElementMatcher<? super MethodDescription>, String> transformers() {
      ElementMatcher.Junction<MethodDescription> methodMatchers = null;
      for (final String methodName : methodNames) {
        if (methodMatchers == null) {
          if (methodName.equals("*")) {
            methodMatchers =
                not(
                    isHashCode()
                        .or(isEquals())
                        .or(isToString())
                        .or(isFinalizer())
                        .or(isGetter())
                        .or(isConstructor())
                        .or(isSetter())
                        .or(isSynthetic()));
          } else {
            methodMatchers = named(methodName);
          }
        } else {
          methodMatchers = methodMatchers.or(named(methodName));
        }
      }

      return Collections.<ElementMatcher<? super MethodDescription>, String>singletonMap(
          methodMatchers, packageName + ".TraceAdvice");
    }
  }
}
