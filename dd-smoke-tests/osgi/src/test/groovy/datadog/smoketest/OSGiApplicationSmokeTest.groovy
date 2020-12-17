package datadog.smoketest

import spock.lang.Timeout
import spock.util.concurrent.PollingConditions

import java.util.concurrent.TimeUnit

class OSGiApplicationSmokeTest extends AbstractSmokeTest {

  @Override
  ProcessBuilder createProcessBuilder() {
    String osgiShadowJar = System.getProperty("datadog.smoketest.osgi.shadowJar.path")
    assert new File(osgiShadowJar).isFile()

    List<String> command = new ArrayList<>()
    command.add(javaPath())
    command.addAll(defaultJavaProperties)
    command.addAll(additionalArguments())
    command.addAll((String[]) ["-jar", osgiShadowJar])

    ProcessBuilder processBuilder = new ProcessBuilder(command)
    processBuilder.directory(new File(buildDirectory))

    return processBuilder
  }

  List<String> additionalArguments() {
    return Collections.emptyList()
  }

  def "placeholder test"() {
    when:
      true

    then:
      true
  }
}
