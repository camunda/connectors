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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.BedrockConverseChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock.BedrockConverseChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Plugs AWS Bedrock's Converse API wire format into the provider-agnostic {@link
 * ProviderWireFormatFixture} SPI.
 *
 * <p>Notable wire-level differences from OpenAI/Anthropic:
 *
 * <ul>
 *   <li>Content blocks are discriminated by which key is present ({@code text}, {@code toolUse},
 *       {@code toolResult}) rather than a {@code type} field.
 *   <li>The system prompt is a top-level {@code system} field (list of text blocks), and tool
 *       results for a turn are batched into a single {@code user}-role message, same as Anthropic.
 *   <li>JSON-schema structured output ({@code outputConfig.textFormat}) sends the schema
 *       <em>name</em> (unlike Anthropic, which drops it) but encodes the schema itself as a
 *       JSON-serialized <em>string</em> ({@code structure.jsonSchema.schema}), not a nested JSON
 *       object like OpenAI/Anthropic do.
 *   <li>The wire enum value for the format type is lowercase {@code "json_schema"}, not the
 *       uppercase Java SDK constant name ({@code OutputFormatType.JSON_SCHEMA}).
 * </ul>
 */
public final class BedrockConverseWireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String apiName() {
    return "BedrockConverse";
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
            .property("provider.type", "bedrock")
            .property("provider.bedrock.region", "us-east-1")
            .property("provider.bedrock.endpoint", wireMock.getHttpBaseUrl())
            .property("provider.bedrock.authentication.type", "credentials")
            .property("provider.bedrock.authentication.accessKey", "dummy")
            .property("provider.bedrock.authentication.secretKey", "dummy")
            .property("provider.bedrock.model.model", "test-model");
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    BedrockConverseChatModelStubs.stubConversation(
        Arrays.stream(turns)
            .map(BedrockConverseWireFormatFixture::toStubTurn)
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
    return BedrockConverseRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(BedrockConverseRecordedChatRequestAdapter::new)
        .toList();
  }
}
