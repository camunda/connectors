package demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MemoryEvidenceTest {
  private final String url = "http://localhost:18087/agentic-ai/memory/owned-conversation/snapshot-one";

  private JsonNode ledger() {
    return Demo.JSON.valueToTree(Map.of("matchedTransactionIds", java.util.List.of("TX-0911", "TX-0912"),
        "overchargeAmount", "482.17", "fraudVerdict", "LOW", "deadline", "2027-01-13",
        "reconciliationFacts", Map.of("refundTotal", "34.99", "postedRefundTransactionIds", "TX-0914",
            "matchCount", "2", "daysBetweenCharges", "1")));
  }

  private JsonNode agent(String snapshot) {
    return Demo.JSON.valueToTree(Map.of("context", Map.of("conversation",
        Map.of("conversationId", "owned-conversation", "snapshotId", snapshot,
            "storeUrl", url.replace("snapshot-one", snapshot), "ledger", ledger()))));
  }

  private JsonNode evidence(String snapshot) {
    return Demo.JSON.valueToTree(Map.of("kind", "customMemory",
        "strategy", "observation masking with pinned case ledger",
        "storeUrl", url.replace("snapshot-one", snapshot), "maskedToolResults", 1, "pinnedLedger", ledger(),
        "reconciliationFacts", ledger().path("reconciliationFacts")));
  }

  private com.fasterxml.jackson.databind.node.ObjectNode snapshot() {
    var snapshot = Demo.JSON.createObjectNode();
    snapshot.put("conversationId", "owned-conversation").put("snapshotId", "snapshot-one")
        .put("storeUrl", url).put("strategy", "observation-masking").set("ledger", ledger());
    var messages = snapshot.putArray("maskedContext");
    var docs = messages.addObject().put("role", "user").putArray("content");
    for (String id : Set.of("pdf", "csv", "skill", "reference", "script")) {
      docs.addObject().put("documentId", id).put("fileName", id);
    }
    var results = messages.addObject().put("role", "tool").putArray("results");
    results.addObject().put("callId", "old").put("toolName", "tool")
        .putArray("content").addObject().put("text",
            "Older tool result masked. Tool: tool. Call ID: old. Full result: " + url + "/results?callId=old");
    for (String id : java.util.List.of("new-one", "new-two")) {
      results.addObject().put("callId", id).put("toolName", "tool").putArray("content")
          .addObject().put("text", "actual " + id);
    }
    return snapshot;
  }

  private Map<String, JsonNode> originals() {
    return Map.of("old", Demo.JSON.getNodeFactory().textNode("original archived content"),
        "new-one", Demo.JSON.getNodeFactory().textNode("actual new-one"),
        "new-two", Demo.JSON.getNodeFactory().textNode("actual new-two"));
  }

  @Test void nativeDocumentResultsComeFromCommittedHistoryRatherThanBpmnVariables() throws Exception {
    var history = Demo.JSON.readTree("""
        [{"role":"TOOL_RESULT","commitStatus":"COMMITTED","historyItemId":"native-call",
          "toolCalls":[{"toolCallId":"native-call","toolName":"c8__get_document","elementId":null}],
          "content":[{"contentType":"TEXT","text":"Document is already in the conversation."}]}]
        """);
    var results = new java.util.HashMap<String, JsonNode>();
    MemoryEvidence.addNativeResults(results, history);
    assertThat(results.get("native-call").asText()).isEqualTo("Document is already in the conversation.");
    var conflict = new java.util.HashMap<>(results);
    conflict.put("native-call", Demo.JSON.getNodeFactory().textNode("different result"));
    assertThatThrownBy(() -> MemoryEvidence.addNativeResults(conflict, history))
        .isInstanceOf(AssertionError.class);
    var uncommitted = history.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) uncommitted.get(0)).put("commitStatus", "PENDING");
    var absent = new java.util.HashMap<String, JsonNode>();
    MemoryEvidence.addNativeResults(absent, uncommitted);
    assertThat(absent).isEmpty();
  }

  @Test void ownedSnapshotRoundtripDocumentsAndReviseAreVerified() throws Exception {
    var first = snapshot();
    var revised = first.deepCopy();
    revised.put("snapshotId", "snapshot-two").put("storeUrl", url.replace("snapshot-one", "snapshot-two"));
    ((com.fasterxml.jackson.databind.node.ArrayNode) revised.path("maskedContext")).addObject()
        .put("role", "user").putArray("content").addObject().put("text", "Analyst: clarify evidence");
    var reader = new MemoryEvidence(uri -> {
      if (uri.getPath().endsWith("/results")) {
        assertThat(uri.toString()).isEqualTo(url + "/results?callId=old");
        return Demo.JSON.valueToTree(Map.of("callId", "old", "toolName", "tool",
            "content", java.util.List.of(Map.of("text", "original archived content"))));
      }
      return uri.getPath().endsWith("snapshot-two") ? revised : first;
    });
    var ids = Set.of("pdf", "csv", "skill", "reference", "script");
    reader.verify(agent("snapshot-one"), evidence("snapshot-one"), ids, null, originals());
    reader.verify(agent("snapshot-two"), evidence("snapshot-two"), ids, "clarify evidence", originals());
  }

  @Test void arbitraryOriginsAndUnownedSnapshotPathsAreRejected() {
    for (String value : java.util.List.of(url.replace("localhost", "example.org"),
        url.replace("18087", "8280"), url + "?query=1", url.replace("owned-conversation", "foreign"))) {
      assertThatThrownBy(() -> MemoryEvidence.ownedUrl(value, "owned-conversation", "snapshot-one"))
          .isInstanceOf(AssertionError.class);
    }
  }

  @Test void persistedOwnedContextSnapshotHasDistinctProvenanceFromLaggingHistory() throws Exception {
    var reader = new MemoryEvidence(uri -> uri.getPath().endsWith("/results")
        ? Demo.JSON.valueToTree(Map.of("callId", "old", "toolName", "tool",
            "content", java.util.List.of(Map.of("text", "original archived content")))) : snapshot());
    reader.verify(agent("snapshot-one"), null, Set.of("pdf", "csv", "skill", "reference", "script"), null, originals());
    var foreign = (com.fasterxml.jackson.databind.node.ObjectNode) agent("snapshot-one");
    foreign.withObject("/context/conversation").put("storeUrl", url.replace("owned-conversation", "foreign"));
    assertThatThrownBy(() -> reader.verify(foreign, null, Set.of("pdf"), null, originals()))
        .isInstanceOf(AssertionError.class);
  }

  @Test void missingLedgerDocumentsOrArchiveContentFail() {
    for (String change : java.util.List.of("ledger", "documents", "archive", "reconciliation")) {
      var snapshot = snapshot();
      if (change.equals("ledger")) snapshot.withObject("/ledger").put("deadline", "wrong");
      if (change.equals("reconciliation")) snapshot.withObject("/ledger").remove("reconciliationFacts");
      if (change.equals("documents")) ((com.fasterxml.jackson.databind.node.ArrayNode)
          snapshot.path("maskedContext").get(0).path("content")).remove(0);
      var reader = new MemoryEvidence(uri -> uri.getPath().endsWith("/results")
          ? Demo.JSON.valueToTree(Map.of("callId", "old", "toolName", "tool",
              "content", java.util.List.of(Map.of("text", "forged")))) : snapshot);
      assertThatThrownBy(() -> reader.verify(agent("snapshot-one"), evidence("snapshot-one"),
          Set.of("pdf", "csv", "skill", "reference", "script"), null, originals()))
          .isInstanceOf(AssertionError.class);
    }
  }
}
