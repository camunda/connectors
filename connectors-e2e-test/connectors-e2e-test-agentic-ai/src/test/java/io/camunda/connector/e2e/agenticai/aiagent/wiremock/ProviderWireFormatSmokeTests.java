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
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentTest;
import io.camunda.connector.e2e.agenticai.aiagent.jobworker.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.azureopenai.AzureOpenAiCompletionsWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.BedrockConverseWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ToolCallStub;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;

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
public class ProviderWireFormatSmokeTests extends BaseAiAgentJobWorkerTest {

  @Parameter ProviderWireFormatFixture fixture;

  static Stream<ProviderWireFormatFixture> fixtures() {
    return Stream.of(
        new OpenAiCompletionsWireFormatFixture(),
        new AnthropicMessagesWireFormatFixture(),
        new BedrockConverseWireFormatFixture(),
        new AzureOpenAiCompletionsWireFormatFixture());
  }

  private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";
  private static final String TRUST_STORE_PASSWORD_PROPERTY = "javax.net.ssl.trustStorePassword";
  private static final String TRUST_STORE_TYPE_PROPERTY = "javax.net.ssl.trustStoreType";

  private static String previousTrustStore;
  private static String previousTrustStorePassword;
  private static String previousTrustStoreType;

  /**
   * Trusts WireMock's self-signed HTTPS certificate JVM-wide, for the {@code
   * AzureOpenAiCompletions} row (Azure's SDK requires HTTPS for API-key auth — see {@code
   * AzureOpenAiCompletionsWireFormatFixture}). Safe to set unconditionally: no other test in this
   * module makes real outbound HTTPS calls that would need the JVM's real default trust store.
   */
  @BeforeAll
  static void trustWireMockHttpsCertificate() {
    previousTrustStore = System.getProperty(TRUST_STORE_PROPERTY);
    previousTrustStorePassword = System.getProperty(TRUST_STORE_PASSWORD_PROPERTY);
    previousTrustStoreType = System.getProperty(TRUST_STORE_TYPE_PROPERTY);

    final var keystoreFile = BaseAiAgentTest.httpsKeystoreFile();
    System.setProperty(TRUST_STORE_PROPERTY, keystoreFile.toString());
    System.setProperty(TRUST_STORE_PASSWORD_PROPERTY, BaseAiAgentTest.HTTPS_KEYSTORE_PASSWORD);
    System.setProperty(TRUST_STORE_TYPE_PROPERTY, "PKCS12");
  }

  @AfterAll
  static void restoreTrustStore() {
    restoreProperty(TRUST_STORE_PROPERTY, previousTrustStore);
    restoreProperty(TRUST_STORE_PASSWORD_PROPERTY, previousTrustStorePassword);
    restoreProperty(TRUST_STORE_TYPE_PROPERTY, previousTrustStoreType);
  }

  private static void restoreProperty(String key, String previousValue) {
    if (previousValue == null) {
      System.clearProperty(key);
    } else {
      System.setProperty(key, previousValue);
    }
  }

  @Override
  protected ElementTemplate withOpenAiCompatibleProvider(ElementTemplate template) {
    return fixture.configureProvider(wireMock).apply(template);
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
    final var lastRequest = fixture.lastRecordedRequest();
    assertThat(lastRequest.messages()).hasSize(2);
    assertThat(lastRequest.messages().get(0).role()).isEqualTo("system");
    assertThat(lastRequest.messages().get(1).role()).isEqualTo("user");
    assertThat(lastRequest.messages().get(1).textContent()).isEqualTo(userPrompt);

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
