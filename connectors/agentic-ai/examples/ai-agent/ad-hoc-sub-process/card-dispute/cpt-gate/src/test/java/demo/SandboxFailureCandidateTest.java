package demo;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.JobSelectors.byElementId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = GateApplication.class)
@CamundaSpringProcessTest
class SandboxFailureCandidateTest {
  @Autowired CamundaClient client;
  @Autowired CamundaProcessTestContext context;

  @Test void createCleanFailure() throws Exception { failure("CREATE", false); }
  @Test void createSeededFailure() throws Exception { failure("CREATE", true); }
  @Test void bashCleanFailure() throws Exception { failure("BASH", false); }
  @Test void bashSeededFailure() throws Exception { failure("BASH", true); }
  @Test void cleanSuccessPropagatesCreateThenBash() throws Exception { success(false); }
  @Test void seededSuccessPropagatesCreateThenBash() throws Exception { success(true); }

  private io.camunda.client.api.response.ProcessInstanceEvent start() throws Exception {
    byte[] model = Files.readAllBytes(Path.of("src/test/resources/round3/card-dispute.candidate.bpmn"));
    assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(model)))
        .isEqualTo("eed29b6a179beb9cceef0d2b5d9e370232b43e76a4ad40607504702c677a98b5");
    var bpmn = io.camunda.zeebe.model.bpmn.Bpmn.readModelFromStream(new java.io.ByteArrayInputStream(model));
    var boundary = bpmn.getModelElementById("Sandbox_Error");
    assertThat(((io.camunda.zeebe.model.bpmn.instance.BoundaryEvent) boundary).getOutgoing()).isEmpty();
    assertThat(bpmn.<io.camunda.zeebe.model.bpmn.instance.FlowNode>getModelElementById("Sandbox_FallbackRead")).isNull();
    assertThat(bpmn.<io.camunda.zeebe.model.bpmn.instance.FlowNode>getModelElementById("Sandbox_Fallback")).isNull();
    assertThat(bpmn.<io.camunda.zeebe.model.bpmn.instance.FlowNode>getModelElementById("FetchCardNetworkRules")).isNull();

    var api = new Api(context.getCamundaRestAddress().toString(), false);
    api.post("/v2/cluster-variables/global", Map.of("name", "cardDisputeLlm", "kind", "JSON",
        "value", Map.of("endpoint", "camunda.secrets.FAKE_ENDPOINT",
            "apiKey", "camunda.secrets.FAKE_API_KEY", "model", "camunda.secrets.FAKE_MODEL")));
    client.newDeployResourceCommand().addResourceBytes(model, "round3-candidate.bpmn")
        .addResourceStringUtf8(Demo.text("review-dispute.form"), "review-dispute.form")
        .addResourceStringUtf8(Demo.text("policies/dispute-compliance-policy.md"), "dispute-compliance-policy.md")
        .send().join();
    var variables = Demo.variables(List.of(), "http://unused.invalid", "unused");
    variables.put("disputeFilingDate", "2026-10-20");
    variables.put("deadlineScriptSha256", "816444bd5c28a64e7ff99c7de4bb6c706e42b3bfe56acc1c98e9089c07fa8416");
    return client.newCreateInstanceCommand().bpmnProcessId("CardDispute").latestVersion()
        .variables(variables).send().join();
  }

  private void failure(String operation, boolean seeded) throws Exception {
    var pi = start();
    var api = new Api(context.getCamundaRestAddress().toString(), false);
    try {
      String callId = UUID.randomUUID().toString();
      var activation = new HashMap<String, Object>();
      activation.put("toolCall", Map.of("operation", operation, "handle", "previous-handle",
          "command", "python3 previously-imported.py",
          "_meta", Map.of("id", callId, "name", "AnalyzeInSandbox")));
      if (seeded) activation.put("toolCallResult", Map.of("handle", "SEEDED_SUCCESS",
          "exit_code", 0, "stdout", "SEEDED_SUCCESS", "filingDeadline", "2027-01-13"));
      context.completeJobOfAdHocSubProcess(byElementId("DisputeAgent"), activation,
          result -> result.activateElement("AnalyzeInSandbox"));
      assertThatProcessInstance(pi).hasActiveElements("AnalyzeInSandbox");
      org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
        var elements = api.post("/v2/element-instances/search", Map.of("filter",
            Map.of("processInstanceKey", Long.toString(pi.getProcessInstanceKey()),
                "elementId", "AnalyzeInSandbox", "state", "ACTIVE"), "page", Map.of("limit", 100)));
        assertThat(elements.path("items").size()).isEqualTo(1);
        String scope = elements.path("items").get(0).path("elementInstanceKey").asText();
        var local = api.post("/v2/variables/search", Map.of("filter",
            Map.of("scopeKey", scope, "name", "toolCallResult"), "page", Map.of("limit", 100)));
        assertThat(local.path("items").size()).isEqualTo(1);
        assertThat(api.variableValue(local.path("items").get(0)).isNull()).isTrue();
      });
      var error = new HashMap<String, Object>();
      error.put("sandboxError", "forced isolated " + operation + " outage");
      error.put("toolCallResult", null);
      context.throwBpmnErrorFromJob(byElementId("AnalyzeInSandbox"), "SANDBOX_UNAVAILABLE", error);
      assertThatProcessInstance(pi).hasCompletedElements("Sandbox_Error")
          .hasTerminatedElements("AnalyzeInSandbox")
          .hasCompletedElement("AnalyzeInSandbox", 0)
          .hasNotActivatedElements("ReviewDraftedDecision", "DisputeDecided");
      org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
        var collected = api.post("/v2/variables/search", Map.of("filter",
            Map.of("processInstanceKey", Long.toString(pi.getProcessInstanceKey()), "name", "toolCallResults"),
            "page", Map.of("limit", 100)));
        assertThat(collected.path("items").isEmpty()).isFalse();
      });
      var history = api.post("/v2/variables/search", Map.of("filter",
          Map.of("processInstanceKey", Long.toString(pi.getProcessInstanceKey()), "name", "toolCallResults"),
          "page", Map.of("limit", 100)));
      var captured = Demo.JSON.createArrayNode();
      for (var variable : history.path("items")) captured.add(api.variableValue(variable));
      Files.writeString(Path.of("proofs/round3-v22-" + operation + "-" + (seeded ? "seeded" : "clean") + "-collection.json"),
          Demo.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(captured));
      for (var variable : history.path("items")) {
        assertThat(api.variableValue(variable).toString()).doesNotContain(
            "SEEDED_SUCCESS", "previous-handle", "stdout", "exit_code", "filingDeadline", "sandboxFallback");
      }
      assertThat(history.path("items").isEmpty()).isFalse();
      for (var collection : captured) {
        assertThat(collection.size()).isEqualTo(1);
        assertThat(collection.get(0).path("content")).isEqualTo(Demo.JSON.valueToTree(Map.of(
            "error", "SANDBOX_FAILED",
            "message", "The sandbox call failed. The filing deadline is not computed.")));
      }
      assertThatProcessInstance(pi).hasNotActivatedElements("ReviewDraftedDecision", "DisputeDecided");
    } finally {
      client.newCancelInstanceCommand(pi.getProcessInstanceKey()).send().join();
    }
  }

    private void success(boolean seeded) throws Exception {
      var pi = start();
      var api = new Api(context.getCamundaRestAddress().toString(), false);
      var captured = Demo.JSON.createArrayNode();
      try {
        for (String operation : List.of("CREATE", "BASH")) {
          String callId = UUID.randomUUID().toString();
          var activation = new HashMap<String, Object>();
          activation.put("toolCall", Map.of("operation", operation,
              "handle", "NEW_CONNECTOR_HANDLE", "command", "python3 imported.py",
              "_meta", Map.of("id", callId, "name", "AnalyzeInSandbox")));
          if (seeded) activation.put("toolCallResult", Map.of("handle", "SEEDED_SUCCESS", "stdout", "STALE_STDOUT"));
          context.completeJobOfAdHocSubProcess(byElementId("DisputeAgent"), activation,
              result -> result.activateElement("AnalyzeInSandbox"));
          assertThatProcessInstance(pi).hasActiveElements("AnalyzeInSandbox");
          org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            var elements = api.post("/v2/element-instances/search", Map.of("filter",
                Map.of("processInstanceKey", Long.toString(pi.getProcessInstanceKey()),
                    "elementId", "AnalyzeInSandbox", "state", "ACTIVE"), "page", Map.of("limit", 100)));
            assertThat(elements.path("items").size()).isEqualTo(1);
            String scope = elements.path("items").get(0).path("elementInstanceKey").asText();
            var local = api.post("/v2/variables/search", Map.of("filter",
                Map.of("scopeKey", scope, "name", "toolCallResult"), "page", Map.of("limit", 100)));
            assertThat(local.path("items").size()).isEqualTo(1);
            assertThat(api.variableValue(local.path("items").get(0)).isNull()).isTrue();
          });
          Object output = operation.equals("CREATE")
              ? Map.of("handle", "NEW_CONNECTOR_HANDLE", "workDir", "/returned/workdir")
              : Map.of("exit_code", 0, "stdout", "NEW_REMOTE_STDOUT");
          context.completeJob(byElementId("AnalyzeInSandbox"), Map.of("toolCallResult", output));
          org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).untilAsserted(() -> {
            var variables = api.post("/v2/variables/search", Map.of("filter",
                Map.of("processInstanceKey", Long.toString(pi.getProcessInstanceKey()), "name", "toolCallResults"),
                "page", Map.of("limit", 100)));
            boolean found = false;
            for (var variable : variables.path("items")) {
              for (var result : api.variableValue(variable)) {
                if (!result.path("id").asText().equals(callId)) continue;
                found = true;
                assertThat(result.path("content")).isEqualTo(Demo.JSON.valueToTree(output));
              }
            }
            assertThat(found).as("new connector result collected for " + operation + " call " + callId).isTrue();
          });
          var variables = api.post("/v2/variables/search", Map.of("filter",
              Map.of("processInstanceKey", Long.toString(pi.getProcessInstanceKey()), "name", "toolCallResults"),
              "page", Map.of("limit", 100)));
          for (var variable : variables.path("items")) captured.add(api.variableValue(variable));
        }
        assertThatProcessInstance(pi).hasCompletedElement("AnalyzeInSandbox", 2)
            .hasNotActivatedElements("Sandbox_Error", "ReviewDraftedDecision", "DisputeDecided");
      } finally {
        Files.writeString(Path.of("proofs/round3-v22-success-" + (seeded ? "seeded" : "clean") + "-collection.json"),
            Demo.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(captured));
        client.newCancelInstanceCommand(pi.getProcessInstanceKey()).send().join();
      }
  }
}
