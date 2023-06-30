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
package io.camunda.connector.runtime.inbound.webhook;

import static io.camunda.connector.runtime.core.Keywords.RESPONSE_BODY_EXPRESSION_KEYWORD;
import static io.camunda.connector.runtime.inbound.webhook.WebhookResponseMapper.PROCESS_DATA_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.feel.FeelEngineWrapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebhookResponseMapperTest {

  private WebhookResponseMapper testObject;

  @Mock private InboundConnectorResult correlatedResult;
  @Mock private Map<String, Object> properties;

  @BeforeEach
  void beforeEach() {
    testObject = new WebhookResponseMapper(new FeelEngineWrapper());

    when(correlatedResult.isActivated()).thenReturn(true);
    when(correlatedResult.getCorrelationPointId()).thenReturn("correlation-point-12345");
    when(correlatedResult.getType()).thenReturn("START_EVENT");
    when(correlatedResult.getResponseData()).thenReturn(Optional.of(Map.of("myKey", "myVal")));
  }

  @Test
  void produceResponse_ResponseBodyExpressionMatchesContext_ReturnMatchedResponse() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD))
        .thenReturn(
            "=if request.body.type = \"url_verification\" then {newChallenge: request.body.challenge} else null");

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "url_verification", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody())
        .containsEntry("newChallenge", "myChallenge")
        .doesNotContainKey(PROCESS_DATA_KEY);
  }

  @Test
  void produceResponse_ResponseBodyAbsent_ReturnProcessData() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD)).thenReturn(null);

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "url_verification", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody()).doesNotContainKey("newChallenge").containsKey(PROCESS_DATA_KEY);
  }

  @Test
  void produceResponse_ResponseBodyBlank_ReturnProcessData() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD)).thenReturn("");

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "url_verification", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody()).doesNotContainKey("newChallenge").containsKey(PROCESS_DATA_KEY);
  }

  @Test
  void
      produceResponse_ResponseBodyExpressionWithFeelExtensionMatchesContext_ReturnMatchedResponse() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD))
        .thenReturn(
            "=if request.body.type = \"url_verification\" then {newChallenge: request.body.challenge + upper case(\"aBc4\")} else null");

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "url_verification", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody())
        .containsEntry("newChallenge", "myChallengeABC4")
        .doesNotContainKey(PROCESS_DATA_KEY);
  }

  @Test
  void
      produceResponse_ResponseBodyExpressionDoesNotMatchContextFeelFallbackToNull_ReturnOnlyProcessData() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD))
        .thenReturn(
            "=if request.body.type = \"url_verification\" then {newChallenge: request.body.challenge} else null");

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "wrong_type", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody())
        .doesNotContainKey("newChallenge")
        .doesNotContainKey(PROCESS_DATA_KEY)
        .hasSize(0);
  }

  @Test
  void
      produceResponse_ResponseBodyExpressionDoesNotMatchContextFeelFallbackToEmpty_ReturnOnlyProcessData() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD))
        .thenReturn(
            "=if request.body.type = \"url_verification\" then {newChallenge: request.body.challenge} else {}");

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "wrong_type", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody())
        .doesNotContainKey("newChallenge")
        .doesNotContainKey(PROCESS_DATA_KEY);
  }

  @Test
  void produceResponse_ResponseBodyExpressionIsNull_ReturnOnlyProcessData() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD)).thenReturn(null);

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "wrong_type", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody()).doesNotContainKey("newChallenge").containsKey(PROCESS_DATA_KEY);
  }

  @Test
  void produceResponse_ResponseBodyExpressionIsEmptyString_ReturnOnlyProcessData() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD)).thenReturn("      ");

    // when
    ResponseEntity<Map> response =
        testObject.mapResponse(
            correlatedResult,
            properties,
            Map.of(
                "request",
                Map.of("body", Map.of("type", "wrong_type", "challenge", "myChallenge"))));

    // then
    assertThat(response.getBody()).doesNotContainKey("newChallenge").containsKey(PROCESS_DATA_KEY);
  }

  @Test
  void produceResponse_ContextWasEmpty_ReturnOnlyProcessData() {
    // given
    when(properties.get(RESPONSE_BODY_EXPRESSION_KEYWORD))
        .thenReturn(
            "=if request.body.type = \"url_verification\" then {newChallenge: request.body.challenge} else null");

    // when
    ResponseEntity<Map> response = testObject.mapResponse(correlatedResult, properties, Map.of());

    // then
    assertThat(response.getBody())
        .doesNotContainKey("newChallenge")
        .doesNotContainKey(PROCESS_DATA_KEY);
  }
}
