package demo;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class StructuralTest {
  private Document model() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return factory.newDocumentBuilder().parse(Demo.ROOT.resolve("card-dispute.bpmn").toFile());
  }

  private String value(String expression) throws Exception {
    return XPathFactory.newInstance().newXPath().evaluate(expression, model());
  }

  private String input(String id, String target) throws Exception {
    return value("//*[@id='" + id + "']//*[local-name()='input' and @target='" + target + "']/@source");
  }

  @Test @DisplayName("#75 - latest governed system prompt")
  void linkedPrompt() throws Exception {
    assertThat(value("count(//*[@id='DisputeAgent']//*[local-name()='linkedResource' and @resourceType='system-prompt' and @linkName='systemPrompt' and (not(@bindingType) or @bindingType='latest') and @resourceId='dispute-compliance-policy.md'])")).isEqualTo("1");
  }

  @Test @DisplayName("#81 - documents reach agent and human preview")
  void documents() throws Exception {
    assertThat(input("DisputeAgent", "data.userPrompt.documents")).isEqualTo("=disputeDocuments");
    assertThat(input("ReviewDraftedDecision", "sourceDocuments")).isEqualTo(
        "=(if agent.responseDocuments != null then concatenate(disputeDocuments, for e in get entries(agent.responseDocuments) return e.value.document) else disputeDocuments)[item != null]");
    assertThat(input("DisputeAgent", "data.userPrompt.documentDelivery")).isEqualTo("content");
    var form = Demo.json("review-dispute.form");
    assertThat(form.findValuesAsText("type")).contains("documentPreview");
    assertThat(form.findValuesAsText("dataSource")).contains("=sourceDocuments");
  }

  @Test @DisplayName("#82 - Daytona tool receives document-capable tool call")
  void sandbox() throws Exception {
    assertThat(value("//*[@id='AnalyzeInSandbox']//*[local-name()='taskDefinition']/@type")).isEqualTo("io.camunda.agenticai:sandboxdaytona:1");
    assertThat(input("AnalyzeInSandbox", "data.connectorMode.toolCall")).isEqualTo("=toolCall");
    linkedPrompt();
    assertThat(input("DisputeAgent", "data.systemPrompt.prompt")).isEmpty();
    for (var policy : new String[] {"policies/dispute-compliance-policy.md", "policies/v2/dispute-compliance-policy.md"}) {
      assertThat(Demo.text(policy)).contains("sandbox_fs_write", "sandbox_export_document",
          "scripts/deadline.py", "sandbox_fs_read", "deadline not verified",
          "Do not request hash-check commands", "Do not substitute fixture rows");
    }
  }

  @Test @DisplayName("#82 - document skill replaces REST rules and no fallback remains")
  void webFetch() throws Exception {
    for (String id : new String[] {"FetchCardNetworkRules", "Sandbox_Fallback", "Sandbox_FallbackRead"}) {
      assertThat(value("count(//*[@id='" + id + "'])")).isEqualTo("0");
    }
    assertThat(input("DisputeAgent", "data.userPrompt.prompt")).contains(
        "disputeFilingDate", "deadlineSkillBundles[1].documentId",
        "## Statement extraction\\n\\n- ", "## Transaction CSV\\n\\n- ",
        "## Reconciliation\\n\\n- ", "Do not request hash-check commands");
    assertThat(value("//*[@id='ReviewDraftedDecision']//*[local-name()='output' and @target='followUpInput']/@source"))
        .contains("## Analyst feedback\\n\\n", "## Revision\\n\\n- ", "analystFeedback");
    assertThat(SkillInputs.manifest(Demo.ROOT.resolve("skills/chargeback-deadline")))
        .isEqualTo(Demo.JSON.readValue(Files.readString(Path.of("src/test/resources/v29/skill-manifest.json")),
            new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, String>>() {}));
    try (var bundle = new java.util.zip.ZipFile(Demo.ROOT.resolve("skills/chargeback-deadline.zip").toFile())) {
      assertThat(bundle.stream().map(java.util.zip.ZipEntry::getName).toList())
          .containsExactlyInAnyOrderElementsOf(SkillInputs.PATHS);
      for (var relative : SkillInputs.PATHS) {
        try (var content = bundle.getInputStream(bundle.getEntry(relative))) {
          assertThat(content.readAllBytes()).isEqualTo(
              Files.readAllBytes(Demo.ROOT.resolve("skills/chargeback-deadline").resolve(relative)));
        }
      }
    }
  }

  @Test @DisplayName("#83 - web search input is omitted for the EU account")
  void webSearchOff() throws Exception {
    assertThat(value("count(//*[@id='DisputeAgent']//*[local-name()='input' and @target='provider.openai.api.completions.serverTools'])"))
        .as("web-search Off must omit the input, not merely set an empty value").isEqualTo("0");
  }

  @Test @DisplayName("#83 provider-side document parsing (file-parser plugin, live)")
  void documentParsing() throws Exception {
    assertThat(input("DisputeAgent", "provider.openai.backend.custom.bodyProperties"))
        .isEqualTo("={plugins: [{id: \"file-parser\", pdf: {engine: \"native\"}}]}");
    assertThat(Files.readAllBytes(Demo.ROOT.resolve("data/statement-2026-09.pdf")))
        .startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  }

  @Test @DisplayName("#113 - A2A 1.0 with real authentication (strict)")
  void authenticatedA2a() throws Exception {
    for (var id : new String[] {"Fraud_Send"}) {
      assertThat(input(id, "headers")).contains("\"A2A-Version\": \"1.0\"");
      assertThat(input(id, "authentication.type")).isEqualTo("bearer");
      assertThat(input(id, "authType")).isEqualTo("bearer");
      assertThat(input(id, "authentication.token")).isEqualTo("{{secrets.FRAUD_AGENT_TOKEN}}");
    }
    assertThat(input("Fraud_Send", "pushToken")).isEqualTo("{{secrets.A2A_PUSH_TOKEN}}");
    assertThat(input("Fraud_Send", "body")).contains("taskPushNotificationConfig", "webhookUrl", "pushToken");
    assertThat(value("//*[@id='Fraud_Answer']//*[local-name()='property' and @name='inbound.auth.apiKey']/@value"))
        .isEqualTo("{{secrets.A2A_PUSH_TOKEN}}");
    assertThat(value("//*[@id='Fraud_Answer']//*[local-name()='property' and @name='inbound.auth.type']/@value"))
        .isEqualTo("APIKEY");
    assertThat(value("//*[@id='Fraud_Answer']//*[local-name()='property' and @name='inbound.auth.apiKeyLocator']/@value"))
        .contains("x-a2a-notification-token");
  }

  @Test void sandboxFallbackWiring() throws Exception {
      assertThat(value("//*[@id='AnalyzeInSandbox']//*[local-name()='header' and @key='errorExpression']/@value"))
          .contains("bpmnError(\"SANDBOX_UNAVAILABLE\"", "sandboxError");
      assertThat(value("//*[@id='Sandbox_Error']/@attachedToRef")).isEqualTo("AnalyzeInSandbox");
      assertThat(value("//*[local-name()='error' and @id='Error_SandboxUnavailable']/@errorCode"))
          .isEqualTo("SANDBOX_UNAVAILABLE");
      assertThat(input("AnalyzeInSandbox", "data.apiUrl")).contains("daytonaApiUrl");
      assertThat(input("AnalyzeInSandbox", "data.apiKey")).contains("daytonaApiKey", "mock-daytona-key");
      assertThat(input("AnalyzeInSandbox", "toolCallResult")).isEqualTo("=null");
      assertThat(value("//*[@id='AnalyzeInSandbox']/*[local-name()='extensionElements']/*[local-name()='ioMapping']/*[local-name()='output' and @target='toolCallResult']/@source"))
          .isEqualTo("=toolCallResult");
      assertThat(value("//*[@id='Sandbox_Error']//*[local-name()='output' and @target='toolCallResult']/@source"))
          .isEqualTo("={error: \"SANDBOX_FAILED\", message: \"The sandbox call failed. The filing deadline is not computed.\"}");
      assertThat(value("count(//*[@id='Sandbox_Error']/*[local-name()='outgoing'])")).isEqualTo("0");
  }

  @Test void customMemory() throws Exception {
    assertThat(input("DisputeAgent", "data.memory.storage.type")).isEqualTo("custom");
    assertThat(input("DisputeAgent", "data.memory.storage.storeType")).isEqualTo("observation-masking");
    assertThat(input("DisputeAgent", "data.memory.storage.parameters")).isEqualTo("={}");
  }

  @Test @DisplayName("#86 - built template exposes custom-backend saved credential")
  void credentialTemplate() throws Exception {
    var template = Demo.JSON.readTree(Files.readString(Path.of("src/test/resources/agent-template.json")));
    var properties = template.path("properties");
    assertThat(properties.isArray()).isTrue();
    boolean found = false;
    for (var property : properties) {
      if ("provider.openai.backend.custom.openAiCustomCredential".equals(property.path("id").asText())) {
        assertThat(property.path("type").asText()).isEqualTo("Configuration");
        assertThat(property.path("binding").path("name").asText()).isEqualTo(property.path("id").asText());
        assertThat(property.path("configurationTemplate").asText()).isEqualTo("io.camunda:agentic-ai-openai-custom-credential:1");
        found = true;
      }
    }
    assertThat(found).as("#9041 credential selector").isTrue();
  }

  @Test @DisplayName("Live hook - both URLs use fraudAgentUrl with 38090 default")
  void fraudUrl() throws Exception {
    assertThat(input("Fraud_Send", "baseUrl")).contains("fraudAgentUrl", "http://localhost:38090");
    assertThat(input("Fraud_Send", "url")).isEqualTo("=baseUrl + operationPath");
    assertThat(input("Fraud_Send", "webhookUrl")).contains("fraudPushUrl", "host.docker.internal:8286/inbound/");
    assertThat(value("count(//*[@id='Fraud_GetTask'])")).isEqualTo("0");
  }

  @Test @DisplayName("Hygiene - LLM settings use saved credential, no inline LLM configuration")
  void llmSettings() throws Exception {
    assertThat(input("DisputeAgent", "provider.openai.backend.type")).isEqualTo("custom");
    assertThat(input("DisputeAgent", "provider.openai.backend.custom.openAiCustomCredential"))
        .isEqualTo("=camunda.vars.env.cardDisputeLlm");
    assertThat(input("DisputeAgent", "provider.openai.backend.custom.endpoint")).isEmpty();
    assertThat(input("DisputeAgent", "provider.openai.backend.custom.authentication.apiKey")).isEmpty();
    assertThat(input("DisputeAgent", "provider.openai.model.model")).isEmpty();
    assertThat(Demo.text("card-dispute.bpmn")).doesNotContain("CAMUNDA_PROVIDED_LLM_");
  }
}
