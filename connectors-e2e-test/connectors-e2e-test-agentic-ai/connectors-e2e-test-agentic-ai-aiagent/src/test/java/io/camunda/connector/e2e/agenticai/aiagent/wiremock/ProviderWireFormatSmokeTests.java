/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock;

import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowAssertions.assertNoDocumentInternalsInLlmRequests;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.EXTRACTED_DOCUMENTS_PREAMBLE;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.HAIKU_JSON;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.HAIKU_JSON_ASSERTIONS;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAgentTest;
import io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions;
import io.camunda.connector.e2e.agenticai.aiagent.subprocess.BaseAgentSubProcessTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesV1WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesV2WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.BedrockConverseV1WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.BedrockConverseV2WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.AzureOpenAiCompletionsWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.MistralV2WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV1WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsV2WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiResponsesV2WireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.Resource;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;
import uk.org.webcompere.systemstubs.properties.SystemProperties;

/**
 * Wire-format e2e coverage per LLM provider/API, asserting on the actual request bodies the AI
 * Agent connector sends to each provider — a regression net for wire-format drift, distinct from
 * the full behavioral e2e suite that already exists for {@code openaiCompatible} (tool calling,
 * feedback loops, memory storage, MCP/A2A, etc.). One row per provider/API supplies a {@link
 * ProviderWireFormatFixture}; the five scenarios below cover the wire-format areas most at risk of
 * drifting: plain text, tool calls, multimodal user-prompt documents, the document of a tool result
 * that the model loads, and structured JSON output.
 *
 * <p>Job-worker flavor only — the job-worker and outbound-connector flavors share the same
 * provider/wire-call code, so testing both would duplicate maintenance cost for no additional
 * wire-format coverage.
 */
@SlowTest
@ParameterizedClass(name = "{0}")
@MethodSource("fixtures")
@ExtendWith(SystemStubsExtension.class)
public class ProviderWireFormatSmokeTests extends BaseAgentSubProcessTest {

  @Parameter ProviderWireFormatFixture fixture;

  static Stream<ProviderWireFormatFixture> fixtures() {
    return Stream.of(
        new OpenAiCompletionsV1WireFormatFixture(),
        new OpenAiCompletionsV2WireFormatFixture(),
        new AnthropicMessagesV1WireFormatFixture(),
        new AnthropicMessagesV2WireFormatFixture(),
        new BedrockConverseV1WireFormatFixture(),
        new BedrockConverseV2WireFormatFixture(),
        new AzureOpenAiCompletionsWireFormatFixture(),
        new OpenAiResponsesV2WireFormatFixture(),
        new MistralV2WireFormatFixture());
  }

  /**
   * Trusts WireMock's self-signed HTTPS certificate JVM-wide, for the {@code
   * AzureOpenAiCompletions} row (Azure's SDK requires HTTPS for API-key auth — see {@code
   * AzureOpenAiCompletionsWireFormatFixture}). Safe to set unconditionally: no other test in this
   * module makes real outbound HTTPS calls that would need the JVM's real default trust store.
   * {@code SystemStub}, being a static field, applies the properties once before the class's tests
   * run and restores their previous values once after, saving us the manual save/restore dance.
   */
  @SystemStub
  private static final SystemProperties TRUST_WIREMOCK_HTTPS_CERTIFICATE =
      new SystemProperties(
          "javax.net.ssl.trustStore",
          BaseAgentTest.httpsKeystoreFile().toString(),
          "javax.net.ssl.trustStorePassword",
          BaseAgentTest.HTTPS_KEYSTORE_PASSWORD,
          "javax.net.ssl.trustStoreType",
          "PKCS12");

  /**
   * Delegates to the fixture, which decides its own template unconditionally (v1 for the fixtures
   * proving the v1→v2 provider-config rewrite, v2 for the ones driving a native provider directly)
   * — see {@code ProviderWireFormatFixture#elementTemplatePath}.
   */
  @Override
  protected String elementTemplatePath() {
    return fixture.elementTemplatePath(super.elementTemplatePath());
  }

  /**
   * Delegates to the fixture, mirroring {@link #elementTemplatePath()} for the baseline
   * (non-provider) element template properties.
   */
  @Override
  protected Map<String, String> elementTemplateProperties() {
    return fixture.elementTemplateBaselineProperties(super.elementTemplateProperties());
  }

