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
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
 * export OPENAI_API_KEY=...                  # the OpenAI row
 * export GOOGLE_VERTEX_AI_PROJECT_ID=... GOOGLE_VERTEX_AI_REGION=... \
 *        GOOGLE_VERTEX_AI_SERVICE_ACCOUNT="$(cat sa-key.json)"   # the Vertex AI rows
 *
 * ./mvnw verify -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Pit-real-llm
 * }</pre>
 *
 * <p>Credentials are read straight from the environment — see {@link #EXTENSION} for why this suite
 * configures the runtime in Java rather than through a Spring profile.
 *
 * <p>The service account variable holds the whole key file verbatim. Each row skips itself when its
 * own credentials are absent, so a partial credential set runs a subset.
 *
 * <p>Narrow a run with {@code -Dit.test='AiAgentE2ETestIT#someScenario'} and {@link
 * ProviderRow#disabled()}. Requires {@code element-templates-cli} on the PATH at the version pinned
 * in {@code .github/workflows/package.json}.
 */
@EnabledIf("hasConfiguredProvider")
public class AiAgentE2ETestIT {

  private static final String BPMN_RESOURCE = "ai-agent-e2e.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String PROCESS_ID = "ai-agent-e2e";

  private static final String DEFAULT_CONNECTORS_IMAGE =
      "registry.camunda.cloud/team-connectors/connectors-bundle";

  private static final String HTTP_JSON_JOB_TYPE = "io.camunda:http-json:1";
  private static final String DATE_TIME_JOB_TYPE = "io.camunda.e2e:date-time:1";

  /** Vertex AI's non-regional endpoint, which is where the newest models land first. */
  private static final String GLOBAL_REGION = "global";

  /**
   * Fabricated name, so it cannot come from model training data — the model can only produce it by
   * relaying what the tool returned. A joke is exactly the content a model will happily supply from
   * its own knowledge instead of calling the tool, which a recognisable joke could not detect.
   */
  private static final String JOKE_NONCE = "Blorptastic-7";

  private static final String JOKE =
      "Why did the robot named "
          + JOKE_NONCE
          + " cross the road? To reticulate the splines on the other side.";

  /** Name of the second entry in {@link #knownUsers()}, which the lookup scenario asks for. */
  private static final String SECOND_USER_NAME = "Ervin Howell";

  private static final String ORDER_STATUS_TRACKING_NUMBER = "1Z999AA10123456784";

  private static final String SYSTEM_PROMPT =
      "You are a helpful chat agent which can answer a wide amount of questions based on your "
          + "knowledge and an optional set of available tools. If tools are provided, prefer them "
          + "instead of guessing an answer. Do not guess any tools which were not explicitly "
          + "configured.";

  private static final Duration USER_TASK_TIMEOUT = Duration.ofMinutes(3);

  /**
   * What {@code GetDateAndTime} reports. Fixed so scenarios can assert on it — a Saturday, so the
   * weekday is a token the model either echoes from the tool result or derives from the ISO date,
   * landing on the same value either way.
   */
  private static final ZonedDateTime DEFAULT_DATE_AND_TIME =
      ZonedDateTime.parse("2026-03-14T15:09:26+01:00[Europe/Berlin]");

  private static final String DEFAULT_DAY_OF_WEEK = dayOfWeek(DEFAULT_DATE_AND_TIME);

  /**
   * Registers the process-test runtime directly rather than through
   * {@code @CamundaSpringProcessTest}. A Spring context here would auto-configure a second
   * connector runtime inside the test JVM — this module has {@code connector-agentic-ai} and {@code
   * connector-http-json} on its compile classpath and the connectors starter on its test classpath
   * — whose workers would compete with the bundle container for the agent and tool jobs this suite
   * exists to route through that container. Without a Spring context, none of those
   * auto-configurations run.
   */
  @RegisterExtension
  static final CamundaProcessTestExtension EXTENSION =
      new CamundaProcessTestExtension()
          .withRuntimeMode(CamundaProcessTestRuntimeMode.SHARED)
          .withConnectorsEnabled(true)
          .withConnectorsDockerImageName(env("CONNECTORS_IMAGE_NAME", DEFAULT_CONNECTORS_IMAGE))
          .withConnectorsDockerImageVersion(env("CONNECTORS_IMAGE_VERSION", "SNAPSHOT"))
          // Keep the container's own HTTP connector off the tool job types, so the test's job mocks
          // are the only thing answering them.
          .withConnectorsEnv("CONNECTOR_OUTBOUND_DISABLED", HTTP_JSON_JOB_TYPE)
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

  /** Reassign before starting an instance to run a scenario against a different point in time. */
  private ZonedDateTime dateAndTime;

  /**
   * Tool elements the mock had no case for, asserted empty after every scenario. Completing such a
   * job with no {@code toolCallResult} would leave the model with an empty tool result, which it
   * covers by improvising — indistinguishable, from the response text alone, from a result that was
   * delivered and ignored.
   */
  private final List<String> unmockedToolElements = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void setUp() {
    setAssertionTimeout(USER_TASK_TIMEOUT);
  }

  @BeforeEach
  void mockTools() {
    dateAndTime = DEFAULT_DATE_AND_TIME;
    unmockedToolElements.clear();

    // GetDateAndTime runs on a job type no connector in the bundle implements, so the job is ours
    // by construction and the tool result is fixed rather than the real wall clock.
    processTestContext
        .mockJobWorker(DATE_TIME_JOB_TYPE)
        .withHandler(
            (jobClient, job) ->
                jobClient
                    .newCompleteCommand(job)
                    .variable("toolCallResult", dateAndTime())
                    .send()
                    .join());

    // Intercept ListUsers, Jokes_API and GetOrderStatus HTTP jobs — the HTTP connector is disabled
    // in the Docker bundle via CONNECTOR_OUTBOUND_DISABLED so these jobs stay open for the test to
    // complete. Matching is done by element id (a single job worker per job type, dispatching on
    // the element that raised the job) rather than by request content.
    processTestContext
        .mockJobWorker(HTTP_JSON_JOB_TYPE)
        .withHandler(
            (jobClient, job) -> {
              var result =
                  switch (job.getElementId()) {
                    case "ListUsers" -> knownUsers();
                    case "Jokes_API" -> JOKE;
                    case "GetOrderStatus" -> orderStatus();
                    default -> {
                      unmockedToolElements.add(job.getElementId());
                      yield "NO MOCK CONFIGURED FOR TOOL ELEMENT " + job.getElementId();
                    }
                  };
              jobClient.newCompleteCommand(job).variable("toolCallResult", result).send().join();
            });
  }

  @AfterEach
  void allToolCallsWereMocked() {
    assertThat(unmockedToolElements).as("tool elements without a configured mock").isEmpty();
  }

  // ---------------------------------------------------------------------------
  // Scenarios
  // ---------------------------------------------------------------------------

  /**
   * Names the tool outright, so this exercises tool <em>execution</em> — calling {@code ListUsers}
   * and using its result. Tool <em>selection</em> is covered by {@link
   * #shouldInferOrderStatusToolFromNaturalRequest(ProviderRow)}.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldCompleteWithUserLookupTool(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider,
            "Use your user lookup tool to list the available users and tell me the name of the"
                + " second user in the list. Reply with that name only.");

    completeUserTask(awaitUserTask(processInstance), true, null);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance).hasCompletedElement("ListUsers", 1);
    assertThat(responseText(processInstance)).contains(SECOND_USER_NAME);
  }

  /** Requests two tools in a single prompt, so the model has to emit both calls in one turn. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldCompleteWithMultipleToolCalls(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider,
            "I need two things: use your date and time tool to tell me which day of the week it is,"
                + " and also use your jokes API tool to fetch a random joke for me. Repeat the"
                + " joke exactly as the tool returns it.");

    completeUserTask(awaitUserTask(processInstance), true, null);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance).hasCompletedElement("GetDateAndTime", 1);
    assertThatProcessInstance(processInstance).hasCompletedElement("Jokes_API", 1);

    assertThat(responseText(processInstance)).contains(DEFAULT_DAY_OF_WEEK).contains(JOKE_NONCE);
  }

  /**
   * Forces at least two model-call turns, so any regression in conversation-history round-tripping
   * — tool calls, tool results, or a provider's own reasoning/thought metadata — surfaces here. The
   * {@code hasCompletedElement} assertion is what proves the second turn reused the retained result
   * rather than silently calling the tool again.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldRetainToolResultAcrossFeedbackLoop(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider, "Use your date and time tool to tell me the exact current date and time");

    var firstTaskKey = awaitUserTask(processInstance);
    completeUserTask(
        firstTaskKey,
        false,
        "Based on the date and time you just looked up, which day of the week was that? Reply with"
            + " the weekday only.");

    completeUserTask(awaitNextUserTask(processInstance, firstTaskKey), true, null);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance).hasCompletedElement("GetDateAndTime", 1);
    assertThat(responseText(processInstance)).contains(DEFAULT_DAY_OF_WEEK);
  }

  /**
   * A natural, customer-support-style request that does not name any tool: the model has to infer
   * on its own that {@code GetOrderStatus} is needed and to bind its {@code fromAi} order-ID
   * parameter, exercising tool <em>selection</em>.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("providers")
  void shouldInferOrderStatusToolFromNaturalRequest(ProviderRow provider) {
    var processInstance =
        deployAndStart(
            provider, "Hi, can you check the status of my order for me? The order ID is ORD-1001.");

    completeUserTask(awaitUserTask(processInstance), true, null);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance).hasCompletedElement("GetOrderStatus", 1);
    assertThat(responseText(processInstance))
        .containsIgnoringCase("shipped")
        .contains(ORDER_STATUS_TRACKING_NUMBER);
  }

  // ---------------------------------------------------------------------------
  // Provider rows
  // ---------------------------------------------------------------------------

  /**
   * Guards the class rather than the individual rows: with no credentials at all {@link
   * #providers()} is empty, and an empty {@code @MethodSource} is a JUnit configuration error
   * rather than a skip.
   */
  private static String env(String name, String defaultValue) {
    final var value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  static boolean hasConfiguredProvider() {
    return providers().findAny().isPresent();
  }

  static Stream<ProviderRow> providers() {
    return Stream.of(
            openAiV1("gpt-4o"),
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
  // Fixtures and helpers
  // ---------------------------------------------------------------------------

  /** The first five users the real {@code jsonplaceholder.typicode.com/users} endpoint returns. */
  private static List<Map<String, Object>> knownUsers() {
    return List.of(
        Map.of("id", 1, "name", "Leanne Graham", "username", "Bret"),
        Map.of("id", 2, "name", SECOND_USER_NAME, "username", "Antonette"),
        Map.of("id", 3, "name", "Clementine Bauch", "username", "Samantha"),
        Map.of("id", 4, "name", "Patricia Lebsack", "username", "Karianne"),
        Map.of("id", 5, "name", "Chelsey Dietrich", "username", "Kamren"));
  }

  private Map<String, Object> dateAndTime() {
    return Map.of(
        "iso", dateAndTime.toOffsetDateTime().toString(),
        "dayOfWeek", dayOfWeek(dateAndTime),
        "timeZone", dateAndTime.getZone().getId());
  }

  private static String dayOfWeek(ZonedDateTime dateTime) {
    return dateTime.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
  }

  private static Map<String, Object> orderStatus() {
    return Map.of(
        "orderId", "ORD-1001",
        "status", "shipped",
        "trackingNumber", ORDER_STATUS_TRACKING_NUMBER,
        "estimatedDelivery", "2026-08-10");
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

  /**
   * Reads {@code responseText} off the completed instance. The {@code hasVariableSatisfies} lambda
   * only captures — CamundaAssert treats it as a polling predicate, so an assertion raised inside
   * it would be retried for the full assertion timeout even though the instance is already
   * completed and the variable value can no longer change.
   */
  private String responseText(ProcessInstanceEvent instance) {
    var response = new AtomicReference<AgentResponse>();
    assertThatProcessInstance(instance)
        .hasVariableSatisfies("agent", AgentResponse.class, response::set);
    return response.get().responseText();
  }

  private long awaitUserTask(ProcessInstanceEvent instance) {
    assertThatProcessInstance(instance).hasActiveElements("User_Feedback");
    return userTaskKeys(instance).getFirst();
  }

  /**
   * Waits for the user task raised by the <em>next</em> agent turn, identified by key rather than
   * by element id: the follow-up loop re-enters the same {@code User_Feedback} element, so the key
   * is the only thing separating the new task from the one just completed.
   */
  private long awaitNextUserTask(ProcessInstanceEvent instance, long previousTaskKey) {
    await()
        .atMost(USER_TASK_TIMEOUT)
        .pollInterval(Duration.ofSeconds(5))
        .untilAsserted(() -> assertThat(userTaskKeysAfter(instance, previousTaskKey)).isNotEmpty());
    return userTaskKeysAfter(instance, previousTaskKey).getFirst();
  }

  private List<Long> userTaskKeysAfter(ProcessInstanceEvent instance, long previousTaskKey) {
    return userTaskKeys(instance).stream().filter(key -> key != previousTaskKey).toList();
  }

  private List<Long> userTaskKeys(ProcessInstanceEvent instance) {
    return camundaClient
        .newUserTaskSearchRequest()
        .filter(f -> f.processInstanceKey(instance.getProcessInstanceKey()))
        .send()
        .join()
        .items()
        .stream()
        .map(task -> task.getUserTaskKey())
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
