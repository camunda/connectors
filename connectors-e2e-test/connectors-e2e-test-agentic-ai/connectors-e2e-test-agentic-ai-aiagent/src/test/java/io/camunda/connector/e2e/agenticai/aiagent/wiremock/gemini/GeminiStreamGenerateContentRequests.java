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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.camunda.connector.e2e.agenticai.aiagent.wiremock.gemini.StreamingGeminiChatModelStubs.STREAM_GENERATE_CONTENT_PATH_PATTERN;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inspects the {@code POST /v1beta/models/{model}:streamGenerateContent} requests the connector
 * actually sent to WireMock.
 *
 * <p>Lives here rather than on a base test class because <em>both</em> Gemini e2e base classes (the
 * sub-process flavor and the task flavor) need it; Anthropic could keep the equivalent helpers on
 * its single base class, this provider has two.
 *
 * <p>Deliberately thinner than {@code AnthropicMessagesRecordedConversation}: no normalization into
 * the shared {@code ProviderWireFormatExpectedMessage} DSL, because these tests assert Gemini's own
 * wire shape ({@code contents[].parts[]}, {@code generationConfig.thinkingConfig}, ...) directly
 * rather than a provider-agnostic projection of it.
 */
public final class GeminiStreamGenerateContentRequests {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** Extracts the model id out of the request path the SDK built. */
  private static final Pattern MODEL_PATTERN =
      Pattern.compile("/v1beta/models/(?<model>[^/]+):streamGenerateContent");

  private GeminiStreamGenerateContentRequests() {}

  /**
   * All recorded model-call requests, oldest first. Filters on the {@code alt=sse} query parameter
   * as well as the path, matching {@link StreamingGeminiChatModelStubs}' stub: a regression that
   * dropped SSE framing then surfaces as zero recorded requests instead of passing unnoticed.
   */
  public static List<LoggedRequest> recorded() {
    final List<LoggedRequest> requests =
        new ArrayList<>(
            findAll(
                postRequestedFor(urlPathMatching(STREAM_GENERATE_CONTENT_PATH_PATTERN))
                    .withQueryParam(
                        StreamingGeminiChatModelStubs.ALT_QUERY_PARAM,
                        equalTo(StreamingGeminiChatModelStubs.ALT_SSE))));
    requests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));
    return requests;
  }

  /** The single recorded model-call request, failing the test if there was not exactly one. */
  public static LoggedRequest sole() {
    final var requests = recorded();
    assertThat(requests).as("recorded model-call requests").hasSize(1);
    return requests.get(0);
  }

  /** Asserts the expected number of model calls happened and returns them, oldest first. */
  public static List<LoggedRequest> recorded(int expectedCount) {
    final var requests = recorded();
    assertThat(requests).as("recorded model-call requests").hasSize(expectedCount);
    return requests;
  }

  public static JsonNode parseBody(LoggedRequest loggedRequest) {
    try {
      return OBJECT_MAPPER.readTree(loggedRequest.getBodyAsString());
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse recorded Gemini generateContent request body: "
              + loggedRequest.getBodyAsString(),
          e);
    }
  }

  /**
   * The model id the SDK put into the request URL. Asserted instead of a fixed stub URL because the
   * stub matches any model id (see {@link StreamingGeminiChatModelStubs}).
   */
  public static String requestedModel(LoggedRequest loggedRequest) {
    final Matcher matcher = MODEL_PATTERN.matcher(loggedRequest.getUrl());
    if (!matcher.find()) {
      throw new IllegalStateException(
          "Recorded request URL does not look like a streamGenerateContent call: "
              + loggedRequest.getUrl());
    }
    return matcher.group("model");
  }
}
