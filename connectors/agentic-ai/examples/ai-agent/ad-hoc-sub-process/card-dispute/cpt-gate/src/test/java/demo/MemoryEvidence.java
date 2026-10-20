package demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MemoryEvidence {
  @FunctionalInterface interface Get {
    JsonNode fetch(URI uri) throws Exception;
  }

  private final Get get;
  private JsonNode previous;

  MemoryEvidence() {
    this(uri -> {
      var response = java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build()
          .send(java.net.http.HttpRequest.newBuilder(uri).timeout(java.time.Duration.ofSeconds(15)).GET().build(),
              java.net.http.HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).as("owned memory snapshot/archive GET").isEqualTo(200);
      assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
      return Demo.JSON.readTree(response.body());
    });
  }

  MemoryEvidence(Get get) {
    this.get = get;
  }

  JsonNode capture(Api api, long pi, JsonNode agent, Set<String> documentIds, String feedback) throws Exception {
    return capture(api, pi, agent, documentIds, feedback, false);
  }

  JsonNode capture(Api api, long pi, JsonNode agent, Set<String> documentIds, String feedback,
      boolean historyOnly) throws Exception {
    String conversationId = agent.path("context").path("conversation").path("conversationId").asText();
    JsonNode evidence = null;
    var agents = api.post("/v2/agent-instances/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 100)));
    assertThat(agents.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(100);
    var historyItems = Demo.JSON.createArrayNode();
    for (var instance : agents.path("items")) {
      var history = api.post("/v2/agent-instances/" + instance.path("agentInstanceKey").asText()
          + "/history/search", Map.of("page", Map.of("limit", 1000)));
      assertThat(history.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
      history.path("items").forEach(historyItems::add);
    }
    if (historyOnly) {
      for (var item : historyItems) {
        if (!item.path("role").asText().equals("USER")) continue;
        for (var candidate : item.path("content").findValues("evidence")) {
          if (candidate.path("kind").asText().equals("customMemory")
              && candidate.path("pinnedLedger").path("deadline").asText().equals("2027-01-13")
              && candidate.path("pinnedLedger").path("fraudVerdict").asText().equals("LOW")
              && candidate.path("reconciliationFacts").path("refundTotal").asText().equals("34.99")) {
            URI candidateUrl = URI.create(candidate.path("storeUrl").asText());
            String snapshotId = candidateUrl.getPath().substring(candidateUrl.getPath().lastIndexOf('/') + 1);
            ownedUrl(candidateUrl.toString(), conversationId, snapshotId);
            evidence = candidate;
          }
        }
      }
      assertThat(evidence).as("revision USER history reveals the completed initial-review snapshot").isNotNull();
    }
    var originals = new java.util.HashMap<String, JsonNode>();
    var variables = api.post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 1000)));
    assertThat(variables.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
    var calls = new java.util.HashMap<String, JsonNode>();
    var results = new java.util.HashMap<String, JsonNode>();
    for (var variable : variables.path("items")) {
      String name = variable.path("name").asText();
      String scope = variable.path("scopeKey").asText();
      if (name.equals("toolCall")) calls.put(scope, api.variableValue(variable));
      if (name.equals("toolCallResult")) results.put(scope, api.variableValue(variable));
    }
    for (var entry : calls.entrySet()) {
      String callId = entry.getValue().path("_meta").path("id").asText();
      if (!callId.isBlank() && results.containsKey(entry.getKey())) {
        originals.put(callId, results.get(entry.getKey()));
      }
    }
    addNativeResults(originals, historyItems);
    var snapshot = verify(agent, evidence, documentIds, feedback, originals);
    Path path = Path.of("proofs/artifacts/memory-" + pi + "-" + (historyOnly ? "history-initial"
        : feedback == null ? "context-initial" : "context-revised") + ".json");
    Files.createDirectories(path.getParent());
    Files.writeString(path, Demo.JSON.writeValueAsString(Map.of("processInstanceKey", Long.toString(pi),
        "provenanceSource", historyOnly ? "owned USER history customMemory" : "owned persisted agent context",
        "snapshot", snapshot)));
    return snapshot;
  }

  JsonNode verify(JsonNode agent, JsonNode evidence, Set<String> documentIds, String feedback,
      Map<String, JsonNode> originals) throws Exception {
    var context = agent.path("context").path("conversation");
    String conversationId = context.path("conversationId").asText();
    URI recorded = URI.create((evidence == null ? context : evidence).path("storeUrl").asText());
    String snapshotId = recorded.getPath().substring(recorded.getPath().lastIndexOf('/') + 1);
    if (evidence != null) {
      assertThat(evidence.path("kind").asText()).isEqualTo("customMemory");
      assertThat(evidence.path("strategy").asText()).isEqualTo("observation masking with pinned case ledger");
    } else {
      assertThat(context.path("snapshotId").asText()).isEqualTo(snapshotId);
    }
    URI url = ownedUrl(recorded.toString(), conversationId, snapshotId);
    var snapshot = get.fetch(url);
    assertThat(snapshot.path("conversationId").asText()).isEqualTo(conversationId);
    assertThat(snapshot.path("snapshotId").asText()).isEqualTo(snapshotId);
    assertThat(snapshot.path("storeUrl").asText()).isEqualTo(url.toString());
    assertThat(snapshot.path("strategy").asText()).isEqualTo("observation-masking");
    var ledger = snapshot.path("ledger");
    if (evidence != null) {
      for (String key : List.of("matchedTransactionIds", "overchargeAmount", "fraudVerdict", "deadline")) {
        assertThat(ledger.path(key)).isEqualTo(evidence.path("pinnedLedger").path(key));
      }
    } else {
      for (String key : List.of("matchedTransactionIds", "overchargeAmount", "fraudVerdict", "deadline", "reconciliationFacts")) {
        assertThat(ledger.path(key)).isEqualTo(context.path("ledger").path(key));
      }
    }
    var facts = Demo.JSON.valueToTree(Map.of("refundTotal", "34.99", "postedRefundTransactionIds", "TX-0914",
        "matchCount", "2", "daysBetweenCharges", "1"));
    assertThat(ledger.path("reconciliationFacts")).isEqualTo(facts);
    if (evidence != null) assertThat(evidence.path("reconciliationFacts")).isEqualTo(facts);
    assertThat(ledger.path("matchedTransactionIds").toString()).contains("TX-0911", "TX-0912");
    assertThat(ledger.path("overchargeAmount").asText()).isEqualTo("482.17");
    assertThat(ledger.path("fraudVerdict").asText()).isEqualTo("LOW");
    assertThat(ledger.path("deadline").asText()).isEqualTo("2027-01-13");
    var results = new ArrayList<JsonNode>();
    var users = new ArrayList<JsonNode>();
    var observedDocuments = new HashSet<String>();
    for (var message : snapshot.path("maskedContext")) {
      if (message.path("role").asText().equals("tool")) {
        for (var result : message.path("results")) results.add(result);
      }
      if (message.path("role").asText().equals("user")) {
        users.add(message);
        assertThat(message.toString()).doesNotContain("Pinned case ledger from completed tool results:");
        for (var content : message.path("content")) {
          if (content.hasNonNull("documentId")) observedDocuments.add(content.path("documentId").asText());
        }
      }
    }
    assertThat(observedDocuments).containsExactlyInAnyOrderElementsOf(documentIds);
    assertThat(users).hasSize(feedback == null ? 1 : 2);
    assertThat(results.size()).as("memory needs older and latest-two observations").isGreaterThan(2);
    if (evidence != null) assertThat(evidence.path("maskedToolResults").asInt()).isEqualTo(results.size() - 2);
    for (int index = 0; index < results.size(); index++) {
      var result = results.get(index);
      String callId = result.path("callId").asText();
      String tool = result.path("toolName").asText();
      assertThat(callId).isNotBlank();
      assertThat(tool).isNotBlank();
      String text = result.path("content").path(0).path("text").asText();
      if (index >= results.size() - 2) {
        assertThat(text).doesNotStartWith("Older tool result masked.");
        assertActualContent(callId, result.path("content"), originals);
      } else {
        String prefix = "Older tool result masked. Tool: " + tool + ". Call ID: " + callId + ". Full result: ";
        assertThat(text).startsWith(prefix);
        URI pointer = URI.create(text.substring(prefix.length()));
        String ownedPrefix = "/agentic-ai/memory/" + conversationId + "/";
        assertThat(pointer.getScheme()).isEqualTo(url.getScheme());
        assertThat(pointer.getRawAuthority()).isEqualTo(url.getRawAuthority());
        assertThat(pointer.getPath()).startsWith(ownedPrefix).endsWith("/results");
        assertThat(pointer.getPath().substring(ownedPrefix.length())).matches("[a-zA-Z0-9-]+/results");
        assertThat(pointer.getRawQuery()).isEqualTo("callId=" + URLEncoder.encode(callId, StandardCharsets.UTF_8));
        assertThat(pointer.getFragment()).isNull();
        var archived = get.fetch(pointer);
        assertThat(archived.path("callId").asText()).isEqualTo(callId);
        assertThat(archived.path("toolName").asText()).isEqualTo(tool);
        assertThat(archived.path("content").isArray()).isTrue();
        assertThat(archived.path("content").isEmpty()).isFalse();
        assertThat(archived.path("content").toString()).doesNotContain("Older tool result masked.");
        assertActualContent(callId, archived.path("content"), originals);
      }
    }
    if (previous != null) {
      assertThat(snapshot.path("conversationId")).isEqualTo(previous.path("conversationId"));
      assertThat(snapshot.path("snapshotId")).isNotEqualTo(previous.path("snapshotId"));
      assertThat(snapshot.path("ledger")).isEqualTo(previous.path("ledger"));
      var previousUsers = new ArrayList<JsonNode>();
      for (var message : previous.path("maskedContext")) {
        if (message.path("role").asText().equals("user")) previousUsers.add(message);
      }
      assertThat(users.subList(0, previousUsers.size())).isEqualTo(previousUsers);
    }
    if (feedback != null) {
      assertThat(feedback).as("explicit analyst feedback").isNotBlank();
      assertThat(users.get(users.size() - 1).toString()).contains(feedback);
    }
    previous = snapshot.deepCopy();
    return snapshot;
  }

  private void assertActualContent(String callId, JsonNode content, Map<String, JsonNode> originals) {
    assertThat(originals).as("persisted actual tool result for call").containsKey(callId);
    var expected = Demo.JSON.createArrayNode();
    var original = originals.get(callId);
    if (original.isTextual()) expected.addObject().put("text", original.asText());
    else expected.add(original);
    assertThat(content).as("memory content equals actual tool result").isEqualTo(expected);
  }

  static void addNativeResults(Map<String, JsonNode> originals, JsonNode history) {
    for (var item : history) {
      if (!item.path("role").asText().equals("TOOL_RESULT")
          || !item.path("commitStatus").asText().equals("COMMITTED")) continue;
      for (var call : item.path("toolCalls")) {
        if (!call.path("toolName").asText().equals("c8__get_document")) continue;
        assertThat(item.path("toolCalls").size()).as("native result has one correlated call").isEqualTo(1);
        assertThat(call.path("elementId").isNull()).as("native tool has no BPMN element").isTrue();
        String callId = call.path("toolCallId").asText();
        assertThat(callId).isNotBlank().isEqualTo(item.path("historyItemId").asText());
        var content = item.path("content");
        assertThat(content.size()).as("native document result text block").isEqualTo(1);
        assertThat(content.get(0).path("contentType").asText()).isEqualTo("TEXT");
        assertThat(content.get(0).path("text").isTextual()).isTrue();
        var result = content.get(0).path("text");
        var existing = originals.putIfAbsent(callId, result);
        if (existing != null) assertThat(existing).as("consistent result for native call").isEqualTo(result);
      }
    }
  }

  static URI ownedUrl(String value, String conversationId, String snapshotId) {
    assertThat(conversationId).matches("[a-zA-Z0-9-]+");
    assertThat(snapshotId).matches("[a-zA-Z0-9-]+");
    URI url = URI.create(value);
    assertThat(url.getScheme()).isEqualTo("http");
    assertThat(url.getHost()).isIn("localhost", "127.0.0.1");
    URI trusted = URI.create(System.getProperty("gate.memoryOrigin", "http://localhost:18087"));
    assertThat(url.getPort()).isEqualTo(trusted.getPort());
    assertThat(url.getHost()).isEqualTo(trusted.getHost());
    assertThat(url.getUserInfo()).isNull();
    assertThat(url.getQuery()).isNull();
    assertThat(url.getFragment()).isNull();
    assertThat(url.getPath()).isEqualTo("/agentic-ai/memory/" + conversationId + "/" + snapshotId);
    return url;
  }
}
