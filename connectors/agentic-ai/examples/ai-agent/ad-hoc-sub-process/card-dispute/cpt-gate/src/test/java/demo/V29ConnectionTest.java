package demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class V29ConnectionTest {
  @Test void recordedRunConnectsFormattedPromptToAllToolsAndReviewWithoutMutation() throws Exception {
    String key = System.getProperty("gate.processInstanceKey");
    assertThat(key).as("Explicit existing v29 process instance key is required").matches("[0-9]+");
    var api = new Api("http://localhost:8280", true);
    var before = api.get("/v2/process-instances/" + key);
    assertThat(before.path("processDefinitionId").asText()).isEqualTo("CardDispute");
    assertThat(before.path("processDefinitionVersion").asInt()).isEqualTo(29);
    assertThat(before.path("hasIncident").asBoolean()).isFalse();
    assertThat(api.binary("/v2/process-definitions/" + before.path("processDefinitionKey").asText() + "/xml"))
        .isEqualTo(Demo.text("card-dispute.bpmn").getBytes(StandardCharsets.UTF_8));

    var tasks = api.post("/v2/user-tasks/search", Map.of("filter",
        Map.of("processInstanceKey", key, "state", "CREATED"), "page", Map.of("limit", 100)));
    assertThat(tasks.path("items").size()).isEqualTo(1);
    assertThat(tasks.path("items").get(0).path("elementId").asText()).isEqualTo("ReviewDraftedDecision");
    var agents = api.post("/v2/agent-instances/search", Map.of("filter",
        Map.of("processInstanceKey", key), "page", Map.of("limit", 100)));
    assertThat(agents.path("items").size()).isEqualTo(1);
    String agentKey = agents.path("items").get(0).path("agentInstanceKey").asText();
    var history = api.post("/v2/agent-instances/" + agentKey + "/history/search",
        Map.of("page", Map.of("limit", 1000)));
    assertThat(history.path("page").path("hasMoreTotalItems").asBoolean()).isFalse();
    var calls = new HashMap<String, String>();
    var results = new HashMap<String, JsonNode>();
    boolean formattedPrompt = false;
    boolean linkedPrompt = false;
    for (var item : history.path("items")) {
      if ("ASSISTANT".equals(item.path("role").asText())) {
        for (var call : item.path("toolCalls")) {
          calls.put(call.path("toolCallId").asText(), call.path("toolName").asText());
        }
      }
      if ("TOOL_RESULT".equals(item.path("role").asText())) {
        assertThat(item.path("toolCalls").size()).isEqualTo(1);
        var call = item.path("toolCalls").get(0);
        results.put(call.path("toolCallId").asText(), item);
      }
      for (var block : item.path("content")) {
        if ("USER".equals(item.path("role").asText())) {
          String text = block.path("text").asText();
          if (text.startsWith("# New card dispute ")) {
            assertThat(text).contains("\n\n- Cardholder:", "\n\n## Transaction CSV\n\n- ");
            formattedPrompt = true;
          }
        }
        if ("CONFIGURATION".equals(item.path("role").asText())) {
          var prompt = block.path("object").path("evidence").path("systemPrompt");
          if ("linked".equals(prompt.path("type").asText())) {
            assertThat(prompt.path("promptId").asText()).isEqualTo("dispute-compliance-policy.md");
            assertThat(prompt.path("binding").asText()).isEqualTo("latest");
            assertThat(prompt.path("version").asInt()).isPositive();
            assertThat(prompt.path("resourceKey").asText()).isNotBlank();
            linkedPrompt = true;
          }
        }
      }
    }
    assertThat(formattedPrompt).isTrue();
    assertThat(linkedPrompt).isTrue();
    assertThat(results.keySet()).containsExactlyInAnyOrderElementsOf(calls.keySet());
    var connected = new HashSet<String>();
    var exportedDocuments = new HashSet<String>();
    String reconciledDocument = null;
    boolean pushedFraudResult = false;
    boolean successfulScript = false;
    boolean realSandbox = false;
    for (var entry : calls.entrySet()) {
      var result = results.get(entry.getKey());
      assertThat(result.path("toolCalls").get(0).path("toolName").asText()).isEqualTo(entry.getValue());
      connected.add(entry.getValue());
      for (var block : result.path("content")) {
        var object = block.path("object");
        assertThat(object.has("error")).as("No recorded tool error").isFalse();
        if ("sandbox_export_document".equals(entry.getValue()) && block.has("documentReference")) {
          String documentId = block.path("documentReference").path("documentId").asText();
          assertThat(documentId).isNotBlank();
          exportedDocuments.add(documentId);
        }
        var evidence = object.path("evidence");
        if ("sandbox".equals(evidence.path("kind").asText())) {
          assertThat(evidence.path("provider").asText()).isEqualTo("daytona");
          assertThat(evidence.path("fallback").asBoolean(true)).isFalse();
          assertThat(evidence.path("sandboxId").asText()).isNotBlank();
          realSandbox = true;
        }
        if ("ReconcileTransactions".equals(entry.getValue()) && object.has("sourceDocumentId")) {
          assertThat(object.path("sourceDocumentId").asText()).isNotBlank();
          assertThat(object.path("matchCount").asInt()).isEqualTo(2);
          reconciledDocument = object.path("sourceDocumentId").asText();
        }
        if ("AskFraudRiskAgent".equals(entry.getValue()) && object.has("state")) {
          assertThat(object.path("state").asText()).isEqualTo("TASK_STATE_COMPLETED");
          assertThat(object.path("evidence").path("delivery").asText()).isEqualTo("push");
          assertThat(object.path("remoteProcessInstanceKey").asText()).isNotBlank();
          pushedFraudResult = true;
        }
        if ("sandbox_bash".equals(entry.getValue()) && block.has("text")) {
          assertThat(block.path("text").asText()).contains("exit_code: 0", "\"filingDeadline\": \"2027-01-13\"");
          successfulScript = true;
        }
      }
    }
    assertThat(connected).contains("sandbox_fs_read", "sandbox_fs_write", "sandbox_export_document",
        "ReconcileTransactions", "AskFraudRiskAgent", "sandbox_bash");
    assertThat(reconciledDocument).isNotNull();
    assertThat(exportedDocuments).contains(reconciledDocument);
    assertThat(pushedFraudResult).isTrue();
    assertThat(successfulScript).isTrue();
    assertThat(realSandbox).isTrue();
    var draft = api.taskVariable(tasks.path("items").get(0).path("userTaskKey").asText(), "draft");
    assertThat(draft.path("decision").asText()).isEqualTo("APPROVE");
    assertThat(draft.path("refundAmount").decimalValue()).isEqualByComparingTo("482.17");
    assertThat(api.get("/v2/process-instances/" + key)).isEqualTo(before);
    assertThat(api.post("/v2/user-tasks/search", Map.of("filter",
        Map.of("processInstanceKey", key, "state", "CREATED"), "page", Map.of("limit", 100))))
        .isEqualTo(tasks);
  }
}
