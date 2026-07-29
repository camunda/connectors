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

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.AI_AGENT_TASK_ID;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.assertions.ProcessInstanceSelectors.byProcessId;
import static org.assertj.core.api.Assertions.assertThat;

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
 *
 * <p>These tests are the acceptance criterion for the migration from the sunset {@code
 * langchain4j-vertex-ai-gemini} / {@code google-cloud-vertexai} stack to {@code
 * langchain4j-google-genai} / {@code com.google.genai:google-genai}.
 *
 * <p>The bug being fixed: {@code region = global} produces the hostname {@code
 * global-aiplatform.googleapis.com} and therefore a 404, because Google's {@code VertexAI.java}
 * builds {@code "%s-aiplatform.googleapis.com".formatted(location)} with no special case for {@code
 * global}. The replacement SDK special-cases it in {@code com.google.genai.ApiClient}.
 *
 * <p>The bug is <b>model-independent</b> - the hostname is built before any model name is involved
 * - so every {@code global} target fails on the old stack regardless of model. That is why {@link
 * #supportedTargets()} pairs the same model with both a regional and the global region: those two
 * cases differ <em>only</em> in region, so a difference between them can only be the hostname and
 * never model availability.
 *
 * <p><b>Verified baseline on the pre-migration stack:</b> the regional target passes; every {@code
 * global} target fails with a 404 on {@code /google.cloud.aiplatform.v1.PredictionService/
 * GenerateContent}. All targets must pass after the migration.
 *
 * <p>These tests are deliberately excluded from CI: they need real credentials, cost money, and
 * depend on model availability outside our control.
 */
@Disabled(
    """
    Manual test. Requires a real Google Cloud project with the Vertex AI API enabled.

    Export before running:
      export GOOGLE_VERTEX_PROJECT_ID='<dev project id>'
      export GOOGLE_VERTEX_SERVICE_ACCOUNT_JSON="$(cat /path/to/key.json)"   # full JSON, not a path

    For the applicationDefaultCredentials cases, additionally point the standard ADC variable at
    the SAME key file. GoogleCredentials.getApplicationDefault() checks this variable first
    (DefaultCredentialsProvider:135), so this exercises the real ADC resolution path with the same
    identity - no `gcloud auth application-default login` needed, and no user-credential
    quota-project noise:
      export GOOGLE_APPLICATION_CREDENTIALS='/path/to/key.json'                # a path, not JSON

    Optional overrides:
      export GOOGLE_VERTEX_REGION='us-central1'                 # default us-central1
      export GOOGLE_VERTEX_MODEL='gemini-2.5-flash'             # regionally available, default gemini-2.5-flash
      export GOOGLE_VERTEX_NEW_MODEL='gemini-3.5-flash-lite'    # global-only, default gemini-3.5-flash-lite

    Then run (Docker must be running - CPT starts a runtime container):
      mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai \\
        -Dtest=GoogleVertexAiRealModelManualTests \\
        -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'

    Run a single group by appending a method name, e.g. -Dtest='GoogleVertexAiRealModelManualTests#respondsToPrompt*'

    The service account needs roles/aiplatform.user on the project. A NOT_FOUND means a
    model-access problem, not the hostname bug - distinguish the two before drawing conclusions.
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

  /** A region/model pair that is expected to work once the migration is in place. */
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

  /**
   * The region/model combinations that must all succeed after the migration.
   *
   * <p>Resolved from environment variables with defaults. Only {@code optionalEnv} is used here, so
   * test discovery never fails when credentials are absent.
   */
  static Stream<VertexTarget> supportedTargets() {
    return Stream.of(
        // same model as the next case, regional - the control for the global comparison
        new VertexTarget(region(), model()),
        // same model, global region - fails with a 404 before the migration
        new VertexTarget(GLOBAL_REGION, model()),
        // the originally reported scenario: a model offered only in the global region
        new VertexTarget(GLOBAL_REGION, newModel()));
  }

  /**
   * The fourth cell of the region x model matrix, which is expected to fail <b>both before and
   * after</b> the migration: the newer model is only offered in the {@code global} region, so a
   * regional request for it is rejected by Vertex AI with {@code NOT_FOUND}.
   *
   * <p>Kept as an asserted failure rather than omitted, because the distinction between the two
   * failure modes is the whole point of this suite. {@code NOT_FOUND} means model availability;
   * {@code 404} on {@code /google.cloud.aiplatform.v1.PredictionService/GenerateContent} means the
   * hostname bug. Virgile's original report conflated them. Asserting this case guards against a
   * future change that makes model-availability errors surface as something else - or that masks
   * them entirely.
   */
  static Stream<VertexTarget> targetsUnavailableInRegion() {
    return Stream.of(new VertexTarget(region(), newModel()));
  }

  /**
   * Overrides - and thereby disables - the inherited {@code @BeforeEach} user feedback job worker
   * from {@link BaseAiAgentTest}. JUnit does not execute lifecycle methods that a subclass
   * overrides, and this override intentionally carries no {@code @BeforeEach} annotation.
   *
   * <p>Without this, the inherited worker would race the CPT conditional behavior registered in
   * {@link #completeUserFeedbackAsSatisfied()} and complete {@code user_feedback} with an empty
   * variable map, leaving {@code userSatisfied} undefined and failing the following gateway.
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
   * Same matrix over the ADC path, where the connector passes no credentials at all and the SDK
   * infers Vertex AI from project + location alone, resolving credentials via {@code
   * GoogleCredentials.getApplicationDefault()}. Reads {@code GOOGLE_APPLICATION_CREDENTIALS}, not
   * {@code GOOGLE_VERTEX_SERVICE_ACCOUNT_JSON}.
   *
   * <p>This is the mode labelled "Hybrid/Self-Managed only" in the element template, and the one
   * most likely to break silently: the migration changes credential resolution from lazy (old
   * {@code VertexAI}) to eager (google-genai's {@code ApiClient} constructor).
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("supportedTargets")
  void respondsToPromptWithApplicationDefaultCredentials(VertexTarget target) throws Exception {
    final var zeebeTest = runPrompt(target, AuthMode.APPLICATION_DEFAULT_CREDENTIALS, PING_PROMPT);

    assertPongResponse(zeebeTest);
  }

  /**
   * Tool calling exercises {@code GoogleGenAiContentMapper} and the tool schema conversion, which
   * differ from the old adapter's mapper. The tools in {@code agentic-ai-connectors.bpmn} are FEEL
   * script tasks, so they self-complete without a job worker.
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
                    response ->
                        // more than one model call proves a tool round trip actually happened
                        assertThat(response.context().metrics().modelCalls()).isGreaterThan(1)));
  }

  /**
   * The fourth matrix cell: a model that exists only in the {@code global} region, requested
   * regionally. Must fail with {@code NOT_FOUND} - the model-availability failure mode - and must
   * <b>not</b> be confused with the hostname 404. See {@link #targetsUnavailableInRegion()}.
   */
  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("targetsUnavailableInRegion")
  void failsWithNotFoundWhenModelIsUnavailableInRegion(VertexTarget target) throws Exception {
    final var zeebeTest =
        startPrompt(target, AuthMode.SERVICE_ACCOUNT_CREDENTIALS, PING_PROMPT)
            .waitForActiveIncidents();

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
          assertThat(incident.getErrorMessage())
              .as("model unavailability must surface as NOT_FOUND, not as a hostname 404")
              .contains("NOT_FOUND")
              .doesNotContain("was not found on this server");
        });

    assertThat(userFeedbackJobWorkerCounter.get())
        .as("user feedback must not be reached when the model call fails")
        .isZero();
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
   * Registers a background behavior that approves the agent's answer as soon as the {@code
   * User_Feedback} task becomes active, so the feedback loop terminates after a single iteration.
   * Must be called before the process instance is created.
   *
   * <p>{@code User_Feedback} is a service task with job type {@code user_feedback}, not a user
   * task, hence {@code completeJob} rather than {@code completeUserTask}.
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
          .property("provider.googleVertexAi.authentication.type", authMode.templateValue)
          // needed so AgentResponse.responseText() is populated
          .property("data.response.format.type", "text");

      // jsonKey is only set - and only bound by element-templates-cli - for the service account
      // mode. Do not try to clear it for ADC: the property carries no "value" key to delete, so
      // withoutPropertyValue would throw PathNotFoundException.
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
