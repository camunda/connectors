package demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.client.CamundaClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

final class RealDaytonaEvidence {
  private final Api api;
  private final SandboxCleanup cleanup;
  private final long pi;
  private final Set<String> creates = new HashSet<>();
  private final Set<String> handles = new HashSet<>();
  private final Map<String, JsonNode> allocations = new HashMap<>();
  private final Map<String, JsonNode> calls = new HashMap<>();
  private final Map<String, JsonNode> results = new HashMap<>();
  private final Set<String> csvDocuments = new HashSet<>();
  JsonNode skillEvidence;

  RealDaytonaEvidence(CamundaClient client, Api api, long pi) {
    this(api, new SandboxCleanup(client, api), pi);
  }

  RealDaytonaEvidence(Api api, SandboxCleanup cleanup, long pi) {
    this.api = api;
    this.cleanup = cleanup;
    this.pi = pi;
  }

  void observe() throws Exception {
    var response = api.post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 1000)));
    assertThat(response.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
    for (var variable : response.path("items")) {
      String name = variable.path("name").asText();
      String scope = variable.path("scopeKey").asText();
      if (name.equals("toolCall")) calls.put(scope, api.variableValue(variable));
      if (name.equals("toolCallResult")) results.put(scope, api.variableValue(variable));
      if (name.equals("sandboxFallback")) assertThat(api.variableValue(variable).asBoolean()).isFalse();
      if (name.equals("daytonaApiUrl")) throw new AssertionError("Real proof forbids a Daytona URL override");
      if (name.equals("disputeDocuments")) {
        for (var document : api.variableValue(variable)) {
          if (document.path("metadata").path("fileName").asText().equals("transactions-2026-09.csv")) {
            csvDocuments.add(document.path("documentId").asText());
          }
        }
      }
    }
    for (var entry : calls.entrySet()) {
      var call = entry.getValue();
      String operation = call.path("operation").asText();
      if (!"CREATE".equals(operation)) continue;
      creates.add(entry.getKey());
      var result = results.get(entry.getKey());
      if (result != null && !result.path("handle").asText().isBlank()) {
        String id = result.path("handle").asText();
        assertThat(id).matches("[a-fA-F0-9-]{36}").isNotEqualTo("00000000-0000-4000-8000-000000000001");
        handles.add(id);
        Files.writeString(Path.of("target/real-daytona-handles.json"), Demo.JSON.writeValueAsString(handles));
      }
    }
    // Check the budget before any extra provider lookup; finally cleans every label-owned sandbox.
    assertThat(creates.size()).as("one CREATE job, including discovery/revision").isLessThanOrEqualTo(1);
    assertThat(handles.size()).as("one real sandbox").isLessThanOrEqualTo(1);
    for (var id : handles) {
      if (!allocations.containsKey(id)) allocations.put(id, cleanup.allocation(pi, id));
    }
  }

  Map<String, Object> execution(String definitionKey) throws Exception {
    observe();
    assertThat(creates).hasSize(1);
    assertThat(handles).hasSize(1);
    String id = handles.iterator().next();
    var allocation = allocations.get(id);
    assertThat(allocation.path("cpu").asInt()).isEqualTo(1);
    assertThat(allocation.path("memory").asInt()).isEqualTo(1);
    assertThat(allocation.path("disk").asInt()).isEqualTo(3);
    assertThat(csvDocuments).as("one original CSV input").hasSize(1);
    String documentId = csvDocuments.iterator().next();
    String stdout = "";
    JsonNode reconciliation = null;
    for (var entry : calls.entrySet()) {
      var call = entry.getValue();
      var result = results.get(entry.getKey());
      if (result == null) continue;
      String operation = call.path("operation").asText();
      if ("BASH".equals(operation) && call.path("command").asText().contains("python")) {
        String text = result.asText();
        if (text.startsWith("exit_code: 0\nstdout:\n") && text.contains("2027-01-13")) {
          stdout = text.substring("exit_code: 0\nstdout:\n".length()).split("\nstderr:", 2)[0];
        }
      }
    }
    for (var result : results.values()) {
      if (result.hasNonNull("sourceDocumentId")) reconciliation = result;
    }
    assertThat(stdout).as("actual successful remote Python stdout with deadline").isNotBlank();
    assertThat(stdout).contains("2027-01-13").doesNotContainIgnoringCase("fallback");
    assertThat(reconciliation).isNotNull();
    assertThat(reconciliation.path("sourceDocumentId").asText()).isEqualTo(documentId);
    var matchedTransactions = new HashSet<String>();
    for (var match : reconciliation.path("matches")) matchedTransactions.add(match.path("id").asText());
    assertThat(matchedTransactions).contains("TX-0911", "TX-0912");
    assertThat(api.get("/v2/process-instances/" + pi).path("state").asText()).isEqualTo("COMPLETED");
    assertThat(api.get("/v2/process-instances/" + pi).path("processDefinitionKey").asText()).isEqualTo(definitionKey);
    assertThat(cleanup.sandboxes(pi)).isEmpty();
    var tasks = api.post("/v2/user-tasks/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi), "state", "CREATED"), "page", Map.of("limit", 1)));
    assertThat(tasks.path("items").isEmpty()).isTrue();
    Path artifact = Path.of("proofs/artifacts/" + pi + "-stdout.txt");
    Files.createDirectories(artifact.getParent());
    Files.writeString(artifact, stdout);
    var evidence = new HashMap<String, Object>();
    evidence.put("realDaytona", true);
    evidence.put("mockOverride", false);
    evidence.put("fallback", false);
    evidence.put("remotePython", true);
    evidence.put("sandboxId", id);
    evidence.put("sandboxCreateCount", creates.size());
    evidence.put("sandboxAllocation", Map.of("cpu", 1, "memory", 1, "disk", 3));
    evidence.put("processInstanceKey", Long.toString(pi));
    evidence.put("processDefinitionKey", definitionKey);
    evidence.put("transactionDocumentId", documentId);
    evidence.put("reconciliation", reconciliation);
    evidence.put("stdoutArtifact", artifact.toString());
    evidence.put("stdoutSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(stdout.getBytes(StandardCharsets.UTF_8))));
    evidence.put("filingDeadline", "2027-01-13");
    evidence.put("fraudConversationCount", 1);
    evidence.put("finalOutcome", "COMPLETED");
    evidence.put("sandboxCleanup", true);
    evidence.put("instanceCleanup", true);
    assertThat(skillEvidence).as("actual history-correlated original skill execution").isNotNull();
    evidence.put("skillEvidence", skillEvidence);
    return evidence;
  }

  static String importedCsvId(JsonNode call, JsonNode result, Set<String> csvDocuments) {
    String id = call.path("document").path("documentId").asText();
    if (!"IMPORT_DOCUMENT".equals(call.path("operation").asText()) || !csvDocuments.contains(id)) return "";
    assertThat(result.isTextual() && result.asText().startsWith("Imported '")
        && result.asText().contains("bytes) to ")).as("successful CSV import result").isTrue();
    return id;
  }

  static String csvImportIdentity(Map<String, JsonNode> calls, Map<String, JsonNode> results, Set<String> csvDocuments) {
    assertThat(csvDocuments).as("Missing or ambiguous disputeDocuments[metadata.fileName=transactions-2026-09.csv].documentId")
        .hasSize(1);
    String imported = "";
    for (var entry : calls.entrySet()) {
      var result = results.get(entry.getKey());
      if (result == null) continue;
      String id = importedCsvId(entry.getValue(), result, csvDocuments);
      if (!id.isBlank()) imported = id;
    }
    assertThat(imported).as("Missing IMPORT_DOCUMENT.document.documentId matching the uploaded CSV in disputeDocuments; expected a successful Imported toolCallResult")
        .isNotBlank();
    return imported;
  }
}
