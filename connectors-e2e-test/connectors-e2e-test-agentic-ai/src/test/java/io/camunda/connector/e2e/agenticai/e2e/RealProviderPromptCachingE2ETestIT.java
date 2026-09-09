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
@Tag(RealProviderCapabilityTags.PROMPT_CACHING)
class RealProviderPromptCachingE2ETestIT extends RealProviderApiSmokeSupport {

  @ParameterizedTest(name = "{0}", allowZeroInvocations = true)
  @MethodSource("providersWithPromptCaching")
  void promptCachingReportsCacheReadAndWriteTokens(ProviderConfig provider) {
    var model =
        buildModel(
            provider,
            AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
            BPMN_RESOURCE,
            DEFAULT_SYSTEM_PROMPT,
            template -> {
              provider.propertiesFor(Capability.PROMPT_CACHING).forEach(template::property);
              template.property("data.systemPrompt.prompt", "=longSystemPrompt");
            });

    var instance =
        startAgent(
            model,
            PROCESS_ID,
            DEFAULT_SYSTEM_PROMPT,
            Map.of(
                "userPrompt",
                "What is the internal project code name? Use your lookup tool.",
                "longSystemPrompt",
                LONG_SYSTEM_PROMPT));
    completeUserFeedback(instance, Map.of("userSatisfied", true));

    // The tool call forces a second model call: turn 1 writes the cache, turn 2 reads it.
    assertAgentResponse(
        instance,
        response -> {
          var agentAssert = AgentSubProcessResponseAssert.assertThat(response).isReady();
          if (provider.reportsCacheCreationTokens()) {
            agentAssert.metricsSatisfy(
                metrics ->
                    Assertions.assertThat(metrics.tokenUsage().cacheCreationTokenCount())
                        .as("cache creation token count")
                        .isPositive());
          }
          agentAssert
              .metricsSatisfy(
                  metrics ->
                      Assertions.assertThat(metrics.tokenUsage().cacheReadTokenCount())
                          .as("cache read token count")
                          .isPositive())
              .hasResponseTextSatisfying(
                  text -> Assertions.assertThat(normalizeDashes(text)).contains(NONCE_CODE_NAME));
        });
  }

}