  /**
   * Overridden directly (rather than the {@code withOpenAiCompatibleProvider} hook {@link
   * BaseAgentSubProcessTest#createProcessInstance} composes) so this row's provider fixture, not
   * the openaiCompatible default, configures the element template — without touching the shared
   * base test classes other providers' scenarios also run through.
   */
  @Override
  protected ZeebeTest createProcessInstance(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      Map<String, Object> variables)
      throws IOException {
    final var composed = fixture.configureProvider(wireMock).andThen(elementTemplateModifier);
    final var updatedElementTemplate =
        elementTemplateWithModifications(elementTemplatePath(), composed);
    final var updatedElementTemplateFile =
        updatedElementTemplate.writeTo(new File(tempDir, "template.json"));
    final var updatedModel = modelWithModifications(process.getFile(), updatedElementTemplateFile);
    return createProcessInstance(customizeModel(updatedModel), variables);
  }

  @Test
  void singleTurnTextResponse() throws Exception {
    final var userPrompt = "Write a haiku about the sea";

    fixture.stubConversation(TurnStub.text(HAIKU_TEXT, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertThat(fixture.modelCallCount()).isEqualTo(1);
    ProviderWireFormatExpectedMessage.assertConversationMessages(
        fixture.lastRecordedRequest(),
        ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
        ProviderWireFormatExpectedMessage.user(userPrompt));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(HAIKU_TEXT));
  }

  @Test
  void toolCallingTurn() throws Exception {
    final var userPrompt = "Explore some of your tools!";
    final var toolCallMessage = "I will call the superflux calculation tool to see what it does.";
    final var finalMessage = "The superflux calculation of 5 and 3 results in 24.";

    fixture.stubConversation(
        TurnStub.toolCalls(
            toolCallMessage,
            10,
            20,
            new ToolCallStub("aaa111", "SuperfluxProduct", "{\"a\": 5, \"b\": 3}")),
        TurnStub.text(finalMessage, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertThat(fixture.modelCallCount()).isEqualTo(2);
    ProviderWireFormatExpectedMessage.assertConversationMessages(
        fixture.lastRecordedRequest(),
        ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
        ProviderWireFormatExpectedMessage.user(userPrompt),
        ProviderWireFormatExpectedMessage.assistantWithToolCalls(
            toolCallMessage, "SuperfluxProduct"),
        ProviderWireFormatExpectedMessage.toolCallResult("aaa111", "24"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(finalMessage));
  }

  @Test
  void documentInUserPrompt() throws Exception {
    final var userPrompt = "Summarize the following document";
    final var responseText = "TL;DR: it is pretty interesting";

    fixture.stubConversation(TurnStub.text(responseText, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of(
                    "userPrompt",
                    userPrompt,
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/test.jpg"))));

    // the document is referenced by its handle; its content is sent only for a template without a
    // delivery of the prompt documents
    assertThat(fixture.modelCallCount()).isEqualTo(1);
    ProviderWireFormatExpectedMessage.assertConversationMessages(
        fixture.lastRecordedRequest(),
        ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
        fixture.promptDocumentsAsContent()
            ? ProviderWireFormatExpectedMessage.userWithDocument(userPrompt)
            : ProviderWireFormatExpectedMessage.userWithDocumentHandle(userPrompt));
    assertNoDocumentInternalsInLlmRequests(fixture.recordedRequests());

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  @Test
  void documentRequestedByTheModel() throws Exception {
    final var userPrompt = "Summarize the following document";
    final var toolCallMessage = "I will read the document.";
    final var responseText = "TL;DR: it is pretty interesting";

    fixture.stubConversation(
        TurnStub.toolCalls(
            toolCallMessage,
            10,
            20,
            new ToolCallStub(
                "get111",
                "c8__load_document",
                "{\"id\": {\"type\":\"camunda.agenticai.document\",\"id\":\"@@DOC_ID_0@@\"}}")),
        TurnStub.text(responseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                Map.of(
                    "userPrompt",
                    userPrompt,
                    "downloadUrls",
                    List.of(wireMock.getHttpBaseUrl() + "/test.jpg"))));

    assertThat(fixture.modelCallCount()).isEqualTo(2);
    if (fixture.promptDocumentsAsContent()) {
      // the content of the document is in the conversation already
      ProviderWireFormatExpectedMessage.assertConversationMessages(
          fixture.lastRecordedRequest(),
          ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
          ProviderWireFormatExpectedMessage.userWithDocument(userPrompt),
          ProviderWireFormatExpectedMessage.assistantWithToolCalls(
              toolCallMessage, "c8__load_document"),
          ProviderWireFormatExpectedMessage.toolCallResultMatching(
              "get111",
              "The content of " + DOCUMENT_REF_REGEX + " is already in the conversation\\."));
    } else {
      // the content of the document follows the tool result in a message of its own
      ProviderWireFormatExpectedMessage.assertConversationMessages(
          fixture.lastRecordedRequest(),
          ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
          ProviderWireFormatExpectedMessage.userWithDocumentHandle(userPrompt),
          ProviderWireFormatExpectedMessage.assistantWithToolCalls(
              toolCallMessage, "c8__load_document"),
          ProviderWireFormatExpectedMessage.toolCallResultMatching(
              "get111", "The content of " + DOCUMENT_REF_REGEX + " follows in the next message\\."),
          ProviderWireFormatExpectedMessage.userWithDocument(
              ToolCallResultDocumentAssertions.DOCUMENT_CONTENT_PREAMBLE));
    }
    assertNoDocumentInternalsInLlmRequests(fixture.recordedRequests());

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  /**
   * A tool returns a document. With the documents feature, the model asks for its content and gets
   * it through {@code c8__load_document}. Without the feature, the content follows the tool results
   * in the older XML tag format.
   */
  @Test
  void documentOfAToolResult() throws Exception {
    if (fixture.documentsFeature()) {
      documentOfAToolResultRequestedByTheModel();
    } else {
      documentOfAToolResultOfAnInstanceWithoutTheDocumentsFeature();
    }
  }

  /**
   * The answer of the load-document tool says that the content follows, and the content follows in
   * a message of its own.
   */
  private void documentOfAToolResultRequestedByTheModel() throws Exception {
    final var userPrompt = "Download the document and summarize it";
    final var downloadMessage = "I will download the document.";
    final var toolCallMessage = "I will read the document.";
    final var responseText = "TL;DR: it is pretty interesting";

    fixture.stubConversation(
        TurnStub.toolCalls(
            downloadMessage,
            10,
            20,
            new ToolCallStub(
                "dl1111",
                "Download_A_File",
                "{\"url\": \"" + wireMock.getHttpBaseUrl() + "/test.jpg\"}")),
        TurnStub.toolCalls(
            toolCallMessage,
            10,
            20,
            new ToolCallStub(
                "get111",
                "c8__load_document",
                "{\"id\": {\"type\":\"camunda.agenticai.document\",\"id\":\"@@DOC_ID_0@@\"}}")),
        TurnStub.text(responseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertThat(fixture.modelCallCount()).isEqualTo(3);
    ProviderWireFormatExpectedMessage.assertConversationMessages(
        fixture.lastRecordedRequest(),
        ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
        ProviderWireFormatExpectedMessage.user(userPrompt),
        ProviderWireFormatExpectedMessage.assistantWithToolCalls(
            downloadMessage, "Download_A_File"),
        ProviderWireFormatExpectedMessage.toolCallResultMatching(
            "dl1111", "\\{\"status\":200,\"document\":" + HANDLE_REGEX + "\\}"),
        ProviderWireFormatExpectedMessage.assistantWithToolCalls(
            toolCallMessage, "c8__load_document"),
        ProviderWireFormatExpectedMessage.toolCallResultMatching(
            "get111", "The content of " + DOCUMENT_REF_REGEX + " follows in the next message\\."),
        ProviderWireFormatExpectedMessage.userWithDocument(
            ToolCallResultDocumentAssertions.DOCUMENT_CONTENT_PREAMBLE));
    assertNoDocumentInternalsInLlmRequests(fixture.recordedRequests());

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  /**
   * An agent instance without the documents feature offers no load-document tool. The tool result
   * holds the reference of the document, and the content follows in a message after the tool
   * results, with a tag in the older attribute format.
   */
  private void documentOfAToolResultOfAnInstanceWithoutTheDocumentsFeature() throws Exception {
    final var userPrompt = "Download the document and summarize it";
    final var downloadMessage = "I will download the document.";
    final var responseText = "TL;DR: it is pretty interesting";

    fixture.stubConversation(
        TurnStub.toolCalls(
            downloadMessage,
            10,
            20,
            new ToolCallStub(
                "dl1111",
                "Download_A_File",
                "{\"url\": \"" + wireMock.getHttpBaseUrl() + "/test.jpg\"}")),
        TurnStub.text(responseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(createProcessInstance(Map.of("userPrompt", userPrompt)));

    assertThat(fixture.modelCallCount()).isEqualTo(2);
    assertThat(fixture.recordedRequests())
        .allSatisfy(request ->
            assertThat(request.toolDefinitions())
                .extracting(ToolDefinition::name)
                .doesNotContain("c8__load_document"));
    ProviderWireFormatExpectedMessage.assertConversationMessages(
        fixture.lastRecordedRequest(),
        ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
        ProviderWireFormatExpectedMessage.user(userPrompt),
        ProviderWireFormatExpectedMessage.assistantWithToolCalls(
            downloadMessage, "Download_A_File"),
        ProviderWireFormatExpectedMessage.toolCallResultMatching(
            "dl1111", LEGACY_TOOL_RESULT_REGEX),
        ProviderWireFormatExpectedMessage.userWithTaggedDocument(
            EXTRACTED_DOCUMENTS_PREAMBLE, LEGACY_DOCUMENT_TAG_REGEX));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

  private static final String DOCUMENT_UUID_REGEX = "[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";

  /**
   * The tool result of an instance without the documents feature: the Camunda document reference,
   * with the store and the content hash.
   */
  private static final String LEGACY_TOOL_RESULT_REGEX =
      "\\{\"status\":200,\"document\":\\{\"storeId\":\"in-memory\",\"documentId\":\""
          + DOCUMENT_UUID_REGEX
          + "\",\"contentHash\":\"[0-9a-f]+\",\"metadata\":\\{\"contentType\":\"image/jpeg\"}"
          + ",\"camunda\\.document\\.type\":\"camunda\"}}";

  /** The tag of the document in the older attribute format. */
  private static final String LEGACY_DOCUMENT_TAG_REGEX =
      "<doc toolName=\"Download_A_File\" toolCallId=\"dl1111\" documentId=\""
          + DOCUMENT_UUID_REGEX
          + "\" storeId=\"in-memory\" contentType=\"image/jpeg\" />";

  /** The handle of a document, which the model reads as text. */
  private static final String HANDLE_REGEX =
      "\\{\"type\":\"camunda\\.agenticai\\.document\",\"id\":\"doc_[0-9a-f]{12}\"[^}]*}";

  /** The file name and the id of a document as the sentences about its content name it. */
  private static final String DOCUMENT_REF_REGEX = "(?:[^()]+ \\()?doc_[0-9a-f]{12}\\)?";

  private static final Map<String, Object> HAIKU_SCHEMA =
      Map.of(
          "type", "object",
          "properties",
              Map.of(
                  "text", Map.of("type", "string"),
                  "length", Map.of("type", "number")),
          "required", List.of("text", "length"));

  @Test
  void jsonResponseSchemaStructuredOutput() throws Exception {
    final var userPrompt = "Write a haiku about the sea";
    final var schemaName = "HaikuSchema";

    fixture.stubConversation(TurnStub.text(HAIKU_JSON, 10, 20));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                elementTemplate ->
                    elementTemplate
                        .property("data.response.format.type", "json")
                        .property("data.response.format.schema", "=" + toJson(HAIKU_SCHEMA))
                        .property("data.response.format.schemaName", schemaName),
                Map.of("userPrompt", userPrompt)));

    assertThat(fixture.modelCallCount()).isEqualTo(1);
    fixture.assertResponseFormatConfigured(fixture.lastRecordedRequest(), schemaName, HAIKU_SCHEMA);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseJsonSatisfying(HAIKU_JSON_ASSERTIONS));
  }

  private static String toJson(Map<String, Object> value) {
    try {
      return new ObjectMapper().writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
