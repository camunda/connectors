package demo;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class SandboxCleanup {
  static final String PROCESS = "CptGateSandboxCleanup";
  private final CamundaClient client;
  private final Api api;
  private final Request transport;

  @FunctionalInterface interface Request {
    JsonNode send(String method, String url, Map<String, Object> query) throws Exception;
  }

  SandboxCleanup(CamundaClient client, Api api) {
    this.client = client;
    this.api = api;
    this.transport = this::remoteRequest;
  }

  SandboxCleanup(Request transport) {
    this.client = null;
    this.api = null;
    this.transport = transport;
  }

  void deploy() {
    // The HTTP connector resolves this placeholder; the harness never receives a secret.
    var model = Bpmn.createExecutableProcess(PROCESS).name("Gate sandbox cleanup")
        .startEvent("CleanupStart").name("Cleanup requested")
        .serviceTask("CleanupHttp").name("Call Daytona cleanup API")
        .zeebeJobType("io.camunda:http-json:1").zeebeJobRetries("1")
        .zeebeInput("bearer", "authentication.type")
        .zeebeInput("{{secrets.DAYTONA_API_KEY}}", "authentication.token")
        .zeebeInput("=cleanupMethod", "method")
        .zeebeInput("=cleanupUrl", "url")
        .zeebeInput("=cleanupQuery", "queryParameters")
        .zeebeInput("=false", "followRedirects")
        .zeebeInput("=20", "connectionTimeoutInSeconds")
        .zeebeInput("=20", "readTimeoutInSeconds")
        .zeebeTaskHeader("resultExpression", "={cleanupResult: if cleanupMethod = \"DELETE\" then {deleted: true} else if is defined(response.body.items) then {items: for s in response.body.items return {id: s.id, owner: s.labels.processInstanceKey, state: s.state}, nextCursor: response.body.nextCursor} else {id: response.body.id, owner: response.body.labels.processInstanceKey, cpu: response.body.cpu, memory: response.body.memory, disk: response.body.disk}}")
        .endEvent("CleanupEnd").name("Cleanup API responded").done();
    client.newDeployResourceCommand().addProcessModel(model, "cpt-gate-sandbox-cleanup.bpmn").send().join();
  }

  private JsonNode remoteRequest(String method, String url, Map<String, Object> query) throws Exception {
    long pi = client.newCreateInstanceCommand().bpmnProcessId(PROCESS).latestVersion()
        .variables(Map.of("cleanupMethod", method, "cleanupUrl", url, "cleanupQuery", query)).send().join().getProcessInstanceKey();
    try {
      org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(45))
          .ignoreExceptionsMatching(e -> e instanceof java.io.IOException && e.getMessage().endsWith("HTTP 404"))
          .untilAsserted(() -> {
        if (!"COMPLETED".equals(api.get("/v2/process-instances/" + pi).path("state").asText())) {
          throw new AssertionError("Daytona cleanup connector has not completed; gate cleanup instance " + pi);
        }

      });
      return api.variable(pi, "cleanupResult");
    } finally {
      if ("ACTIVE".equals(api.get("/v2/process-instances/" + pi).path("state").asText())) {
        client.newCancelInstanceCommand(pi).send().join();
      }
    }
  }

  List<String> sandboxes(long owner) throws Exception {
    var found = new ArrayList<String>();
    String cursor = "";
    for (int page = 0; page < 100; page++) {
      var query = new java.util.HashMap<String, Object>();
      query.put("labels", Demo.JSON.writeValueAsString(Map.of("processInstanceKey", Long.toString(owner))));
      query.put("limit", 100);
      if (!cursor.isBlank()) query.put("cursor", cursor);
      JsonNode result = request("GET", "https://app.daytona.io/api/sandbox", query);
      if (!result.path("items").isArray()) throw new AssertionError("Unexpected Daytona list result");
      for (var sandbox : result.path("items")) {
        if (!Long.toString(owner).equals(sandbox.path("owner").asText())) {
          throw new AssertionError("Daytona label filter returned a foreign sandbox; refusing deletion");
        }

        String id = sandbox.path("id").asText();
        if (!id.matches("[a-fA-F0-9-]{36}")) throw new AssertionError("Invalid Daytona sandbox id");
        found.add(id);
      }
      cursor = result.path("nextCursor").asText("");
      if (cursor.isBlank()) return found;
    }
    throw new AssertionError("Daytona pagination bound exceeded");
  }

  private JsonNode request(String method, String url, Map<String, Object> query) throws Exception {
    return transport.send(method, url, query);
  }

  JsonNode allocation(long owner, String id) throws Exception {
    var owned = sandboxes(owner);
    if (owned.size() != 1 || !owned.contains(id)) throw new AssertionError("Real proof requires exactly one owned sandbox");
    var sandbox = request("GET", "https://app.daytona.io/api/sandbox/" + id, Map.of());
    if (!Long.toString(owner).equals(sandbox.path("owner").asText()) || !id.equals(sandbox.path("id").asText())) {
      throw new AssertionError("Sandbox ownership does not match; refusing certification");
    }
    return sandbox;
  }

  void deleteOwned(long owner) throws Exception {
    var ids = sandboxes(owner);
    Exception failure = null;
    for (var id : ids) {
      try {
        request("DELETE", "https://app.daytona.io/api/sandbox/" + id, Map.of());
      } catch (Exception | AssertionError error) {
        if (failure == null) failure = new Exception("Owned sandbox deletion failed", error);
        else failure.addSuppressed(error);
      }
    }
    if (failure != null) throw failure;
    org.awaitility.Awaitility.await().pollInterval(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(90))
        .untilAsserted(() -> {
          if (!sandboxes(owner).isEmpty()) throw new AssertionError("Gate-owned Daytona sandboxes remain for " + owner);
        });
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/sandbox-cleanup.txt"),
        owner + ": deleted " + ids.size() + " sandboxes; verified none remain\n",
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
  }
}
