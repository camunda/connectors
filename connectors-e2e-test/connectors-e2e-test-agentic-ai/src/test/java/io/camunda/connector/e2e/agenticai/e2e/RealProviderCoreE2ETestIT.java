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
import java.util.Objects;
import java.util.regex.Pattern;
import org.assertj.core.api.Assertions;
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
@Tag(RealProviderCapabilityTags.CORE)
class RealProviderCoreE2ETestIT extends RealProviderApiSmokeSupport {

  private static final Pattern MEMORY_TOKEN =
      Pattern.compile("(?i)MEMORY TOKEN:\\s*([a-z]{8,})\\b");

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providers")
  void toolCallLoopSurfacesPlantedFact(ProviderConfig provider) {
    var model =
        buildModel(
            provider, AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH, BPMN_RESOURCE, template -> {});

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            DEFAULT_SYSTEM_PROMPT,
            Map.of("userPrompt", "What is the internal project code name? Use your lookup tool."));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .hasResponseTextSatisfying(
                    text ->
                        Assertions.assertThat(normalizeDashes(text)).contains(NONCE_CODE_NAME)));
  }

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providers")
  void userFeedbackLoopReplaysAssistantTextOnFollowUp(ProviderConfig provider) {
    final var initialPrompt =
        "What is the internal project code name? Use your lookup tool. In your final answer, invent "
            + "a nonsense lowercase alphabetic memory token of at least 8 letters that does not occur in "
            + "the tool result, and put it on its own final line as 'MEMORY TOKEN: <token>'.";
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            template ->
                template.property(
                    "data.userPrompt.prompt",
                    "=if (is defined(followUpInput)) then followUpInput else userPrompt"));

    var instance =
        startAgent(model, PROCESS_ID, DEFAULT_SYSTEM_PROMPT, Map.of("userPrompt", initialPrompt));

    // Capture evidence that exists only in turn 1's completed assistant text. It is deliberately
    // neither a planted tool fact nor supplied by either user prompt.
    final var firstResponse = readAgentResponseAtUserFeedback(instance);
    final var firstResponseText =
        Objects.requireNonNull(firstResponse.responseText(), "Turn 1 response text");
    final var memoryToken = extractMemoryToken(firstResponseText);
    Assertions.assertThat(PLANTED_SECRET).doesNotContainIgnoringCase(memoryToken);
    Assertions.assertThat(initialPrompt).doesNotContainIgnoringCase(memoryToken);
    Assertions.assertThat(DEFAULT_SYSTEM_PROMPT).doesNotContainIgnoringCase(memoryToken);

    completeUserFeedback(
        instance,
        Map.of(
            "userSatisfied",
            false,
            "followUpInput",
            "What memory token did you write in your immediately preceding answer? Reply with the "
                + "token only. Do not call a tool."));

    completeUserFeedback(instance, Map.of("userSatisfied", true));

    assertAgentResponse(
        instance,
        response ->
            AgentSubProcessResponseAssert.assertThat(response)
                .isReady()
                .hasResponseTextSatisfying(
                    text ->
                        Assertions.assertThat(normalizeShortAnswer(text))
                            .isEqualToIgnoringCase(memoryToken)));
  }

  static String extractMemoryToken(String responseText) {
    final var matcher = MEMORY_TOKEN.matcher(responseText);
    Assertions.assertThat(matcher.find())
        .as("turn 1 response contains an invented memory token")
        .isTrue();
    return matcher.group(1);
  }
}
