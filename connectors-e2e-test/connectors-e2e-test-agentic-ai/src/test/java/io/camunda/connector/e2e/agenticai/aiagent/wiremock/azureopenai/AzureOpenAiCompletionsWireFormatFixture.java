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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.azureopenai;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.azureopenai.AzureOpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.azureopenai.AzureOpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Plugs Azure OpenAI's Chat Completions wire format into the provider-agnostic {@link
 * ProviderWireFormatFixture} SPI.
 *
 * <p>The request/response body shape is identical to {@code OpenAiCompletionsWireFormatFixture};
 * see {@code AzureOpenAiCompletionsChatModelStubs} for details. Only the URL (deployment-based,
 * {@code {endpoint}/openai/deployments/{deploymentId}/chat/completions}) and authentication differ.
 *
 * <p>Azure's SDK ({@code azure-core}'s {@code KeyCredentialPolicy}) unconditionally rejects
 * non-HTTPS endpoints when using API-key authentication (hardcoded, no builder-level bypass) — so
 * unlike the other three fixtures, this one points at WireMock's HTTPS port, whose self-signed
 * certificate ({@code BaseAiAgentTest.httpsKeystoreFile()}) is also configured as the JVM's trust
 * store for this test run (see {@code ProviderWireFormatSmokeTests}).
 *
 * <p>Separately, {@code langchain4j-azure-openai}'s message mapper only handles {@code
 * TextContent}/{@code ImageContent} in user messages, not {@code PdfFileContent}, unlike
 * OpenAI/Anthropic/Bedrock.
 */
public final class AzureOpenAiCompletionsWireFormatFixture implements ProviderWireFormatFixture {

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
    AzureOpenAiCompletionsChatModelStubs.stubConversation(
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
    return AzureOpenAiCompletionsRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(AzureOpenAiCompletionsRecordedChatRequestAdapter::new)
        .toList();
  }
}
