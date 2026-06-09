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
package io.camunda.connector.e2e.agenticai.aiagent.jobworker;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_JSON;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_JSON_ASSERTIONS;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.agenticai.aiagent.wiremock.RecordedLlmConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SlowTest
public class AiAgentJobWorkerResponseHandlingTests extends BaseAiAgentJobWorkerTest {

  @Nested
  class ResponseText {

    @AfterEach
    void verifyRequestedResponseFormat() {
      assertThat(RecordedLlmConversation.recorded().lastRequest().responseFormat()).isEmpty();
    }

    @Test
    void fallsBackToResponseTextWhenNoResponsePropertiesAreConfigured() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate ->
              elementTemplate
                  .withoutPropertyValueStartingWith("data.response.includeAssistantMessage")
                  .withoutPropertyValueStartingWith("data.response.format."),
          HAIKU_TEXT,
          true,
          (agentResponse) ->
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
                  .hasResponseText(HAIKU_TEXT)
                  .hasNoResponseJson());
    }
  }

  @Nested
  class ResponseJson {

    private String expectedJsonSchemaName;

    @BeforeEach
    void setUp() {
      expectedJsonSchemaName = null;
    }

    @AfterEach
    void verifyRequestedResponseFormat() {
      final var fmt = RecordedLlmConversation.recorded().lastRequest().responseFormat();
      assertThat(fmt).isPresent();
      if (expectedJsonSchemaName == null) {
        assertThat(fmt.get().path("type").asText()).isEqualTo("json_object");
      } else {
        assertThat(fmt.get().path("type").asText()).isEqualTo("json_schema");
        assertThat(fmt.get().path("json_schema").path("name").asText())
            .isEqualTo(expectedJsonSchemaName);
        final var schema = fmt.get().path("json_schema").path("schema");
        assertThat(schema.path("type").asText()).isEqualTo("object");
        assertThat(schema.path("properties").has("text")).isTrue();
        assertThat(schema.path("properties").has("length")).isTrue();
        assertThat(schema.path("required").toString()).contains("text").contains("length");
      }
    }

    @Test
    void returnsJsonObject() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          elementTemplate -> elementTemplate.property("data.response.format.type", "json"),
          HAIKU_JSON,
          true,
          (agentResponse) ->
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
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
              JobWorkerAgentResponseAssert.assertThat(agentResponse)
                  .hasResponseMessageText(HAIKU_JSON)
                  .hasNoResponseText()
                  .hasResponseJsonSatisfying(HAIKU_JSON_ASSERTIONS));

      expectedJsonSchemaName = "HaikuSchema";
    }

    @Test
    void raisesIncidentWhenJsonCouldNotBeParsed() throws Exception {
      final var zeebeTest =
          setupBasicTestWithoutFeedbackLoop(
              testProcess,
              elementTemplate -> elementTemplate.property("data.response.format.type", "json"),
              Map.of(),
              HAIKU_TEXT);
      zeebeTest.waitForActiveIncidents();

      assertIncident(
          zeebeTest,
          incident -> {
            assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
            assertThat(incident.getErrorMessage())
                .startsWith("Failed to parse response content as JSON");
          });
    }
  }
}
