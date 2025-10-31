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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.outboundconnector;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_JSON;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_JSON_ASSERTIONS;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SlowTest
public class L4JAiAgentConnectorResponseHandlingTests extends BaseL4JAiAgentConnectorTest {

  @Nested
  class ResponseText {

    @AfterEach
    void verifyRequestedResponseFormat() {
      final var lastChatRequest = chatRequestCaptor.getValue();
      assertThat(lastChatRequest.responseFormat()).isNull();
    }

    @Test
    void fallsBackToResponseTextWhenNoResponsePropertiesAreConfigured() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate -> elementTemplate.withoutPropertyValueStartingWith("data.response."),
          HAIKU_TEXT,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasNoResponseMessage()
                  .hasResponseText(HAIKU_TEXT)
                  .hasNoResponseJson());
    }

    @Test
    void returnsResponseTextIfConfigured() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate ->
              elementTemplate
                  .property("data.response.format.type", "text")
                  .property("data.response.includeAssistantMessage", "=false"),
          HAIKU_TEXT,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasNoResponseMessage()
                  .hasResponseText(HAIKU_TEXT)
                  .hasNoResponseJson());
    }

    @Test
    void returnsResponseMessageIfConfigured() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate ->
              elementTemplate
                  .property("data.response.format.type", "text")
                  .property("data.response.includeAssistantMessage", "=true"),
          HAIKU_TEXT,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasResponseMessageText(HAIKU_TEXT)
                  .hasResponseText(HAIKU_TEXT)
                  .hasNoResponseJson());
    }

    @Test
    void triesToParseTextResponseAsJsonIfConfigured() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate ->
              elementTemplate
                  .property("data.response.format.type", "text")
                  .property("data.response.format.parseJson", "=true"),
          HAIKU_JSON,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasResponseText(HAIKU_JSON)
                  .hasResponseJsonSatisfying(HAIKU_JSON_ASSERTIONS));
    }

    @Test
    void returnsNullWhenTextResponseFailsToBeParsedAsJson() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate ->
              elementTemplate
                  .property("data.response.format.type", "text")
                  .property("data.response.format.parseJson", "=true"),
          HAIKU_TEXT,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasResponseText(HAIKU_TEXT)
                  .hasNoResponseJson());
    }
  }

  @Nested
  class ResponseJson {

    private JsonSchema expectedJsonSchema;

    @BeforeEach
    void setUp() {
      expectedJsonSchema = null;
    }

    @AfterEach
    void verifyRequestedResponseFormat() {
      final var lastChatRequest = chatRequestCaptor.getValue();
      assertThat(lastChatRequest.responseFormat())
          .isNotNull()
          .satisfies(
              format -> {
                assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);

                if (expectedJsonSchema == null) {
                  assertThat(format.jsonSchema()).isNull();
                } else {
                  assertThat(format.jsonSchema())
                      .usingRecursiveComparison()
                      .isEqualTo(expectedJsonSchema);
                }
              });
    }

    @Test
    void returnsJsonObject() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate -> elementTemplate.property("data.response.format.type", "json"),
          HAIKU_JSON,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasNoResponseText()
                  .hasResponseJsonSatisfying(HAIKU_JSON_ASSERTIONS));
    }

    @Test
    void returnsBothResponseJsonAndMessageIfConfigured() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate ->
              elementTemplate
                  .property("data.response.format.type", "json")
                  .property("data.response.includeAssistantMessage", "=true"),
          HAIKU_JSON,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasResponseMessageText(HAIKU_JSON)
                  .hasNoResponseText()
                  .hasResponseJsonSatisfying(HAIKU_JSON_ASSERTIONS));
    }

    @Test
    void requestContainsJsonSchemaAndSchemaNameIfConfigured() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate ->
              elementTemplate
                  .property("data.response.format.type", "json")
                  .property(
                      "data.response.format.schema",
                      """
                      ={
                        "type": "object",
                        "properties": {
                          "text": {
                            "type": "string"
                          },
                          "length": {
                            "type": "number"
                          }
                        },
                        "required": [
                          "text",
                          "length"
                        ]
                      }
                      """)
                  .property("data.response.format.schemaName", "HaikuSchema"),
          HAIKU_JSON,
          true,
          (agentResponse) ->
              AgentResponseAssert.assertThat(agentResponse)
                  .hasResponseMessageText(HAIKU_JSON)
                  .hasNoResponseText()
                  .hasResponseJsonSatisfying(HAIKU_JSON_ASSERTIONS));

      expectedJsonSchema =
          JsonSchema.builder()
              .name("HaikuSchema")
              .rootElement(
                  JsonObjectSchema.builder()
                      .addStringProperty("text")
                      .addNumberProperty("length")
                      .required("text", "length")
                      .build())
              .build();
    }

    @Test
    void raisesIncidentWhenJsonCouldNotBeParsed() throws Exception {
      final var setup =
          setupBasicTestWithoutFeedbackLoop(
              testProcess,
              elementTemplate -> elementTemplate.property("data.response.format.type", "json"),
              HAIKU_TEXT,
              Map.of());
      setup.getRight().waitForActiveIncidents();

      assertIncident(
          setup.getRight(),
          incident -> {
            assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
            assertThat(incident.getErrorMessage())
                .startsWith("Failed to parse response content as JSON");
          });
    }
  }
}
