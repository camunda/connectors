package demo;

import io.camunda.client.CamundaClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WebSearchProbe {
  static final String PROCESS_ID = "CptGateWebSearchProbe";
  static final Path REPORT = Path.of("target/web-search-probe.json");

  static String classify(String error) {
    return error.toLowerCase(Locale.ROOT).contains("not available on region-specific endpoints")
        ? "REGIONAL_UNAVAILABLE" : "OTHER_ERROR";
  }

  static void run(CamundaClient client, Api api) throws Exception {
    Files.deleteIfExists(REPORT);
    var deployment = client.newDeployResourceCommand()
        .addResourceFile("src/test/resources/web-search-probe.bpmn").send().join();
    var definitions = deployment.getProcesses().stream()
        .filter(process -> PROCESS_ID.equals(process.getBpmnProcessId())).toList();
    if (definitions.size() != 1) throw new AssertionError("Probe deployment must contain only its own process");
    long pi = client.newCreateInstanceCommand().processDefinitionKey(definitions.get(0).getProcessDefinitionKey())
        .send().join().getProcessInstanceKey();
    Throwable primary = null;
    try {
      long deadline = System.nanoTime() + Duration.ofSeconds(120).toNanos();
      String outcome = "TIMEOUT";
      while (System.nanoTime() < deadline) {
        var incidents = api.post("/v2/incidents/search", Map.of("filter",
            Map.of("processInstanceKey", Long.toString(pi), "state", "ACTIVE"),
            "page", Map.of("limit", 100)));
        if (incidents.path("page").path("totalItems").asInt() > 100) {
          throw new AssertionError("Probe incidents exceed scan bound");
        }
        if (!incidents.path("items").isEmpty()) {
          outcome = "REGIONAL_UNAVAILABLE";
          for (var incident : incidents.path("items")) {
            if (!"REGIONAL_UNAVAILABLE".equals(classify(incident.path("errorMessage").asText()))) {
              outcome = "OTHER_ERROR";
            }
          }
          break;
        }
        if (api.completed(pi, "ProbeAgent") > 0) {
          outcome = "AVAILABLE";
          break;
        }
        Thread.sleep(1000);
      }
      // Store only the classification: connector errors may contain resolved credentials.
      Files.writeString(REPORT, Demo.JSON.writeValueAsString(Map.of(
          "outcome", outcome, "processId", PROCESS_ID, "processInstanceKey", Long.toString(pi))));
      if ("AVAILABLE".equals(outcome)) throw new AssertionError("web search now available; enable it in the model");
      if (!"REGIONAL_UNAVAILABLE".equals(outcome)) {
        throw new AssertionError("Web-search probe failed: " + outcome + " (error details withheld)");
      }
      System.out.println("#83 web search: N/A: not available on EU account (regional rejection verified)");
    } catch (Exception | AssertionError e) {
      primary = e;
      throw e;
    } finally {
      try {
        if ("ACTIVE".equals(api.get("/v2/process-instances/" + pi).path("state").asText())) {
          client.newCancelInstanceCommand(pi).send().join();
        }
        api.assertNoRawKeys(pi);
      } catch (Exception | AssertionError cleanup) {
        if (primary == null) throw cleanup;
        primary.addSuppressed(cleanup);
      }
    }
  }
}
