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
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_TASK_V2_ELEMENT_TEMPLATE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "RUN_NATIVE_LLM_E2E", matches = "true")
@Tag(RealProviderCapabilityTags.STRUCTURED_OUTPUT)
class RealProviderStructuredOutputE2ETestIT extends RealProviderApiSmokeSupport {

  private static final String ACCESS_JOB_TYPE = "lookup-access-authorization";
  private static final String MULTI_TOOL_BPMN_RESOURCE = "classpath:real-provider-multi-tool.bpmn";
  private static final String NO_TOOL_BPMN_RESOURCE = "classpath:real-provider-no-tools.bpmn";
  private static final String MULTI_TOOL_SYSTEM_PROMPT =
      DEFAULT_SYSTEM_PROMPT
          + " When the user's request requires a tool whose input depends on another tool's result, "
          + "call only the prerequisite tool in the first model invocation. After its result "
          + "appears in the conversation, immediately call the dependent tool in the next model "
          + "invocation before answering. Do not wait for another user message, and never call both "
          + "tools in the same model invocation.";
  private static final String STRUCTURED_OUTPUT_SYSTEM_PROMPT =
      DEFAULT_SYSTEM_PROMPT
          + " Call the Lookup Classified Fact tool at most once. After its result appears in the "
          + "conversation, return the final JSON response immediately and do not call any tool "
          + "again.";
  private static final String NO_TOOL_STRUCTURED_OUTPUT_SYSTEM_PROMPT =
      "Return the values supplied by the user as the final JSON response.";
  private static final String NONCE_ACCESS_CODE = "Vantablack-3";
  private static final String DIRECT_CODE_NAME = "ZypherionDirect4";
  private static final String DIRECT_CLEARANCE = "CobaltDirect2";
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
  @MethodSource("providersWithStructuredOutputAndTools")
  void structuredOutputWithMultipleToolCallsReturnsSchemaConformingJson(ProviderConfig provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            MULTI_TOOL_BPMN_RESOURCE,
            template ->
                template
                    .property("data.response.format.type", "json")
                    .property("data.response.format.schema", "=" + MULTI_TOOL_RESPONSE_SCHEMA)
                    .property("data.response.format.schemaName", "ClassifiedAccess"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            MULTI_TOOL_SYSTEM_PROMPT,
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
                .metricsSatisfy(metrics -> assertThat(metrics.toolCalls()).isEqualTo(2))
                .hasResponseJsonSatisfying(
                    json -> {
                      @SuppressWarnings("unchecked")
                      var map = (Map<String, Object>) json;
                      assertThat(map).containsKeys("codeName", "accessCode");
                      assertThat(normalizeDashes(String.valueOf(map.get("codeName"))))
                          .contains(NONCE_CODE_NAME);
                      assertThat(normalizeDashes(String.valueOf(map.get("accessCode"))))
                          .contains(NONCE_ACCESS_CODE);
                    }));

    assertThat(normalizeDashes(capturedAccessAuthorizationCodeName.get()))
        .as("codeName argument passed to Lookup Access Authorization")
        .contains(NONCE_CODE_NAME);
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithStructuredOutputAndTools")
  void structuredOutputReturnsSchemaConformingJson(ProviderConfig provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            template ->
                template
                    .property("data.response.format.type", "json")
                    .property("data.response.format.schema", "=" + RESPONSE_SCHEMA)
                    .property("data.response.format.schemaName", "ClassifiedFact"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            STRUCTURED_OUTPUT_SYSTEM_PROMPT,
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
                      assertThat(map).containsKeys("codeName", "clearanceLevel");
                      assertThat(normalizeDashes(String.valueOf(map.get("codeName"))))
                          .contains(NONCE_CODE_NAME);
                      assertThat(normalizeDashes(String.valueOf(map.get("clearanceLevel"))))
                          .contains(NONCE_CLEARANCE);
                    }));
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithStructuredOutputWithoutTools")
  void structuredOutputWithoutToolsReturnsSchemaConformingJson(ProviderConfig provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_TASK_V2_ELEMENT_TEMPLATE_PATH,
            NO_TOOL_BPMN_RESOURCE,
            template ->
                template
                    .property("data.response.format.type", "json")
                    .property("data.response.format.schema", "=" + RESPONSE_SCHEMA)
                    .property("data.response.format.schemaName", "DirectFact"));

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            NO_TOOL_STRUCTURED_OUTPUT_SYSTEM_PROMPT,
            Map.of(
                "userPrompt",
                "Return codeName '"
                    + DIRECT_CODE_NAME
                    + "' and clearanceLevel '"
                    + DIRECT_CLEARANCE
                    + "' exactly."));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .metricsSatisfy(metrics -> assertThat(metrics.toolCalls()).isZero())
                .hasResponseJsonSatisfying(
                    json -> {
                      @SuppressWarnings("unchecked")
                      var map = (Map<String, Object>) json;
                      assertThat(String.valueOf(map.get("codeName")))
                          .isEqualToIgnoringCase(DIRECT_CODE_NAME);
                      assertThat(String.valueOf(map.get("clearanceLevel")))
                          .isEqualToIgnoringCase(DIRECT_CLEARANCE);
                    }));
  }
}
