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
package io.camunda.connector.e2e.agenticai.aiagent;

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.process.test.api.CamundaProcessTestContext;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Manual verification of the Google Vertex AI provider against a <b>real</b> Vertex AI endpoint.
 * {@code region = global} needs its own hostname special case; {@link #supportedTargets()} pairs
 * the same model with a regional and a global target so a failure can only be the hostname, not
 * model availability.
 *
 * <p>Excluded from CI: needs real credentials, costs money, depends on model availability.
 */
@Disabled(
"""
Requires a real Google Cloud project with the Vertex AI API enabled.
Export GOOGLE_VERTEX_PROJECT_ID + GOOGLE_VERTEX_SERVICE_ACCOUNT_JSON + GOOGLE_APPLICATION_CREDENTIALS before running
""")
public class GoogleVertexAiRealModelManualTests extends BaseAiAgentConnectorTest {

  private static final String ENV_PROJECT_ID = "GOOGLE_VERTEX_PROJECT_ID";
  private static final String ENV_SERVICE_ACCOUNT_JSON = "GOOGLE_VERTEX_SERVICE_ACCOUNT_JSON";
  private static final String ENV_REGION = "GOOGLE_VERTEX_REGION";
  private static final String ENV_MODEL = "GOOGLE_VERTEX_MODEL";
  private static final String ENV_NEW_MODEL = "GOOGLE_VERTEX_NEW_MODEL";

  private static final String DEFAULT_REGION = "us-central1";
  private static final String DEFAULT_MODEL = "gemini-2.5-flash";
  private static final String DEFAULT_NEW_MODEL = "gemini-3.5-flash-lite";

  private static final String GLOBAL_REGION = "global";

  private static final String PING_PROMPT = "Reply with the single word PONG and nothing else.";
  private static final String TOOL_PROMPT =
      "Calculate the superflux product of 7 and 5 using the available tool, then tell me the result.";

  @Autowired private CamundaProcessTestContext processTestContext;

  /** A region/model pair that is expected to work. */
  private record VertexTarget(String region, String model) {
    @Override
    public String toString() {
      return "%s / %s".formatted(region, model);
    }
  }

  private enum AuthMode {
    SERVICE_ACCOUNT_CREDENTIALS("serviceAccountCredentials"),
    APPLICATION_DEFAULT_CREDENTIALS("applicationDefaultCredentials");

    private final String templateValue;

    AuthMode(String templateValue) {
      this.templateValue = templateValue;
    }
  }

  static Stream<VertexTarget> supportedTargets() {
    return Stream.of(
        // same model as the next case, regional - the control for the global comparison
        new VertexTarget(region(), model()),
        //        new VertexTarget(region(), newModel()),
        // same model, global region - the hostname case that needs special handling
        new VertexTarget(GLOBAL_REGION, model()),
        // the originally reported scenario: a model offered only in the global region
        new VertexTarget(GLOBAL_REGION, newModel()));
  }

  /**
   * Disables the inherited {@code @BeforeEach} user feedback worker (no annotation here overrides
   * it) - it would race {@link #completeUserFeedbackAsSatisfied()} and complete {@code
   * user_feedback} with an empty variable map.
   */
  @Override
  void openUserFeedbackJobWorker() {
    // intentionally empty - feedback is driven by the CPT conditional behavior API instead
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("supportedTargets")
  void respondsToPrompt(VertexTarget target) throws Exception {
    final var zeebeTest = runPrompt(target, AuthMode.SERVICE_ACCOUNT_CREDENTIALS, PING_PROMPT);

    assertPongResponse(zeebeTest);
  }

  /**
   * Same matrix, but with no credentials passed - the SDK resolves them via {@code
   * GoogleCredentials.getApplicationDefault()} (reads {@code GOOGLE_APPLICATION_CREDENTIALS}).
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("supportedTargets")
  void respondsToPromptWithApplicationDefaultCredentials(VertexTarget target) throws Exception {
    final var zeebeTest = runPrompt(target, AuthMode.APPLICATION_DEFAULT_CREDENTIALS, PING_PROMPT);

    assertPongResponse(zeebeTest);
  }

  /**
   * Exercises {@code GoogleGenAiContentMapper}'s tool call/schema conversion. Tools in {@code
   * agentic-ai-connectors.bpmn} are FEEL script tasks, so they self-complete without a job worker.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("supportedTargets")
  void respondsToPromptWithToolCalling(VertexTarget target) throws Exception {
    final var zeebeTest = runPrompt(target, AuthMode.SERVICE_ACCOUNT_CREDENTIALS, TOOL_PROMPT);

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasResponseTestSatisfying(responseText -> assertThat(responseText).contains("36"))
                .satisfies(
                    response -> {
                      final var conversation =
                          (InProcessConversationContext) response.context().conversation();
                      final var toolCallsMade =
                          conversation.messages().stream()
                              .filter(AssistantMessage.class::isInstance)
                              .map(AssistantMessage.class::cast)
                              .mapToInt(message -> message.toolCalls().size())
                              .sum();

                      // exactly one tool call proves the round trip happened without retries
                      assertThat(toolCallsMade).isEqualTo(1);
                      assertThat(response.context().metrics().modelCalls()).isGreaterThan(1);
                    }));
  }

  private ZeebeTest runPrompt(VertexTarget target, AuthMode authMode, String userPrompt)
      throws Exception {
    final var zeebeTest = startPrompt(target, authMode, userPrompt);
    zeebeTest.waitForProcessCompletion();

    return zeebeTest;
  }

  private ZeebeTest startPrompt(VertexTarget target, AuthMode authMode, String userPrompt)
      throws Exception {
    completeUserFeedbackAsSatisfied();

    return createProcessInstance(provider(target, authMode), Map.of("userPrompt", userPrompt));
  }

  private void assertPongResponse(ZeebeTest zeebeTest) {
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasResponseTestSatisfying(
                    responseText -> assertThat(responseText).containsIgnoringCase("pong")));
  }

  /**
   * Auto-approves the agent's answer once {@code User_Feedback} becomes active. Must be called
   * before the process instance is created. It's a service task (job type {@code user_feedback}),
   * not a user task, hence {@code completeJob}.
   */
  private void completeUserFeedbackAsSatisfied() {
    processTestContext
        .when(
            () ->
                assertThatProcessInstance(byProcessId("Agentic_AI_Connectors"))
                    .hasActiveElements("User_Feedback"))
        .as("approve agent response")
        .then(() -> processTestContext.completeJob("user_feedback", userSatisfiedFeedback()));
  }

  private Function<ElementTemplate, ElementTemplate> provider(
      VertexTarget target, AuthMode authMode) {
    return elementTemplate -> {
      elementTemplate
          // the shared fixtures default to OpenAI - drop those so only Vertex AI is configured
          .withoutPropertyValueStartingWith("provider.openai.")
          .property("provider.type", "google-vertex-ai")
          .property("provider.googleVertexAi.projectId", requiredEnv(ENV_PROJECT_ID))
          .property("provider.googleVertexAi.region", target.region())
          .property("provider.googleVertexAi.model.model", target.model())
          .property("provider.googleVertexAi.authentication.type", authMode.templateValue);

      // jsonKey has no "value" key to clear for ADC - withoutPropertyValue would throw
      // PathNotFoundException, so only set it for the service account mode.
      if (authMode == AuthMode.SERVICE_ACCOUNT_CREDENTIALS) {
        elementTemplate.property(
            "provider.googleVertexAi.authentication.jsonKey",
            requiredEnv(ENV_SERVICE_ACCOUNT_JSON));
      }

      return elementTemplate;
    };
  }

  private static String region() {
    return optionalEnv(ENV_REGION, DEFAULT_REGION);
  }

  private static String model() {
    return optionalEnv(ENV_MODEL, DEFAULT_MODEL);
  }

  private static String newModel() {
    return optionalEnv(ENV_NEW_MODEL, DEFAULT_NEW_MODEL);
  }

  private static String optionalEnv(String name, String defaultValue) {
    return StringUtils.defaultIfBlank(System.getenv(name), defaultValue);
  }

  private static String requiredEnv(String name) {
    final var value = System.getenv(name);
    if (StringUtils.isBlank(value)) {
      throw new IllegalStateException(
          "Environment variable %s is required to run this manual test. See the @Disabled reason on %s for setup instructions."
              .formatted(name, GoogleVertexAiRealModelManualTests.class.getSimpleName()));
    }
    return value;
  }
}
