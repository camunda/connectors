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

import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.ProviderWireFormatFixture;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi.RecordedChatRequest;
import java.util.List;

/**
 * Shared wire-format plumbing for AWS Bedrock's Converse API, common to both the non-streaming
 * ({@link BedrockConverseV1WireFormatFixture}, plain buffered JSON via {@code converse}) and
 * streaming ({@link BedrockConverseV2WireFormatFixture}, AWS EventStream via {@code
 * converseStream}) fixtures — the request wire format and the recorded-request parsing are
 * identical between the two; only the response framing differs, so each fixture stubs its own
 * conversation independently.
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
abstract class AbstractBedrockConverseWireFormatFixture implements ProviderWireFormatFixture {

  @Override
  public String toString() {
    return apiName();
  }

  @Override
  public List<RecordedChatRequest> recordedRequests() {
    return BedrockConverseRecordedConversation.recorded().requests().stream()
        .<RecordedChatRequest>map(BedrockConverseRecordedChatRequestAdapter::new)
        .toList();
  }
}
