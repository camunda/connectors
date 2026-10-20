package demo;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byKey;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = GateApplication.class, properties = {
    "camunda.process-test.runtime-mode=remote",
    "camunda.process-test.data-deletion-mode=NONE",
    "camunda.process-test.clock-reset-enabled=false"
})
@CamundaSpringProcessTest
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class LiveTest {
  private static final boolean REAL_DAYTONA = Boolean.getBoolean("gate.realDaytona");
  @Autowired CamundaClient client;
  final Api api = new Api("http://localhost:8280", true);
  private int deployedPolicyVersion;
  private byte[] deployedPolicyBytes;

  @BeforeAll static void preflight() throws Exception {
    var api = new Api("http://localhost:8280", true);
    var topology = api.get("/v2/topology");
    assertThat(topology.path("brokers").isEmpty()).isFalse();
    for (var broker : topology.path("brokers")) {
      for (var partition : broker.path("partitions")) {
        assertThat(partition.path("health").asText()).isEqualToIgnoringCase("healthy");
      }
    }
    assertThat(new Api("http://localhost:8286", false).get("/actuator/health").path("status").asText()).isEqualTo("UP");
    assertThat(new Api("http://localhost:38090", false).get("/.well-known/agent-card.json").path("name").asText()).isNotBlank();
    CredentialSafety.credential(api.get("/v2/cluster-variables/global/cardDisputeLlm"));
  }

  private void deployPolicy(String path) throws Exception {
    var deployment = client.newDeployResourceCommand()
        .addResourceStringUtf8(Demo.text(path), "dispute-compliance-policy.md").send().join();
    var resources = deployment.getResource().stream()
        .filter(resource -> resource.getResourceName().equals("dispute-compliance-policy.md")).toList();
    assertThat(resources).hasSize(1);
    deployedPolicyVersion = resources.get(0).getVersion();
    assertThat(deployedPolicyVersion).isPositive();
    deployedPolicyBytes = api.deployedResource(Long.toString(resources.get(0).getResourceKey()),
        Demo.text(path).getBytes(StandardCharsets.UTF_8));
  }

  @Test @DisplayName("#83 - prove the regional web-search exception with a separate gate-owned agent")
  void webSearchProbe() throws Exception {
    WebSearchProbe.run(client, api);
  }

  private void deploy() throws Exception {
    if (REAL_DAYTONA) {
      var policies = api.post("/v2/resources/search", Map.of("filter",
          Map.of("resourceName", "dispute-compliance-policy.md"),
          "sort", List.of(Map.of("field", "version", "order", "DESC")), "page", Map.of("limit", 1))).path("items");
      assertThat(policies.isEmpty()).as("Select policy v1 before real proof").isFalse();
      var policy = policies.get(0);
      assertThat(api.binary("/v2/resources/" + policy.path("resourceKey").asText() + "/content/binary"))
          .as("Select policy v1 before real proof; gate never silently changes it")
          .isEqualTo(Demo.text("policies/dispute-compliance-policy.md").getBytes(StandardCharsets.UTF_8));
      deployedPolicyVersion = policy.path("version").asInt();
      deployedPolicyBytes = api.binary("/v2/resources/" + policy.path("resourceKey").asText() + "/content/binary");
      new SandboxCleanup(client, api).deploy();
      return;
    }
    client.newDeployResourceCommand().addResourceStringUtf8(Demo.text("card-dispute.bpmn"), "card-dispute.bpmn")
        .addResourceStringUtf8(Demo.text("review-dispute.form"), "review-dispute.form").send().join();
    deployPolicy("policies/dispute-compliance-policy.md");
  }

  private JsonNode upload(String file, String type) throws Exception {
    return upload(Demo.ROOT.resolve("data/" + file), type);
  }

  private JsonNode upload(java.nio.file.Path path, String type) throws Exception {
    String file = path.getFileName().toString();
    String boundary = "gate-" + UUID.randomUUID();
    var bytes = new ByteArrayOutputStream();
    bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + file
        + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    bytes.write(Files.readAllBytes(path));
    bytes.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"metadata\"\r\nContent-Type: application/json\r\n\r\n"
        + Demo.JSON.writeValueAsString(Map.of("contentType", type, "fileName", file))
        + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    var request = HttpRequest.newBuilder(URI.create("http://localhost:8280/v2/documents"))
        .timeout(Duration.ofSeconds(15)).header("Authorization", "Basic ZGVtbzpkZW1v")
        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
        .POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("document upload").isBetween(200, 299);
    return Demo.JSON.readTree(response.body());
  }

  private long start(FraudEndpoint fraud, DaytonaEndpoint daytona, List<Long> owned) throws Exception {
    var variables = Demo.variables(List.of(), "http://localhost:8280", "unused");
    var manifest = Demo.JSON.readValue(Files.readString(java.nio.file.Path.of("src/test/resources/round3/skill-manifest.json")),
        new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    variables.putAll(SkillInputs.upload(Demo.ROOT, manifest, "2026-10-20", this::upload));
    variables.remove("cardRulesKey");
    variables.put("fraudAgentUrl", fraud.baseUrl());
    if (!fraud.real()) variables.put("fraudPushUrl", "http://localhost:8286/inbound/a2a-fraud-agent-reply");
    if (daytona != null) {
      api.expectedDaytonaStubUrl = daytona.apiUrl();
      variables.put("daytonaApiUrl", daytona.apiUrl());
      variables.put("daytonaApiKey", DaytonaEndpoint.FAKE_KEY);
    }
    long key;
    if (REAL_DAYTONA) {
      assertThat(variables).doesNotContainKeys("daytonaApiUrl", "daytonaApiKey");
      var before = Demo.JSON.readTree(Files.readString(java.nio.file.Path.of("target/real-daytona-before.json")));
      long definitionKey = Long.parseLong(before.path("processDefinitionKey").asText());
      key = client.newCreateInstanceCommand().processDefinitionKey(definitionKey)
          .variables(variables).send().join().getProcessInstanceKey();
    } else {
      key = client.newCreateInstanceCommand().bpmnProcessId("CardDispute").latestVersion()
          .variables(variables).send().join().getProcessInstanceKey();
    }
    owned.add(key);
    Files.writeString(java.nio.file.Path.of("target/owned-instances.txt"), key + "\n",
        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
    return key;
  }

  private void draft(String task, String version) throws Exception {
    var draft = api.taskVariable(task, "draft");
    assertThat(draft.path("decision").asText()).isEqualTo("APPROVE");
    var evidenceText = draft.path("evidence").toString();
    assertThat(evidenceText).doesNotContain("Sandbox fallback");
    boolean pdfFacts = false;
    for (var item : draft.path("evidence")) {
      String text = item.asText();
      if (text.startsWith("Card statement (PDF):")
          && text.contains("AP-STM-7Q4K-0926") && text.contains("1,737")) pdfFacts = true;
    }
    assertThat(pdfFacts).as("#83 PDF-only statement ID and rewards facts in draft evidence").isTrue();
    assertThat(draft.path("refundAmount").decimalValue()).isEqualByComparingTo("482.17");
    assertThat(draft.path("reasonCode").asText()).isEqualTo("CN-DUP-01");
    var evidence = new ArrayList<String>();
    draft.path("evidence").forEach(e -> evidence.add(e.asText()));
    assertThat(String.join("\n", evidence)).contains("TX-0911", "TX-0912", "2027-01-13");
    assertThat(evidence).anyMatch(e -> e.startsWith("Fraud risk agent:"));
    var documents = api.taskVariable(task, "sourceDocuments");
    assertThat(documents.isArray()).isTrue();
    assertThat(documents.size()).isGreaterThanOrEqualTo(5);
    for (var document : documents) assertThat(document.path("documentId").asText()).isNotBlank();
  }

  private void cleanup(List<Long> owned) throws Exception {
    Exception failure = null;
    for (long pi : owned) {
      try {
        if ("ACTIVE".equals(api.get("/v2/process-instances/" + pi).path("state").asText())) {
          client.newCancelInstanceCommand(pi).send().join();
        }
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
            assertThat(api.post("/v2/user-tasks/search", Map.of("filter",
                Map.of("processInstanceKey", Long.toString(pi), "state", "CREATED"), "page", Map.of("limit", 1))).path("items").isEmpty()).isTrue());
      } catch (Exception | AssertionError e) {
        var wrapped = new Exception("Gate instance/task cleanup failed for " + pi, e);
        if (failure == null) failure = wrapped; else failure.addSuppressed(wrapped);
      }
      try {
        api.assertNoRawKeys(pi);
      } catch (Exception | AssertionError e) {
        var wrapped = new Exception("Credential safety scan failed for " + pi, e);
        if (failure == null) failure = wrapped; else failure.addSuppressed(wrapped);
      }
      try {
        if (REAL_DAYTONA) new SandboxCleanup(client, api).deleteOwned(pi);
      } catch (Exception | AssertionError e) {
        var wrapped = new Exception("Daytona cleanup failed for " + pi, e);
        if (failure == null) failure = wrapped; else failure.addSuppressed(wrapped);
      }
    }
    if (failure != null) throw failure;
  }

  private void finish(List<Long> owned, boolean restorePolicy, Throwable primary) throws Exception {
    Exception failure = null;
    try { cleanup(owned); }
    catch (Exception e) { failure = e; }
    if (restorePolicy) {
      try { deployPolicy("policies/dispute-compliance-policy.md"); }
      catch (Exception e) {
        if (failure == null) failure = e; else failure.addSuppressed(e);
      }
    }
    if (failure != null) {
      if (primary == null) throw failure;
      primary.addSuppressed(failure);
    }
  }

  @Test @org.junit.jupiter.api.Order(1)
  @DisplayName("L1 v1 real LLM, Daytona, guarded real A2A - revise then accept - completed")
  void l1() throws Exception {
    var owned = new ArrayList<Long>();
    RealDaytonaEvidence realEvidence = null;
    try (var fraud = new FraudEndpoint(true);
        var daytona = REAL_DAYTONA ? null : new DaytonaEndpoint()) {
      Throwable failure = null;
      try {
        deploy();
        long pi = start(fraud, daytona, owned);
        if (REAL_DAYTONA) realEvidence = new RealDaytonaEvidence(client, api, pi);
        var observation = realEvidence;
        String review = api.review(pi, "", () -> { if (observation != null) observation.observe(); });
        draft(review, "v1");
        var skill = SkillEvidence.capture(api, pi, review);
        if (realEvidence != null) realEvidence.skillEvidence = skill;
        if (!REAL_DAYTONA) PythonEvidence.tone("cold", api.taskVariable(review, "draft"), deployedPolicyBytes,
            Demo.ROOT.resolve("policies/dispute-compliance-policy.md").toString(), deployedPolicyVersion);
        var memory = new MemoryEvidence();
        var documents = api.taskVariable(review, "disputeDocuments");
        var documentIds = new java.util.HashSet<String>();
        for (var document : documents) documentIds.add(document.path("documentId").asText());
        assertThat(documentIds).hasSize(5);
        var initialMemory = memory.capture(api, pi, api.taskVariable(review, "agent"), documentIds, null);
        assertProvenance(pi, true);
        assertDocumentParsing(pi);
        api.assertNoRawKeys(pi);
        for (var tool : List.of("ReconcileTransactions", "AnalyzeInSandbox", "AskFraudRiskAgent")) {
          assertThat(api.completed(pi, tool)).as(tool + " completed").isGreaterThanOrEqualTo(1);
        }
        assertThat(api.completed(pi, "Fraud_Send")).isEqualTo(1);
        assertThat(api.completed(pi, "Fraud_Answer")).isEqualTo(1);
        assertThat(api.completed(pi, "Fraud_Timeout")).isZero();
        fraud.verifyOneSend();
        fraud.verifyAuthenticated();
        if (daytona != null) daytona.verifyUsed();
        assertThat(api.completed(pi, "Sandbox_Fallback")).as("no fallback may impersonate a sandbox run").isZero();
        assertNoSandboxFallback(pi);
        String reviewScope = api.get("/v2/user-tasks/" + review).path("elementInstanceKey").asText();
        assertThat(reviewScope).as("first review task owns submitted feedback").isNotBlank();
        api.complete(review, "revise");
        String second = api.review(pi, review, () -> { if (observation != null) observation.observe(); });
        String feedback = api.scopedVariable(pi, reviewScope, "analystFeedback").asText();
        assertThat(feedback).as("persisted first-review feedback equals submitted text").isEqualTo(Api.ANALYST_FEEDBACK);
        draft(second, "v1");
        assertThat(api.taskVariable(second, "disputeDocuments")).isEqualTo(documents);
        assertThat(api.taskVariable(second, "disputeFilingDate").asText()).isEqualTo("2026-10-20");
        assertThat(api.taskVariable(second, "deadlineScriptSha256").asText()).isEqualTo(SkillInputs.SCRIPT_SHA);
        assertThat(new MemoryEvidence().capture(api, pi, api.taskVariable(second, "agent"), documentIds, null, true))
            .as("revision USER history reveals the exact initial completed snapshot").isEqualTo(initialMemory);
        memory.capture(api, pi, api.taskVariable(second, "agent"), documentIds,
            feedback);
        api.assertNoRawKeys(pi);
        assertThat(api.completed(pi, "Fraud_Send")).isEqualTo(1);
        fraud.verifyOneSend();
        api.complete(second, "accept");
        assertThatProcessInstance(byKey(pi)).isCompleted();
        if (observation != null) observation.observe();
      } catch (Exception | AssertionError e) {
        failure = e;
        throw e;
      } finally {
        finish(owned, !REAL_DAYTONA, failure);
      }
      if (realEvidence != null) {
        var before = Demo.JSON.readTree(Files.readString(java.nio.file.Path.of("target/real-daytona-before.json")));
        Files.writeString(java.nio.file.Path.of("target/real-daytona-execution.json"),
            Demo.JSON.writeValueAsString(realEvidence.execution(before.path("processDefinitionKey").asText())));
      }
    }
  }

  @Test @org.junit.jupiter.api.Order(2)
  @DisplayName("L2 warm policy with WireMock A2A - APPROVE then accept - completed")
  void l2() throws Exception {
    var owned = new ArrayList<Long>();
    try (var fraud = new FraudEndpoint(false); var daytona = new DaytonaEndpoint()) {
      Throwable failure = null;
      try {
        deploy();
        deployPolicy("policies/v2/dispute-compliance-policy.md");
        long pi = start(fraud, daytona, owned);
        String review = api.review(pi, "");
        draft(review, "v2");
        SkillEvidence.capture(api, pi, review);
        PythonEvidence.tone("warm", api.taskVariable(review, "draft"), deployedPolicyBytes,
            Demo.ROOT.resolve("policies/v2/dispute-compliance-policy.md").toString(), deployedPolicyVersion);
        var documentIds = new java.util.HashSet<String>();
        for (var document : api.taskVariable(review, "disputeDocuments")) {
          documentIds.add(document.path("documentId").asText());
        }
        assertThat(documentIds).hasSize(5);
        new MemoryEvidence().capture(api, pi, api.taskVariable(review, "agent"), documentIds, null);
        assertProvenance(pi, false);
        assertDocumentParsing(pi);
        api.assertNoRawKeys(pi);
        fraud.verifyOneSend();
        fraud.verifyPush();
        assertThat(api.completed(pi, "Fraud_Answer")).isEqualTo(1);
        assertThat(api.completed(pi, "Fraud_Timeout")).isZero();
        fraud.verifyAuthenticated();
        daytona.verifyUsed();
        assertThat(api.completed(pi, "Sandbox_Fallback")).isZero();
        assertNoSandboxFallback(pi);
        api.complete(review, "accept");
        assertThatProcessInstance(byKey(pi)).isCompleted();
      } catch (Exception | AssertionError e) {
        failure = e;
        throw e;
      } finally {
        finish(owned, true, failure);
      }
    }
  }

  private void assertNoSandboxFallback(long pi) throws Exception {
    var variables = api.post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi), "name", "sandboxFallback"), "page", Map.of("limit", 100)));
    for (var variable : variables.path("items")) {
      assertThat(variable.path("value").asText()).isEqualTo("false");
    }
  }

  private void assertProvenance(long pi, boolean requireRemoteInstance) throws Exception {
    var response = api.post("/v2/variables/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi), "name", "toolCallResult"),
        "page", Map.of("limit", 1000)));
    assertThat(response.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
    boolean fraud = false;
    boolean document = false;
    boolean remote = false;
    for (var variable : response.path("items")) {
      if (variable.path("isTruncated").asBoolean()) {
        variable = api.get("/v2/variables/" + variable.path("variableKey").asText());
      }
      var result = Demo.JSON.readTree(variable.path("value").asText());
      if (result.path("evidence").path("kind").asText().equals("a2a")) {
        if (!result.path("taskId").asText().isBlank()
            && result.path("evidence").path("authScheme").asText().equals("bearer")
            && result.path("evidence").path("delivery").asText().equals("push")
            && result.path("state").asText().equals("TASK_STATE_COMPLETED")) fraud = true;
        if (requireRemoteInstance && !result.path("remoteProcessInstanceKey").asText().isBlank()
            && !result.path("remoteOperateUrl").asText().isBlank()) {
          var uri = URI.create(result.path("remoteOperateUrl").asText());
          if (uri.getHost() != null) {
            remote = true;
            System.out.println("#113 remote instance URL host:port=" + uri.getHost() + ":"
                + (uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort()));
          }
        }
      }
      if (!result.path("sourceDocumentId").asText().isBlank()) document = true;
    }
    assertThat(fraud).as("A2A result contains taskId and bearer evidence").isTrue();
    assertThat(document).as("reconciliation result contains sourceDocumentId").isTrue();
    assertThat(!requireRemoteInstance || remote).as("#113 real agent remote instance metadata").isTrue();
  }

  private void assertDocumentParsing(long pi) throws Exception {
    var agents = api.post("/v2/agent-instances/search", Map.of("filter",
        Map.of("processInstanceKey", Long.toString(pi)), "page", Map.of("limit", 100)));
    assertThat(agents.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(100);
    boolean configuration = false;
    boolean linkedPrompt = false;
    boolean userPdf = false;
    for (var agent : agents.path("items")) {
      var history = api.post("/v2/agent-instances/" + agent.path("agentInstanceKey").asText()
          + "/history/search", Map.of("page", Map.of("limit", 1000)));
      assertThat(history.path("page").path("totalItems").asInt()).isLessThanOrEqualTo(1000);
      for (var item : history.path("items")) {
        for (var evidence : item.path("content").findValues("evidence")) {
          if ("CONFIGURATION".equals(item.path("role").asText())) {
            var prompt = evidence.path("systemPrompt");
            linkedPrompt |= prompt.path("type").asText().equals("linked")
                && prompt.path("promptId").asText().equals("dispute-compliance-policy.md")
                && prompt.path("binding").asText().equals("latest")
                && prompt.path("version").asInt() == deployedPolicyVersion;
            var parsing = evidence.path("documentParsing");
            configuration |= parsing.path("plugin").asText().equals("file-parser")
                && parsing.path("pdfEngine").asText().equals("native");
          }
          if ("USER".equals(item.path("role").asText())
              && evidence.path("kind").asText().equals("documentParsing")) {
            for (var document : evidence.path("documents")) {
              userPdf |= document.path("fileName").asText().equals("statement-2026-09.pdf");
            }
          }
        }
      }
    }
    assertThat(configuration).as("#83 CONFIGURATION native file-parser evidence").isTrue();
    assertThat(linkedPrompt).as("#75 CONFIGURATION linked system prompt with deployed resource version").isTrue();
    System.out.println("#75 linked system prompt history: PASS (resource version " + deployedPolicyVersion + ")");
    assertThat(userPdf).as("#83 USER documentParsing PDF evidence").isTrue();
    System.out.println("#83 provider-side document parsing history: PASS");
  }
}
