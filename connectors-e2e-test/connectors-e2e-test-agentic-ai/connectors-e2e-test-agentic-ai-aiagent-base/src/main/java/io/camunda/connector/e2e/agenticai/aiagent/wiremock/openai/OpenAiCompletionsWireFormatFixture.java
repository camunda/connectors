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
 * Plugs the OpenAI-compatible chat completions stubs ({@link OpenAiCompletionsChatModelStubs} /
 * {@link OpenAiCompletionsRecordedConversation}, which also back the rest of the agentic-ai e2e
 * suite) into the provider-agnostic {@link ProviderWireFormatFixture} SPI.
 */
public final class OpenAiCompletionsWireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "OpenAiCompletions";
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
            .property("provider.type", "openaiCompatible")
            .property("provider.openaiCompatible.endpoint", wireMock.getHttpBaseUrl() + "/v1")
            .property("provider.openaiCompatible.authentication.apiKey", "dummy")
            .property("provider.openaiCompatible.model.model", "test-model");
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    OpenAiCompletionsChatModelStubs.stubConversation(
        Arrays.stream(turns)
            .map(OpenAiCompletionsWireFormatFixture::toStubTurn)
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
    return OpenAiCompletionsRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(OpenAiCompletionsRecordedChatRequestAdapter::new)
        .toList();
  }
}
