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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Plugs Azure OpenAI's Chat Completions wire format into the provider-agnostic {@link
 * ProviderWireFormatFixture} SPI, driving the connector through the v1 {@code azureOpenAi} element
 * template. With the v1&rarr;v2 provider-config rewrite switch on (the default), the v1 template's
 * {@code provider.azureOpenAi.*} config is rewritten onto the native v2 OpenAI provider's {@code
 * foundry} backend ({@code OpenAiChatModelConfiguration.OpenAiBackend.OpenAiFoundryBackend} - see
 * {@code V1ToV2ProviderConfigurationMapperImpl#mapAzureOpenAi}), which:
 *
 * <ul>
 *   <li>always drives the vendor SDK's streaming endpoint ({@code
 *       client.chat().completions().createStreaming(params)}) - so the response body must be a real
 *       {@code text/event-stream} SSE stream, exactly like the plain OpenAI/{@code
 *       openaiCompatible} rows. The request/response chunk shape is otherwise byte-for-byte
 *       identical to OpenAI's, so this fixture reuses {@link OpenAiCompletionsChatModelStubs}
 *       (already SSE-based), {@link OpenAiCompletionsRecordedConversation} and {@link
 *       OpenAiCompletionsRecordedChatRequestAdapter} directly rather than duplicating them.
 *   <li>normalizes the configured endpoint by appending a unified {@code /openai/v1} path segment
 *       ({@code AzureUrlPathMode.UNIFIED}) rather than the deployment-based path the old
 *       LangChain4j/azure-core client used ({@code
 *       /openai/deployments/{deploymentId}/chat/completions}) - the openai-java SDK then appends
 *       the family path itself, giving {@code {endpoint}/openai/v1/chat/completions}. There is no
 *       {@code api-version} query parameter unless one is explicitly configured (the v1&rarr;v2
 *       mapper never sets one), and the model is addressed via the body's {@code model} field, not
 *       a deployment-name path segment.
 *   <li>sends API-key authentication as a dedicated {@code api-key} header via the openai-java
 *       SDK's own credential type, not {@code Authorization: Bearer} and not {@code azure-core}'s
 *       {@code KeyCredentialPolicy} - so, unlike the pre-rewrite client, HTTPS is no longer
 *       enforced at the credential layer. This fixture still points at WireMock's HTTPS port for
 *       parity with real Azure endpoints; its self-signed certificate ({@code
 *       BaseAgentTest.httpsKeystoreFile()}) is trusted JVM-wide for this test run (see {@code
 *       ProviderWireFormatSmokeTests}).
 * </ul>
 */
public final class AzureOpenAiCompletionsWireFormatFixture implements ProviderWireFormatFixture {

  private static final String CHAT_COMPLETIONS_PATH = "/openai/v1/chat/completions";

  @Override
  public String apiName() {
    return "AzureOpenAiCompletions";
  }

  @Override
  public String toString() {
    return apiName();
  }

  @Override
  public Function<ElementTemplate, ElementTemplate> configureProvider(
      WireMockRuntimeInfo wireMock) {
    return template ->
        template
            .property("provider.type", "azureOpenAi")
            .property("provider.azureOpenAi.endpoint", wireMock.getHttpsBaseUrl())
            .property("provider.azureOpenAi.authentication.type", "apiKey")
            .property("provider.azureOpenAi.authentication.apiKey", "dummy")
            .property("provider.azureOpenAi.model.deploymentName", "test-model");
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    OpenAiCompletionsChatModelStubs.stubConversation(
        CHAT_COMPLETIONS_PATH,
        Arrays.stream(turns)
            .map(AzureOpenAiCompletionsWireFormatFixture::toStubTurn)
            .toArray(Turn[]::new));
  }

  private static Turn toStubTurn(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text -> Turn.text(text.text(), text.inputTokens(), text.outputTokens());
      case TurnStub.ToolCalls toolCalls ->
          Turn.toolCalls(
              toolCalls.text(),
              toolCalls.inputTokens(),
              toolCalls.outputTokens(),
              toolCalls.toolCalls().stream()
                  .map(tc -> ToolCall.of(tc.id(), tc.name(), tc.argumentsJson()))
                  .toArray(ToolCall[]::new));
    };
  }

  @Override
  public List<RecordedChatRequest> recordedRequests() {
    return OpenAiCompletionsRecordedConversation.recorded(CHAT_COMPLETIONS_PATH).requests().stream()
        .<RecordedChatRequest>map(OpenAiCompletionsRecordedChatRequestAdapter::new)
        .toList();
  }
}
