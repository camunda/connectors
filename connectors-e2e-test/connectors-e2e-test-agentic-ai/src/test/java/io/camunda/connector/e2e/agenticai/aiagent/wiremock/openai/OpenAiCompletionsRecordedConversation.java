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

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Inspects the chat completion requests the connector actually sent to WireMock.
 *
 * <p>Each model call produces one recorded request.
 */
public final class OpenAiCompletionsRecordedConversation {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<RecordedChatRequest> requests;

  private OpenAiCompletionsRecordedConversation(List<RecordedChatRequest> requests) {
    this.requests = requests;
  }

  /** Reads and parses all recorded {@code POST /v1/chat/completions} requests, oldest first. */
  public static OpenAiCompletionsRecordedConversation recorded() {
    final List<LoggedRequest> loggedRequests =
        new ArrayList<>(
            findAll(
                postRequestedFor(
                    urlPathEqualTo(OpenAiCompletionsChatModelStubs.CHAT_COMPLETIONS_PATH))));

    // WireMock does not guarantee ordering across versions; sort chronologically. The agent loop is
    // strictly sequential (each model call waits for the previous turn's tools), so logged
    // timestamps are unambiguous.
    loggedRequests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));

    final List<RecordedChatRequest> parsed =
        loggedRequests.stream()
            .map(LoggedRequest::getBodyAsString)
            .map(OpenAiCompletionsRecordedConversation::parse)
            .map(RecordedChatRequest::new)
            .toList();

    return new OpenAiCompletionsRecordedConversation(parsed);
  }

  private static JsonNode parse(String body) {
    try {
      return OBJECT_MAPPER.readTree(body);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse recorded chat request body: " + body, e);
    }
  }

  /** Number of model calls, equivalent to {@code chatRequestCaptor.getAllValues().size()}. */
  public int modelCallCount() {
    return requests.size();
  }

  public List<RecordedChatRequest> requests() {
    return requests;
  }

  /** The most recent request, equivalent to {@code chatRequestCaptor.getValue()}. */
  public RecordedChatRequest lastRequest() {
    if (requests.isEmpty()) {
      throw new IllegalStateException("No chat completion requests were recorded");
    }
    return requests.getLast();
  }

  /** A single parsed OpenAI-compatible chat completion request body. */
  public static final class RecordedChatRequest {

    private final JsonNode body;

    private RecordedChatRequest(JsonNode body) {
      this.body = body;
    }

    public JsonNode body() {
      return body;
    }

    /** The ordered {@code messages} array sent to the model. */
    public List<JsonNode> messages() {
      return elements("messages");
    }

    /** The {@code tools} array sent to the model (empty if none). */
    public List<JsonNode> tools() {
      return elements("tools");
    }

    /** Tool function names in the order they were declared. */
    public List<String> toolNames() {
      return tools().stream().map(tool -> tool.path("function").path("name").asText()).toList();
    }

    /** The {@code response_format} node, or empty if the request did not request a format. */
    public Optional<JsonNode> responseFormat() {
      final JsonNode node = body.get("response_format");
      return node == null || node.isNull() ? Optional.empty() : Optional.of(node);
    }

    private List<JsonNode> elements(String fieldName) {
      final JsonNode node = body.get(fieldName);
      if (node == null || !node.isArray()) {
        return List.of();
      }
      final List<JsonNode> result = new ArrayList<>();
      node.forEach(result::add);
      return result;
    }
  }
}
