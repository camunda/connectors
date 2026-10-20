package demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;

final class SkillEvidence {
  static JsonNode capture(Api api, long pi, String task) throws Exception {
    var raw = Demo.JSON.createObjectNode();
    raw.put("skillDirectory", Demo.ROOT.resolve("skills/chargeback-deadline").toString());
    raw.set("manifest", api.taskVariable(task, "deadlineSkillManifest"));
    raw.put("filingDate", api.taskVariable(task, "disputeFilingDate").asText());
    raw.put("deadlineScriptSha256", api.taskVariable(task, "deadlineScriptSha256").asText());
    raw.set("draftEvidence", api.taskVariable(task, "draft").path("evidence"));
    var documents = api.taskVariable(task, "disputeDocuments");
    raw.set("documents", documents);
    var hashes = raw.putObject("documentSha256");
    for (var doc : documents) {
      String name = doc.path("metadata").path("fileName").asText();
      if (!java.util.List.of("SKILL.md", "network-rules.md", "deadline.py").contains(name)) continue;
      String id = doc.path("documentId").asText();
      assertThat(id).matches("[a-zA-Z0-9-]+");
      var bytes = api.binary("/v2/documents/" + id + "?storeId="
          + java.net.URLEncoder.encode(doc.path("storeId").asText(), java.nio.charset.StandardCharsets.UTF_8)
          + "&contentHash=" + java.net.URLEncoder.encode(doc.path("contentHash").asText(),
              java.nio.charset.StandardCharsets.UTF_8));
      hashes.put(id, HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    var elements = api.elements(pi);
    raw.set("elements", elements);
    var children = raw.putObject("scopeChildren");
    for (var element : elements) {
      if (!element.path("type").asText().equals("AD_HOC_SUB_PROCESS_INNER_INSTANCE")) continue;
      String scope = element.path("elementInstanceKey").asText();
      var response = api.post("/v2/element-instances/search", Map.of("filter",
          Map.of("processInstanceKey", Long.toString(pi), "elementInstanceScopeKey", scope),
          "page", Map.of("limit", 1000)));
      assertThat(response.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
      var keys = children.putArray(scope);
      for (var child : response.path("items")) keys.add(child.path("elementInstanceKey").asText());
    }
    var variables = api.post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 1000)));
    assertThat(variables.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
    var safe = raw.putArray("variables");
    for (var variable : variables.path("items")) {
      String name = variable.path("name").asText();
      if (!java.util.List.of("toolCall", "toolCallResult").contains(name)) continue;
      var item = safe.addObject().put("name", name).put("scopeKey", variable.path("scopeKey").asText());
      item.set("value", api.variableValue(variable));
    }
    var agents = api.post("/v2/agent-instances/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 100)));
    assertThat(agents.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(100);
    var history = raw.putArray("history");
    for (var agent : agents.path("items")) {
      var response = api.post("/v2/agent-instances/" + agent.path("agentInstanceKey").asText()
          + "/history/search", Map.of("page", Map.of("limit", 1000)));
      assertThat(response.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
      for (var item : response.path("items")) history.add(item);
    }
    CredentialSafety.noRawKeys(raw, api.expectedDaytonaStubUrl);
    Path path = Path.of("proofs/artifacts/" + pi + "-skill-history.json");
    Files.createDirectories(path.getParent());
    Files.writeString(path, Demo.JSON.writeValueAsString(raw));
    PythonEvidence.validate("skill_history", "normalize", path);
    return Demo.JSON.readTree(Files.readString(path.resolveSibling(pi + "-skill-history-normalized.json")));
  }

  private SkillEvidence() {}
}
