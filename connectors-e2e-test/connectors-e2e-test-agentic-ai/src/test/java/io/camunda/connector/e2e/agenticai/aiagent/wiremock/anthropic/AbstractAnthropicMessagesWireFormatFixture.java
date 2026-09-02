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

import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.TurnStub;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;

/**
 * Shared wire-format plumbing for Anthropic's Messages API, common to both the v1 ({@link
 * AnthropicMessagesV1WireFormatFixture}) and v2 ({@link AnthropicMessagesV2WireFormatFixture})
 * fixtures. The v1 provider config is rewritten onto the native v2 provider at the connector
 * boundary, so both fixtures drive the exact same native streaming wire format and share this same
 * streaming stub ({@link #stubConversation}); they only differ in which element template drives the
 * connector and how it is pointed at the WireMock server (see {@code apiName()}/{@code
 * configureProvider(...)} on each subclass).
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
abstract class AbstractAnthropicMessagesWireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String toString() {
    return apiName();
  }

  @Override
  public void stubConversation(TurnStub... turns) {
    StreamingAnthropicMessagesSseChatModelStubs.stubConversation(turns);
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
