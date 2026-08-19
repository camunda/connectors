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

import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.e2e.BpmnFile;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.agenticai.BpmnUtil;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaProcessTestExtension;
import io.camunda.process.test.api.CamundaProcessTestRuntimeMode;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.File;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Real-LLM CPT coverage for the AI Agent sub-process, run against every configured provider.
 *
 * <p>Runs the agent and its tools inside the connectors bundle Docker image. To run locally, build
 * and tag that image, then point the test at it and supply the provider credentials:
 *
 * <pre>{@code
 * ./mvnw package -pl apps/bundle/default-bundle -DskipTests
 * cd apps/bundle/default-bundle && docker build -t camunda/connectors-bundle:local .
 *
 * export CONNECTORS_IMAGE_NAME=camunda/connectors-bundle CONNECTORS_IMAGE_VERSION=local
 * export OPENAI_API_KEY=...                  # the OpenAI rows
 * export GOOGLE_VERTEX_AI_PROJECT_ID=... GOOGLE_VERTEX_AI_REGION=... \
 *        GOOGLE_VERTEX_AI_SERVICE_ACCOUNT="$(cat sa-key.json)"   # the Vertex AI rows
 *
 * ./mvnw verify -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Pit-real-llm
 * }</pre>
 *
 * <p>The service account variable holds the whole key file verbatim. Each row skips itself when its
 * own credentials are absent, so a partial credential set runs a subset. Narrow a run further with
 * {@code -Dit.test='AiAgentE2ETestIT#someScenario'} and {@link ProviderRow#disabled()}.
 *
 * <p>Requires {@code element-templates-cli} on the PATH at the version pinned in {@code
 * .github/workflows/package.json}.
 */
@EnabledIf("hasConfiguredProvider")
public class AiAgentE2ETestIT {

  private static final String BPMN_RESOURCE = "ai-agent-e2e.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String PROCESS_ID = "ai-agent-e2e";

  private static final String DEFAULT_CONNECTORS_IMAGE =
      "registry.camunda.cloud/team-connectors/connectors-bundle";

  /** Vertex AI's non-regional endpoint, which is where the newest models land first. */
  private static final String GLOBAL_REGION = "global";

  /** Served by both OpenAI API families, and cheaper on a tool-calling loop than the gpt-5 line. */
  private static final String OPENAI_V2_MODEL = "gpt-4.1";

  private static final Duration USER_TASK_TIMEOUT = Duration.ofMinutes(3);

  private static final String SYSTEM_PROMPT =
      "You are a helpful chat agent which can answer a wide amount of questions based on your "
          + "knowledge and an optional set of available tools. If tools are provided, prefer them "
          + "instead of guessing an answer. Do not guess any tools which were not explicitly "
          + "configured.";

  @RegisterExtension
  static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withRuntimeMode(CamundaProcessTestRuntimeMode.SHARED)
          .withConnectorsEnabled(true)
          .withConnectorsDockerImageName(env("CONNECTORS_IMAGE_NAME", DEFAULT_CONNECTORS_IMAGE))
          .withConnectorsDockerImageVersion(env("CONNECTORS_IMAGE_VERSION", "SNAPSHOT"))
          // Container-side logging: the agent runs in the bundle, so its own log is the only place
          // a provider request, a tool result or a failed agent-instance history write shows up.
          .withConnectorsEnv("LOGGING_LEVEL_IO_CAMUNDA_CONNECTOR", "DEBUG")
          .withConnectorsEnv("LOGGING_LEVEL_IO_CAMUNDA_CONNECTOR_AGENTICAI", "TRACE")
          .withConnectorsSecret("OPENAI_API_KEY", env("OPENAI_API_KEY", ""))
          .withConnectorsSecret(
              "GOOGLE_VERTEX_AI_SERVICE_ACCOUNT", env("GOOGLE_VERTEX_AI_SERVICE_ACCOUNT", ""))
          .withConnectorsSecret(
              "GOOGLE_VERTEX_AI_PROJECT_ID", env("GOOGLE_VERTEX_AI_PROJECT_ID", ""))
          .withConnectorsSecret("GOOGLE_VERTEX_AI_REGION", env("GOOGLE_VERTEX_AI_REGION", ""));

  // Injected by the extension before each test.
  private CamundaClient camundaClient;
  private CamundaProcessTestContext processTestContext;

  @TempDir private File tempDir;

  @BeforeAll
  static void setUp() {
    setAssertionTimeout(USER_TASK_TIMEOUT);
  }

  /**
   * Every tool runs on its own job type, which no connector in the bundle implements, so these
   * mocks are the only thing that can answer a tool job and every tool result is fixed.
   */
  @BeforeEach
  void mockTools() {
    mockTool(DATE_TIME_JOB_TYPE, DATE_AND_TIME);
    mockTool(LIST_USERS_JOB_TYPE, KNOWN_USERS);
    mockTool(JOKE_JOB_TYPE, JOKE);
    mockTool(ORDER_STATUS_JOB_TYPE, ORDER_STATUS);
  }

  private void mockTool(String jobType, Object toolCallResult) {
    processTestContext
        .mockJobWorker(jobType)
        .withHandler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variable("toolCallResult", toolCallResult)
                    .send()
                    .join());
  }

  // ---------------------------------------------------------------------------
  // Scenarios
  // ---------------------------------------------------------------------------

  /** One tool call in a single round, with the tool named outright. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldCompleteWithToolCall(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider,
            """
            Use your user lookup tool to list the available users and tell me the name of the \
            second user in the list. Reply with that name only.""");

    completeUserTask(awaitUserTask(processInstance, USER_FEEDBACK), true, null);

    assertThatProcessInstance(processInstance).isCompleted().hasNoActiveIncidents();
    assertThatProcessInstance(processInstance).hasCompletedElement("ListUsers", 1);

    // one call to request the tool, one to answer from its result
    var response = assertAgentResponse(processInstance, 2);
    assertThat(response.responseText()).contains((String) KNOWN_USERS.get(1).get("name"));
  }

  /** Two tools requested at once, so both calls have to be emitted in the same round. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldCompleteWithMultipleToolCallsInOneRound(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider,
            """
            I need two things: use your date and time tool to tell me which day of the week it \
            is, and also use your joke tool to fetch a random joke for me. Repeat the joke \
            exactly as the tool returns it.""");

    completeUserTask(awaitUserTask(processInstance, USER_FEEDBACK), true, null);

    assertThatProcessInstance(processInstance).isCompleted().hasNoActiveIncidents();
    assertThatProcessInstance(processInstance).hasCompletedElement("GetDateAndTime", 1);
    assertThatProcessInstance(processInstance).hasCompletedElement("GetJoke", 1);

    // both tools are requested in the same call, so this stays at two
    var response = assertAgentResponse(processInstance, 2);
    assertThat(response.responseText()).contains(DAY_OF_WEEK).contains(JOKE_NONCE);
  }

  /**
   * Two tool calls the model cannot batch: the order ID it needs for the second is only known from
   * the result of the first. The model-call count is what proves the rounds were sequential — one
   * call to request the lookup, one to request the order status, one to answer.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldCompleteWithMultipleToolCallRounds(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider,
            """
            Look up the list of users, take the second user in that list, and then check the \
            status of the order that user has placed. Tell me the order status and the tracking \
            number.""");

    completeUserTask(awaitUserTask(processInstance, USER_FEEDBACK), true, null);

    assertThatProcessInstance(processInstance).isCompleted().hasNoActiveIncidents();
    assertThatProcessInstance(processInstance).hasCompletedElement("ListUsers", 1);
    assertThatProcessInstance(processInstance).hasCompletedElement("GetOrderStatus", 1);

    // one call per tool request plus one to answer: the rounds cannot have been batched
    var response = assertAgentResponse(processInstance, 3);
    assertThat(response.responseText())
        .containsIgnoringCase("shipped")
        .contains(ORDER_TRACKING_NUMBER);
  }

  /**
   * A user task as a tool — the human-in-the-loop pattern. The agent pauses inside the ad-hoc
   * sub-process until the task is completed, and the answer supplied here reaches its response.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldCompleteWithUserTaskTool(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider,
            """
            I need the internal code name of the current maintenance window. You do not know it, \
            so ask a human expert for it, then tell me their answer verbatim.""");

    camundaClient
        .newCompleteUserTaskCommand(awaitUserTask(processInstance, "AskHuman"))
        .variables(Map.of("humanAnswer", HUMAN_ANSWER))
        .send()
        .join();

    completeUserTask(awaitUserTask(processInstance, USER_FEEDBACK), true, null);

    assertThatProcessInstance(processInstance).isCompleted().hasNoActiveIncidents();
    assertThatProcessInstance(processInstance).hasCompletedElement("AskHuman", 1);

    // one call to ask the human, one to answer once they replied
    var response = assertAgentResponse(processInstance, 2);
    assertThat(response.responseText()).contains(HUMAN_ANSWER_NONCE);
  }

  /**
   * Leaves the ad-hoc sub-process and re-enters it with follow-up input, so any regression in
   * conversation-history round-tripping — tool calls, tool results, or a provider's own
   * reasoning/thought metadata — surfaces here. The {@code hasCompletedElement} count is what
   * proves the second round reused the retained result rather than silently calling the tool again.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldCompleteWithUserFeedbackLoop(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider, "Use your date and time tool to tell me the exact current date and time");

    completeUserTask(
        awaitUserTask(processInstance, USER_FEEDBACK),
        false,
        """
        Based on the date and time you just looked up, which day of the week was that? Reply with \
        the weekday only.""");

    completeUserTask(awaitUserTask(processInstance, USER_FEEDBACK), true, null);

    assertThatProcessInstance(processInstance).isCompleted().hasNoActiveIncidents();
    assertThatProcessInstance(processInstance).hasCompletedElement("GetDateAndTime", 1);

    // two calls for the first round, at least one more after re-entering with the follow-up
    var response = assertAgentResponse(processInstance, 3);
    assertThat(response.responseText()).contains(DAY_OF_WEEK);
  }

  // ---------------------------------------------------------------------------
  // Provider rows
  // ---------------------------------------------------------------------------

  /**
   * Guards the class rather than the individual rows: with no credentials at all {@link
   * #providers()} is empty, and an empty {@code @MethodSource} is a JUnit configuration error
   * rather than a skip.
   */
  static boolean hasConfiguredProvider() {
    return providers().findAny().isPresent();
  }

  static Stream<ProviderRow> providers() {
    return Stream.of(
            openAiV1("gpt-4o"),
            // both API families of the v2 provider: they build different wire requests and unwrap
            // tool calls and tool results differently, so each needs to run the scenarios
            openAiV2("responses", OPENAI_V2_MODEL),
            openAiV2("completions", OPENAI_V2_MODEL),
            googleVertexAiV1("gemini-2.5-flash"),
            // Gemini 3 models are served on the global endpoint, not the regional ones
            googleVertexAiV1("gemini-3.5-flash-lite", GLOBAL_REGION))
        .filter(ProviderRow::isEnabled);
  }

  /** OpenAI, v1. */
  static ProviderRow openAiV1(String model) {
    return new ProviderRow(
        "openai-v1/" + model,
        List.of("OPENAI_API_KEY"),
        AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type", "openai",
            "provider.openai.authentication.apiKey", "{{secrets.OPENAI_API_KEY}}",
            "provider.openai.model.model", model));
  }

  /**
   * OpenAI, v2, on the {@code openai-api} backend, for the given API family ({@code responses} or
   * {@code completions}).
   */
  static ProviderRow openAiV2(String apiFamily, String model) {
    return new ProviderRow(
        "openai-v2/" + apiFamily + "/" + model,
        List.of("OPENAI_API_KEY"),
        AI_AGENT_SUB_PROCESS_V2_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type", "openai",
            "provider.openai.backend.type", "openai-api",
            "provider.openai.backend.openai.apiKey", "{{secrets.OPENAI_API_KEY}}",
            "provider.openai.api.type", apiFamily,
            "provider.openai.model.model", model));
  }

  /** Google Vertex AI, v1, in the region {@code GOOGLE_VERTEX_AI_REGION} names. */
  static ProviderRow googleVertexAiV1(String model) {
    return googleVertexAiV1(
        model, model, "{{secrets.GOOGLE_VERTEX_AI_REGION}}", List.of("GOOGLE_VERTEX_AI_REGION"));
  }

  /**
   * Google Vertex AI, v1, pinned to {@code region} — for models the configured region does not
   * serve. A pinned region needs no {@code GOOGLE_VERTEX_AI_REGION} to be set.
   */
  static ProviderRow googleVertexAiV1(String model, String region) {
    return googleVertexAiV1(model + "@" + region, model, region, List.of());
  }

  private static ProviderRow googleVertexAiV1(
      String id, String model, String region, List<String> regionEnvVars) {
    return new ProviderRow(
        "google-vertex-ai-v1/" + id,
        Stream.concat(
                Stream.of("GOOGLE_VERTEX_AI_SERVICE_ACCOUNT", "GOOGLE_VERTEX_AI_PROJECT_ID"),
                regionEnvVars.stream())
            .toList(),
        AI_AGENT_SUB_PROCESS_V1_ELEMENT_TEMPLATE_PATH,
        Map.of(
            "provider.type",
            "google-vertex-ai",
            "provider.googleVertexAi.projectId",
            "{{secrets.GOOGLE_VERTEX_AI_PROJECT_ID}}",
            "provider.googleVertexAi.region",
            region,
            "provider.googleVertexAi.authentication.type",
            "serviceAccountCredentials",
            "provider.googleVertexAi.authentication.jsonKey",
            "{{secrets.GOOGLE_VERTEX_AI_SERVICE_ACCOUNT}}",
            "provider.googleVertexAi.model.model",
            model));
  }

  record ProviderRow(
      String id,
      List<String> requiredEnvVars,
      boolean enabled,
      String elementTemplatePath,
      Map<String, String> properties) {

    ProviderRow(
        String id,
        List<String> requiredEnvVars,
        String elementTemplatePath,
        Map<String, String> properties) {
      this(id, requiredEnvVars, true, elementTemplatePath, properties);
    }

    ProviderRow disabled() {
      return new ProviderRow(id, requiredEnvVars, false, elementTemplatePath, properties);
    }

    boolean isEnabled() {
      return enabled && requiredEnvVars.stream().allMatch(v -> System.getenv(v) != null);
    }

    @Override
    public String toString() {
      return id;
    }
  }

  // ---------------------------------------------------------------------------
  // Tool fixtures
  // ---------------------------------------------------------------------------

  private static final String USER_FEEDBACK = "User_Feedback";

  private static final String DATE_TIME_JOB_TYPE = "io.camunda.e2e:date-time:1";
  private static final String LIST_USERS_JOB_TYPE = "io.camunda.e2e:list-users:1";
  private static final String JOKE_JOB_TYPE = "io.camunda.e2e:joke:1";
  private static final String ORDER_STATUS_JOB_TYPE = "io.camunda.e2e:order-status:1";

  /**
   * Fixture values a model cannot produce from its own knowledge, so an answer containing one can
   * only have come from a tool result. A joke or a plausible user name would not do: those the
   * model will happily supply itself, which is exactly how tool results that never arrived went
   * unnoticed.
   */
  private static final String JOKE_NONCE = "Blorptastic-7";

  private static final String HUMAN_ANSWER_NONCE = "Quibbleton-4";

  private static final String JOKE =
      "Why did the robot named "
          + JOKE_NONCE
          + " cross the road? To reticulate the splines on the other side.";

  private static final String HUMAN_ANSWER =
      "The current maintenance window code name is " + HUMAN_ANSWER_NONCE + ".";

  private static final String ORDER_TRACKING_NUMBER = "1Z999AA10123456784";

  private static final List<Map<String, Object>> KNOWN_USERS =
      List.of(
          Map.of("id", 1, "name", "Leanne Marchetti", "orderId", "ORD-1000"),
          Map.of("id", 2, "name", "Ervin Quibbleton", "orderId", "ORD-1001"),
          Map.of("id", 3, "name", "Clementine Vosk", "orderId", "ORD-1002"),
          Map.of("id", 4, "name", "Patricia Bramblewood", "orderId", "ORD-1003"),
          Map.of("id", 5, "name", "Chelsey Dunmoor", "orderId", "ORD-1004"));

  private static final Map<String, Object> ORDER_STATUS =
      Map.of(
          "orderId", "ORD-1001",
          "status", "shipped",
          "trackingNumber", ORDER_TRACKING_NUMBER,
          "estimatedDelivery", "2026-08-10");

  /** A Saturday, so the weekday holds whether the model echoes it or derives it from the date. */
  private static final ZonedDateTime FIXED_DATE_AND_TIME =
      ZonedDateTime.parse("2026-03-14T15:09:26+01:00[Europe/Berlin]");

  private static final String DAY_OF_WEEK =
      FIXED_DATE_AND_TIME.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

  private static final Map<String, Object> DATE_AND_TIME =
      Map.of(
          "iso", FIXED_DATE_AND_TIME.toOffsetDateTime().toString(),
          "dayOfWeek", DAY_OF_WEEK,
          "timeZone", FIXED_DATE_AND_TIME.getZone().getId());

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static String env(String name, String defaultValue) {
    final var value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private ProcessInstanceEvent deployAndStart(ProviderRow provider, String inputText) {
    camundaClient
        .newDeployResourceCommand()
        .addProcessModel(buildModel(provider), PROCESS_ID + ".bpmn")
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();

    return camundaClient
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(Map.of("inputText", inputText))
        .send()
        .join();
  }

  private BpmnModelInstance buildModel(ProviderRow provider) {
    var template =
        ElementTemplate.from(provider.elementTemplatePath())
            .property("agentContext", "=agent.context")
            .property("data.systemPrompt.prompt", "=\"" + SYSTEM_PROMPT + "\"")
            .property(
                "data.userPrompt.prompt",
                "=if (is defined(followUpInput)) then followUpInput else inputText")
            .property("data.userPrompt.documents", "=[]")
            .property("data.memory.storage.type", "in-process")
            .property("data.memory.contextWindowSize", "=20")
            .property("data.limits.maxModelCalls", "=20")
            .property("data.response.includeAssistantMessage", "=false")
            .property("data.response.includeAgentContext", "=true")
            // the template default is PT30S, which would add minutes to a retried real-LLM run
            .property("retryBackoff", "PT0S");

    provider.properties().forEach(template::property);

    try {
      var templateFile = template.writeTo(new File(tempDir, "template.json"));
      var bpmnFile =
          new File(AiAgentE2ETestIT.class.getClassLoader().getResource(BPMN_RESOURCE).toURI());
      var model =
          new BpmnFile(bpmnFile).apply(templateFile, "AI_Agent", new File(tempDir, "applied.bpmn"));
      return BpmnUtil.withAgentDefinitionMarker(model, "AI_Agent", "aiAgentSubProcess");
    } catch (Exception e) {
      throw new RuntimeException("Failed to build BPMN model for " + provider.id(), e);
    }
  }

  /** Reads the agent response and asserts the number of model calls it took to produce it. */
  private AgentResponse assertAgentResponse(ProcessInstanceEvent instance, int minModelCalls) {
    var captured = new AtomicReference<AgentResponse>();
    // The lambda only captures: CamundaAssert treats it as a polling predicate, so an assertion
    // raised inside it would be retried for the full assertion timeout even though the instance is
    // already completed and the variable value can no longer change.
    assertThatProcessInstance(instance)
        .hasVariableSatisfies("agent", AgentResponse.class, captured::set);

    var response = captured.get();
    assertThat(response.context().metrics().modelCalls())
        .as("model calls")
        .isGreaterThanOrEqualTo(minModelCalls);
    return response;
  }

  /** Waits for a created user task on {@code elementId} — the feedback loop re-enters its own. */
  private long awaitUserTask(ProcessInstanceEvent instance, String elementId) {
    var keys = new AtomicReference<List<Long>>(List.of());
    await()
        .atMost(USER_TASK_TIMEOUT)
        .pollInterval(Duration.ofSeconds(2))
        .untilAsserted(
            () -> {
              keys.set(createdUserTaskKeys(instance, elementId));
              assertThat(keys.get()).as("created user task on %s", elementId).isNotEmpty();
            });
    return keys.get().getFirst();
  }

  private List<Long> createdUserTaskKeys(ProcessInstanceEvent instance, String elementId) {
    return camundaClient
        .newUserTaskSearchRequest()
        .filter(
            f ->
                f.processInstanceKey(instance.getProcessInstanceKey())
                    .elementId(elementId)
                    .state(UserTaskState.CREATED))
        .send()
        .join()
        .items()
        .stream()
        .map(UserTask::getUserTaskKey)
        .toList();
  }

  private void completeUserTask(long taskKey, boolean satisfied, String followUpInput) {
    var variables =
        followUpInput == null
            ? Map.<String, Object>of("userSatisfied", satisfied)
            : Map.<String, Object>of("userSatisfied", satisfied, "followUpInput", followUpInput);

    camundaClient.newCompleteUserTaskCommand(taskKey).variables(variables).send().join();
  }
}
