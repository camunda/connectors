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
package io.camunda.connector.e2e.agenticai.e2e;

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH;

import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.e2e.app.TestConnectorRuntimeApplication;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.agenticai.tools.process-definition.cache.enabled=false",
      "camunda.connector.agenticai.aiagent.chat-model.api.default-timeout=PT2M",
      "logging.level.io.camunda.connector.agenticai=TRACE"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@CamundaSpringProcessTest
@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_LLM_E2E", matches = "true")
@Tag(RealProviderCapabilityTags.STRUCTURED_OUTPUT)
class RealProviderStructuredOutputE2ETestIT extends RealProviderApiSmokeSupport {

  private static final String ACCESS_JOB_TYPE = "lookup-access-authorization";
  private static final String NONCE_ACCESS_CODE = "Vantablack-3";
  private static final String ACCESS_AUTHORIZATION_SECRET =
      "ACCESS AUTHORIZATION: The access authorization code is " + NONCE_ACCESS_CODE + ".";
  private static final String MULTI_TOOL_RESPONSE_SCHEMA =
      "{\"type\":\"object\","
          + "\"properties\":{\"codeName\":{\"type\":\"string\"},\"accessCode\":{\"type\":\"string\"}},"
          + "\"required\":[\"codeName\",\"accessCode\"]}";

  private final AtomicReference<String> capturedAccessAuthorizationCodeName =
      new AtomicReference<>();

  @BeforeEach
  void mockAccessAuthorizationTool() {
    processTestContext
        .mockJobWorker(ACCESS_JOB_TYPE)
        .withHandler(
            (jobClient, job) -> {
              var codeName = String.valueOf(job.getVariablesAsMap().get("codeName"));
              capturedAccessAuthorizationCodeName.set(codeName);
              if (codeName.isBlank() || "null".equals(codeName)) {
                throw new IllegalStateException(
                    "Lookup Access Authorization called without a resolvable codeName argument");
              }
              jobClient
                  .newCompleteCommand(job)
                  .variable("toolCallResult", ACCESS_AUTHORIZATION_SECRET)
                  .send()
                  .join();
            });
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithStructuredOutput")
  void structuredOutputWithMultipleToolCallsReturnsSchemaConformingJson(ProviderConfig provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template ->
                template
                    .property("data.response.format.type", "json")
                    .property("data.response.format.schema", "=" + MULTI_TOOL_RESPONSE_SCHEMA)
                    .property("data.response.format.schemaName", "ClassifiedAccess"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            DEFAULT_SYSTEM_PROMPT,
            Map.of(
                "userPrompt",
                "What is the internal project code name, and what is the access authorization "
                    + "code? Use your lookup tools to get both, then return them as JSON."));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .metricsSatisfy(metrics -> Assertions.assertThat(metrics.toolCalls()).isEqualTo(2))
                .hasResponseJsonSatisfying(
                    json -> {
                      @SuppressWarnings("unchecked")
                      var map = (Map<String, Object>) json;
                      Assertions.assertThat(map).containsKeys("codeName", "accessCode");
                      Assertions.assertThat(normalizeDashes(String.valueOf(map.get("codeName"))))
                          .contains(NONCE_CODE_NAME);
                      Assertions.assertThat(normalizeDashes(String.valueOf(map.get("accessCode"))))
                          .contains(NONCE_ACCESS_CODE);
                    }));

    Assertions.assertThat(normalizeDashes(capturedAccessAuthorizationCodeName.get()))
        .as("codeName argument passed to Lookup Access Authorization")
        .contains(NONCE_CODE_NAME);
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithStructuredOutput")
  void structuredOutputReturnsSchemaConformingJson(ProviderConfig provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template ->
                template
                    .property("data.response.format.type", "json")
                    .property("data.response.format.schema", "=" + RESPONSE_SCHEMA)
                    .property("data.response.format.schemaName", "ClassifiedFact"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            DEFAULT_SYSTEM_PROMPT,
            Map.of(
                "userPrompt",
                "Look up the internal project code name and clearance level and return them."));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .hasResponseJsonSatisfying(
                    json -> {
                      @SuppressWarnings("unchecked")
                      var map = (Map<String, Object>) json;
                      Assertions.assertThat(map).containsKeys("codeName", "clearanceLevel");
                      Assertions.assertThat(normalizeDashes(String.valueOf(map.get("codeName"))))
                          .contains(NONCE_CODE_NAME);
                      Assertions.assertThat(
                              normalizeDashes(String.valueOf(map.get("clearanceLevel"))))
                          .contains(NONCE_CLEARANCE);
                    }));
  }
}
