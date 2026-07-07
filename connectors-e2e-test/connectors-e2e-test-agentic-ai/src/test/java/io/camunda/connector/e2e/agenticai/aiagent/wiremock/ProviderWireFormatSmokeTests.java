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

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_JSON;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_JSON_ASSERTIONS;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentTest;
import io.camunda.connector.e2e.agenticai.aiagent.jobworker.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.BedrockConverseWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.AzureOpenAiCompletionsWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
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
 * ProviderWireFormatFixture}; the four scenarios below cover the wire-format areas most at risk of
 * drifting: plain text, tool calls, multimodal user-prompt documents, and structured JSON output.
 *
 * <p>Job-worker flavor only — the job-worker and outbound-connector flavors share the same
 * provider/wire-call code, so testing both would duplicate maintenance cost for no additional
 * wire-format coverage.
 */
@SlowTest
@ParameterizedClass(name = "{0}")
@MethodSource("fixtures")
@ExtendWith(SystemStubsExtension.class)
public class ProviderWireFormatSmokeTests extends BaseAiAgentJobWorkerTest {

  @Parameter ProviderWireFormatFixture fixture;

  static Stream<ProviderWireFormatFixture> fixtures() {
    return Stream.of(
        new OpenAiCompletionsWireFormatFixture(),
        new AnthropicMessagesWireFormatFixture(),
        new BedrockConverseWireFormatFixture(),
        new AzureOpenAiCompletionsWireFormatFixture());
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
          BaseAiAgentTest.httpsKeystoreFile().toString(),
          "javax.net.ssl.trustStorePassword",
          BaseAiAgentTest.HTTPS_KEYSTORE_PASSWORD,
          "javax.net.ssl.trustStoreType",
          "PKCS12");

  /**
   * Overridden directly (rather than the {@code withOpenAiCompatibleProvider} hook {@link
   * BaseAiAgentJobWorkerTest#createProcessInstance} composes) so this row's provider fixture, not
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
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
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

    assertThat(fixture.modelCallCount()).isEqualTo(1);
    ProviderWireFormatExpectedMessage.assertConversationMessages(
        fixture.lastRecordedRequest(),
        ProviderWireFormatExpectedMessage.system(expectedSystemPrompt()),
        ProviderWireFormatExpectedMessage.userWithDocument(userPrompt));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasResponseText(responseText));
  }

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
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
