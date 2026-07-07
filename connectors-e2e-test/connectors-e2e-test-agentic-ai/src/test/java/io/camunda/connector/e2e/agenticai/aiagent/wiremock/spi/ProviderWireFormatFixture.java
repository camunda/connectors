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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import io.camunda.connector.e2e.ElementTemplate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A provider/API-specific fixture plugged into the parameterized provider wire-format smoke test
 * suite: knows how to point the AI Agent connector at a local WireMock server for this provider's
 * wire format, how to stub assistant turns, and how to parse the requests the connector actually
 * sent back into the provider-agnostic {@link RecordedChatRequest} shape.
 *
 * <p>One implementation per provider/API (e.g. {@code OpenAiCompletions}, {@code
 * AnthropicMessages}, {@code BedrockConverse}, {@code AzureOpenAiCompletions}) is registered as a
 * row of the parameterized suite.
 */
public interface ProviderWireFormatFixture {

  /**
   * Display name of the provider/API, e.g. {@code "AnthropicMessages"}. Used as the JUnit
   * parameterized class display name (via {@code toString()}).
   */
  String apiName();

  /** Configures the element template to point the connector at this fixture's WireMock server. */
  Function<ElementTemplate, ElementTemplate> configureProvider(WireMockRuntimeInfo wireMock);

  /**
   * Wires the WireMock scenario chain returning each turn's response in order, mirroring the
   * conversation loop the connector is expected to drive.
   */
  void stubConversation(TurnStub... turns);

  /** Reads and parses all recorded model-call requests, oldest first. */
  List<RecordedChatRequest> recordedRequests();

  default int modelCallCount() {
    return recordedRequests().size();
  }

  default RecordedChatRequest lastRecordedRequest() {
    final var requests = recordedRequests();
    if (requests.isEmpty()) {
      throw new IllegalStateException("No model call requests were recorded");
    }
    return requests.getLast();
  }

  /**
   * Asserts that the given request actually carries the configured JSON schema on the wire: the
   * schema's {@code type}/{@code properties}/{@code required} must match {@code expectedSchema},
   * and (where the provider puts it on the wire at all) the schema name must match {@code
   * expectedSchemaName}. Override when a provider's wire format doesn't carry the schema name (see
   * {@code AnthropicMessagesWireFormatFixture}).
   */
  default void assertResponseFormatConfigured(
      RecordedChatRequest request, String expectedSchemaName, Map<String, Object> expectedSchema) {
    final var responseFormat = request.responseFormat();
    assertThat(responseFormat).as("structured output format in recorded request").isPresent();
    assertThat(responseFormat.get().type()).isEqualTo("json_schema");
    assertThat(responseFormat.get().schemaName()).isEqualTo(expectedSchemaName);
    assertSchemaContentMatches(responseFormat.get().jsonSchema(), expectedSchema);
  }

  @SuppressWarnings("unchecked")
  static void assertSchemaContentMatches(
      Map<String, Object> actualSchema, Map<String, Object> expectedSchema) {
    assertThat(actualSchema)
        .as("recorded JSON schema")
        .containsEntry("type", expectedSchema.get("type"))
        .containsEntry("properties", expectedSchema.get("properties"));
    // "required" is semantically a set - some providers don't preserve declaration order.
    assertThat((List<Object>) actualSchema.get("required"))
        .as("recorded JSON schema 'required'")
        .containsExactlyInAnyOrderElementsOf((List<Object>) expectedSchema.get("required"));
  }
}
