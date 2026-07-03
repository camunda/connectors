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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic.AnthropicMessagesChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.assertj.core.api.Assertions;

/**
 * Plugs Anthropic's Messages API wire format into the provider-agnostic {@link
 * ProviderWireFormatFixture} SPI.
 *
 * <p>Notable wire-level differences from OpenAI's Chat Completions format:
 *
 * <ul>
 *   <li>The system prompt is sent as a top-level {@code system} field, not a {@code system}-role
 *       message.
 *   <li>Tool results for a turn are batched into {@code tool_result} content blocks of a single
 *       {@code user}-role message rather than one message per tool call.
 *   <li>JSON-schema structured output is a native wire field ({@code output_config.format}), but
 *       the schema <em>name</em> configured on the connector is dropped; only the raw JSON schema
 *       is sent.
 *   <li>Anthropic rejects schemaless JSON mode outright ({@code UnsupportedFeatureException}) — out
 *       of scope here since this suite always supplies a schema.
 * </ul>
 */
public final class AnthropicMessagesWireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "AnthropicMessages";
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
            .property("provider.type", "anthropic")
            .property("provider.anthropic.endpoint", wireMock.getHttpBaseUrl() + "/v1/")
            .property("provider.anthropic.authentication.apiKey", "dummy")
            .property("provider.anthropic.model.model", "test-model");
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    AnthropicMessagesChatModelStubs.stubConversation(
        Arrays.stream(turns)
            .map(AnthropicMessagesWireFormatFixture::toStubTurn)
            .toArray(Turn[]::new));
  }

  private static Turn toStubTurn(TurnStub turn) {
    return switch (turn) {
      case TurnStub.Text text ->
          Turn.text(text.text(), text.promptTokens(), text.completionTokens());
      case TurnStub.ToolCalls toolCalls ->
          Turn.toolCalls(
              toolCalls.text(),
              toolCalls.promptTokens(),
              toolCalls.completionTokens(),
              toolCalls.toolCalls().stream()
                  .map(tc -> ToolCall.of(tc.id(), tc.name(), tc.argumentsJson()))
                  .toArray(ToolCall[]::new));
    };
  }

  @Override
  public List<RecordedChatRequest> recordedRequests() {
    return AnthropicMessagesRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(AnthropicMessagesRecordedChatRequestAdapter::new)
        .toList();
  }

  @Override
  public void assertResponseFormatConfigured(
      RecordedChatRequest request, String expectedSchemaName, Map<String, Object> expectedSchema) {
    final var responseFormat = request.responseFormat();
    Assertions.assertThat(responseFormat)
        .as("output_config.format in recorded request")
        .isPresent();
    Assertions.assertThat(responseFormat.get().type()).isEqualTo("json_schema");
    // Anthropic's wire format has no field for the schema name configured on the connector -
    // only the raw JSON schema is sent, so expectedSchemaName is intentionally not asserted here.
    ProviderWireFormatFixture.assertSchemaContentMatches(
        responseFormat.get().jsonSchema(), expectedSchema);
  }
}
