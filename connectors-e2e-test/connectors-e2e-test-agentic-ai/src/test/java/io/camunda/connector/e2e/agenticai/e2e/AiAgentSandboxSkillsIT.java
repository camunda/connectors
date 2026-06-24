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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.connector.e2e.agenticai.CamundaDocumentTestConfiguration;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import io.daytona.sdk.Daytona;
import io.daytona.sdk.DaytonaConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Live, manually-run viability test for the AI Agent <b>sandbox + skills</b> feature set.
 *
 * <p>It runs the real example chat process ({@code agentic-ai-sandbox-chat.bpmn}, a near-verbatim
 * copy of {@code
 * connectors/agentic-ai/examples/ai-agent/ad-hoc-sub-process/ai-agent-chat-with-tools/ai-agent-chat-with-tools.bpmn}
 * — the only change is that the skill-download URL is injected via the {@code skillDownloadUrl}
 * process variable so it can point at this test's embedded WireMock server) against a <b>real
 * Daytona sandbox</b> and a <b>real LLM</b> (AWS Bedrock, as configured in the BPMN). Outcomes are
 * asserted with the {@code hasVariableSatisfiesJudge} LLM judge because real-LLM output is
 * non-deterministic.
 *
 * <h2>What it exercises</h2>
 *
 * <ul>
 *   <li><b>A — sandbox basics</b>: bash compute + OS inspection inside the sandbox.
 *   <li><b>B — export OUT</b>: produce a file in the sandbox and hand it back via {@code
 *       sandbox_export_document}.
 *   <li><b>C — import IN</b>: a document produced by a tool call ({@code Fetch_URL}) is registered
 *       and pulled into the sandbox via {@code sandbox_import_document}.
 *   <li><b>D — skill round-trip</b>: load the {@code sandbox-doc-tools} skill, then
 *       fetch→import→inspect→summarize a document.
 *   <li><b>E — skill catalog</b>: the agent advertises the available skill and describes what it
 *       enables.
 * </ul>
 *
 * <h2>How to run</h2>
 *
 * <p>This test is SKIPPED unless {@code DAYTONA_API_KEY} <em>and</em> {@code
 * AWS_BEDROCK_ACCESS_KEY} are set. It needs no local skill server — the {@code sandbox-doc-tools}
 * skill is zipped on the fly from {@code src/test/resources/skills/sandbox-doc-tools/} and served
 * by WireMock.
 *
 * <pre>
 *   DAYTONA_API_KEY=... \
 *   AWS_BEDROCK_ACCESS_KEY=... AWS_BEDROCK_SECRET_KEY=... \
 *   mvn test -pl connectors-e2e-test/connectors-e2e-test-agentic-ai -Dtest=AiAgentSandboxSkillsIT
 * </pre>
 */
@SpringBootTest(
    classes = {AiAgentE2ETestApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=false",
      "camunda.connector.polling.enabled=false",
      // Resolve {{secrets.X}} directly from the env var X (the BPMN uses unprefixed secret names
      // such as DAYTONA_API_KEY / AWS_BEDROCK_ACCESS_KEY); default prefix is SECRET_.
      "camunda.connector.secret-provider.environment.prefix=",
      "camunda.connector.agenticai.tools.process-definition.cache.enabled=false",
      // Judge LLM configuration (uses Bedrock Haiku for cost efficiency)
      "camunda.process-test.judge.chat-model.provider=amazon-bedrock",
      "camunda.process-test.judge.chat-model.model=eu.anthropic.claude-haiku-4-5-20251001-v1:0",
      "camunda.process-test.judge.chat-model.region=eu-central-1",
      "camunda.process-test.judge.chat-model.credentials.access-key=${AWS_BEDROCK_ACCESS_KEY:NOT_SET}",
      "camunda.process-test.judge.chat-model.credentials.secret-key=${AWS_BEDROCK_SECRET_KEY:NOT_SET}",
      "camunda.process-test.judge.threshold=0.6"
    })
@CamundaSpringProcessTest
@WireMockTest
@Import(CamundaDocumentTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "DAYTONA_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AWS_BEDROCK_ACCESS_KEY", matches = ".+")
public class AiAgentSandboxSkillsIT {

  private static final String BPMN_RESOURCE = "agentic-ai-sandbox-chat.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String PROCESS_ID = "ai-agent-chat-with-tools";

  private static final String SKILL_RESOURCE_DIR = "skills/sandbox-doc-tools/";
  private static final String SKILL_PATH = "/skills/sandbox-doc-tools.zip";
  private static final String SAMPLE_CSV_PATH = "/sandbox-sample.csv";

  // Generous timeout for real sandbox creation + multiple LLM round-trips.
  private static final Duration PROCESS_TIMEOUT = Duration.ofMinutes(5);

  private static final Logger LOG = LoggerFactory.getLogger(AiAgentSandboxSkillsIT.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private CamundaClient camundaClient;

  private String skillDownloadUrl;
  private String sampleCsvUrl;

  // Process instances started by each test, used to resolve and delete their sandboxes in teardown.
  private final List<Long> startedProcessInstanceKeys = new ArrayList<>();

  @BeforeAll
  static void setUp() {
    setAssertionTimeout(PROCESS_TIMEOUT);
  }

  @BeforeEach
  void setupStubs(WireMockRuntimeInfo wireMock) throws IOException {
    InMemoryDocumentStore.INSTANCE.clear();

    skillDownloadUrl = wireMock.getHttpBaseUrl() + SKILL_PATH;
    sampleCsvUrl = wireMock.getHttpBaseUrl() + SAMPLE_CSV_PATH;

    // Serve the sandbox-doc-tools skill as a .zip built on the fly from the checked-in source.
    stubFor(
        get(urlPathEqualTo(SKILL_PATH))
            .willReturn(
                aResponse()
                    .withBody(buildSkillZip())
                    .withHeader("Content-Type", "application/zip")));

    // Serve the sample CSV so the agent's Fetch_URL tool can produce a Camunda document from it.
    stubFor(
        get(urlPathEqualTo(SAMPLE_CSV_PATH))
            .willReturn(
                aResponse()
                    .withBodyFile("sandbox-sample.csv")
                    .withHeader("Content-Type", "text/csv")));

    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();
  }

  // ---------------------------------------------------------------------------
  // A — Sandbox basics: bash compute + OS inspection
  // ---------------------------------------------------------------------------

  @Test
  void sandboxBasics_bashComputeAndOsInfo() {
    var processInstance =
        startChat(
            "Use your sandbox to compute the 10th Fibonacci number with a shell command, and also "
                + "tell me which operating system the sandbox runs on. Report both the computed "
                + "number and the OS.");

    completeUserFeedbackSatisfied(processInstance);

    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasVariableSatisfiesJudge(
            "agent",
            """
            The agent variable contains a responseText showing it used a sandbox/shell to compute
            the 10th Fibonacci number (55) and reports the operating system the sandbox runs on
            (e.g. Linux).""");
  }

  // ---------------------------------------------------------------------------
  // B — Export OUT: produce a file in the sandbox, return it as a document
  // ---------------------------------------------------------------------------

  @Test
  void exportDocument_producesDownloadableCsv() {
    var processInstance =
        startChat(
            "In your sandbox, create a CSV file that lists the numbers 1 to 5 in one column and "
                + "their squares in a second column, then export that file so I can download it. "
                + "Tell me you have exported it.");

    completeUserFeedbackSatisfied(processInstance);

    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasVariableSatisfiesJudge(
            "agent",
            """
            The agent variable contains a responseText indicating it created a CSV file in its
            sandbox containing the numbers 1 through 5 and their squares (1, 4, 9, 16, 25) and
            exported it as a downloadable document/attachment.""");
  }

  // ---------------------------------------------------------------------------
  // C — Import IN: tool-call-result document pulled into the sandbox
  // ---------------------------------------------------------------------------

  @Test
  void importDocument_fromToolCallResult() {
    var processInstance =
        startChat(
            "Use your Fetch URL tool to fetch this file: "
                + sampleCsvUrl
                + " . Then import the fetched document into your sandbox and tell me how many lines "
                + "it has and what its header row (the first line) contains.");

    completeUserFeedbackSatisfied(processInstance);

    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasVariableSatisfiesJudge(
            "agent",
            """
            The agent variable contains a responseText showing it fetched a CSV document, imported
            it into its sandbox, and reports details of the file: that the header/first line is
            "name,score" and that the file contains rows for alice, bob and carol (3 data rows / 4
            total lines).""");
  }

  // ---------------------------------------------------------------------------
  // D — Skill round-trip: load skill, fetch → import → inspect → summarize
  // ---------------------------------------------------------------------------

  @Test
  void skillRoundTrip_loadInspectAndSummarize() {
    var processInstance =
        startChat(
            "Load your document tools skill. Then use your Fetch URL tool to fetch "
                + sampleCsvUrl
                + " , import the fetched document into your sandbox, inspect it using the skill's "
                + "approach, and give me a short summary of what the file contains.");

    completeUserFeedbackSatisfied(processInstance);

    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasVariableSatisfiesJudge(
            "agent",
            """
            The agent variable contains a responseText showing it loaded a document-tools skill,
            imported the fetched CSV into its sandbox, inspected it, and summarized its contents:
            a header row with columns "name" and "score" and data rows for alice, bob and carol.""");
  }

  // ---------------------------------------------------------------------------
  // E — Skill catalog: advertise the available skill and what it enables
  // ---------------------------------------------------------------------------

  @Test
  void skillCatalog_describesAvailableSkill() {
    var processInstance =
        startChat(
            "What skills do you have available? Load the one for working with documents and briefly "
                + "describe what it lets you do.");

    completeUserFeedbackSatisfied(processInstance);

    assertThatProcessInstance(processInstance)
        .isCompleted()
        .hasVariableSatisfiesJudge(
            "agent",
            """
            The agent variable contains a responseText that identifies a skill for working with
            documents (e.g. "sandbox-doc-tools") and explains it can bring conversation documents
            into a sandbox, inspect them with shell tools, and optionally export a derived
            document.""");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ProcessInstanceEvent startChat(String inputText) {
    var instance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText", inputText,
                    "inputDocuments", List.of(),
                    "skillDownloadUrl", skillDownloadUrl))
            .send()
            .join();
    startedProcessInstanceKeys.add(instance.getProcessInstanceKey());
    return instance;
  }

  /**
   * Best-effort teardown: deletes the Daytona sandbox created by each test immediately so the happy
   * path leaves nothing behind. The BPMN's short auto-stop + auto-delete is the crash-safe net for
   * when this never runs (JVM dies); this just makes cleanup instant in the common case.
   */
  @AfterEach
  void deleteSandboxes() {
    String apiKey = System.getenv("DAYTONA_API_KEY");
    String apiUrl = System.getenv("DAYTONA_API_URL"); // may be null for cloud
    try {
      for (Long processInstanceKey : startedProcessInstanceKeys) {
        String sandboxId = resolveSandboxId(processInstanceKey);
        if (sandboxId != null) {
          deleteSandbox(apiKey, apiUrl, sandboxId);
        }
      }
    } finally {
      startedProcessInstanceKeys.clear();
    }
  }

  /**
   * Reads the persisted {@code agentContext} variable and extracts the sandbox session id from
   * {@code properties.sandboxHandle.sessionId}. Returns {@code null} if not resolvable (e.g. the
   * agent never opened a sandbox or the variable is gone).
   */
  private String resolveSandboxId(long processInstanceKey) {
    try {
      // withFullValues() disables value truncation — agentContext is large, so the default
      // truncated value would not parse as JSON.
      var items =
          camundaClient
              .newVariableSearchRequest()
              .filter(f -> f.processInstanceKey(processInstanceKey).name("agentContext"))
              .withFullValues()
              .send()
              .join()
              .items();
      if (items.isEmpty()) {
        return null;
      }
      JsonNode context = OBJECT_MAPPER.readTree(items.getFirst().getValue());
      JsonNode sessionId = context.path("properties").path("sandboxHandle").path("sessionId");
      return sessionId.isMissingNode() || sessionId.isNull() ? null : sessionId.asText();
    } catch (Exception e) {
      LOG.warn(
          "Could not resolve sandbox id for process instance {}: {}",
          processInstanceKey,
          e.getMessage());
      return null;
    }
  }

  private static void deleteSandbox(String apiKey, String apiUrl, String sandboxId) {
    DaytonaConfig.Builder builder = new DaytonaConfig.Builder().apiKey(apiKey);
    if (apiUrl != null && !apiUrl.isBlank()) {
      builder = builder.apiUrl(apiUrl);
    }
    try (Daytona daytona = new Daytona(builder.build())) {
      daytona.get(sandboxId).delete();
      LOG.info("Deleted Daytona sandbox id={} during e2e teardown", sandboxId);
    } catch (Exception e) {
      LOG.warn(
          "Failed to delete Daytona sandbox id={} during e2e teardown: {}",
          sandboxId,
          e.getMessage());
    }
  }

  private void completeUserFeedbackSatisfied(ProcessInstanceEvent processInstance) {
    // Wait for the agent to finish its turn and hand off to the User Feedback user task.
    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstance.getProcessInstanceKey()))
            .send()
            .join();
    long taskKey = tasks.items().getFirst().getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(taskKey)
        .variables(Map.of("userSatisfied", true))
        .send()
        .join();
  }

  private static byte[] buildSkillZip() throws IOException {
    var baos = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(baos)) {
      addZipEntry(zip, "SKILL.md");
      addZipEntry(zip, "inspect.sh");
    }
    return baos.toByteArray();
  }

  private static void addZipEntry(ZipOutputStream zip, String fileName) throws IOException {
    try (InputStream in =
        AiAgentSandboxSkillsIT.class
            .getClassLoader()
            .getResourceAsStream(SKILL_RESOURCE_DIR + fileName)) {
      if (in == null) {
        throw new IllegalStateException(
            "Skill resource not found: " + SKILL_RESOURCE_DIR + fileName);
      }
      zip.putNextEntry(new ZipEntry(fileName));
      zip.write(in.readAllBytes());
      zip.closeEntry();
    }
  }
}
