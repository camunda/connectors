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

import static io.camunda.process.test.api.CamundaAssert.assertThatProcessInstance;
import static io.camunda.process.test.api.CamundaAssert.setAssertionTimeout;

import io.camunda.client.CamundaClient;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * CPT E2E tests validating document extraction from tool call results.
 *
 * <p>Tests verify that the AI Agent connector correctly delivers PDF documents returned by tool
 * calls to the LLM, and that the LLM can reason about their content.
 *
 * <p>Requires {@code OPENAI_API_KEY} environment variable and a running connectors Docker image.
 *
 * <p>Document delivery approach: mock workers create documents via {@code
 * camundaClient.newCreateDocumentCommand()}, storing them in the embedded Zeebe engine. The
 * document reference is wrapped in a {@link CamundaDocumentReferenceModel} which serializes with
 * the {@code "camunda.document.type": "camunda"} discriminator, allowing the AI Agent connector
 * (running in Docker) to recognize and fetch the document content from embedded Zeebe via gRPC.
 */
@SpringBootTest(classes = AiAgentE2ETestApplication.class)
@CamundaSpringProcessTest
@ActiveProfiles("it-real-llm")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class AiAgentE2EDocumentTestIT {

  private static final String BPMN_RESOURCE = "ai-agent-e2e-document.bpmn";
  private static final String FORM_RESOURCE = "ai-agent-chat-user-feedback.form";
  private static final String PROCESS_ID = "ai-agent-e2e-document";
  private static final String HTTP_JSON_JOB_TYPE = "io.camunda:http-json:1";

  private static final String DOC_PROJECT_LAUNCH = "/document-tool-call-results/project-launch.pdf";
  private static final String DOC_HEADCOUNT_REPORT =
      "/document-tool-call-results/headcount-report.pdf";
  private static final String DOC_AUTHOR_INFO = "/document-tool-call-results/author-info.pdf";

  @Autowired private CamundaClient camundaClient;
  @Autowired private CamundaProcessTestContext processTestContext;

  @BeforeAll
  static void setUp() {
    setAssertionTimeout(Duration.ofMinutes(3));
  }

  @BeforeEach
  void clearDocumentStore() {
    // Clear in-memory document store for local dev mode (connector runs in test JVM).
    // In CPT Docker mode documents are stored in embedded Zeebe and cleared per-test via Zeebe
    // lifecycle.
    InMemoryDocumentStore.INSTANCE.clear();
  }

  @BeforeEach
  void mockDocumentTools() {
    // The HTTP connector is disabled in the Docker bundle via
    // CONNECTOR_OUTBOUND_DISABLED=io.camunda:http-json:1, so HTTP tool jobs stay open.
    // This mock worker intercepts those jobs and returns appropriate tool call results.
    processTestContext
        .mockJobWorker(HTTP_JSON_JOB_TYPE)
        .withHandler(
            (jobClient, job) -> {
              var result =
                  switch (job.getElementId()) {
                    case "Download_Report" -> createDocumentRef(DOC_PROJECT_LAUNCH);
                    case "Search_Documents" ->
                        List.of(
                            createDocumentRef(DOC_PROJECT_LAUNCH),
                            createDocumentRef(DOC_HEADCOUNT_REPORT));
                    case "Fetch_Report" ->
                        Map.of(
                            "summary",
                            "Q1 Financial Report",
                            "attachments",
                            List.of(
                                createDocumentRef(DOC_PROJECT_LAUNCH),
                                createDocumentRef(DOC_HEADCOUNT_REPORT)),
                            "metadata",
                            Map.of("cover", createDocumentRef(DOC_AUTHOR_INFO)));
                    case "Get_External_Document" ->
                        createInlineDocumentRef(
                            "The Specification Document v2.0 defines the API architecture for the"
                                + " XR-7 platform integration layer.",
                            "Spec Sheet",
                            "text/plain");
                    case "Download_Corrupted_Report" -> createBrokenDocumentRef();
                    default -> null;
                  };
              var cmd = jobClient.newCompleteCommand(job);
              if (result != null) {
                cmd = cmd.variable("toolCallResult", result);
              }
              cmd.send().join();
            });
  }

  // ---------------------------------------------------------------------------
  // Scenario 1: Single document from tool call result
  // ---------------------------------------------------------------------------

  @Test
  void shouldReasonAboutSingleDocument() {
    deployResources();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Use the Download_Report tool to download the project report and tell me the"
                        + " project name and its launch date"))
            .send()
            .join();

    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    completeUserTask(processInstance.getProcessInstanceKey(), true);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText that mentions 'Project Zypherion' and the"
                + " specific date 'March 15, 2026', proving it read and reasoned about the PDF"
                + " document content provided via the tool call result");
  }

  // ---------------------------------------------------------------------------
  // Scenario 2: Multiple documents from tool call result
  // ---------------------------------------------------------------------------

  @Test
  void shouldReasonAboutMultipleDocuments() {
    deployResources();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Use the Search_Documents tool to search for all available reports and"
                        + " summarize what each one contains"))
            .send()
            .join();

    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    completeUserTask(processInstance.getProcessInstanceKey(), true);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText that mentions both 'Project Zypherion'"
                + " (from the project report) and '847 employees' or '847 people' (from the"
                + " headcount report), proving it received and read both PDF documents from the"
                + " tool call result");
  }

  // ---------------------------------------------------------------------------
  // Scenario 3: Documents in nested structure from tool call result
  // ---------------------------------------------------------------------------

  @Test
  void shouldReasonAboutNestedDocumentStructure() {
    deployResources();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Use the Fetch_Report tool to get the full report with all attachments and"
                        + " cover page, then describe the content of every document in it"))
            .send()
            .join();

    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    completeUserTask(processInstance.getProcessInstanceKey(), true);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText that references all three document facts:"
                + " (1) 'Project Zypherion' launching on 'March 15, 2026', (2) '847 employees'"
                + " or '847 people' across '12 offices', and (3) 'Dr. Kael Thrennix' as Chief"
                + " Analytics Officer - proving the agent read all documents from the nested"
                + " tool call result structure");
  }

  // ---------------------------------------------------------------------------
  // Scenario 4: External (inline) document reference from tool call result
  // ---------------------------------------------------------------------------

  @Test
  void shouldHandleExternalDocumentReference() {
    deployResources();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Use the Get_External_Document tool to retrieve the externally referenced"
                        + " specification document and summarize its content"))
            .send()
            .join();

    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    completeUserTask(processInstance.getProcessInstanceKey(), true);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText that mentions the 'Spec Sheet' document"
                + " or 'XR-7 platform' or 'specification', proving it received and reported on"
                + " the document returned by the Get_External_Document tool");
  }

  // ---------------------------------------------------------------------------
  // Scenario 5: Mixed text tool and document tool in one turn
  // ---------------------------------------------------------------------------

  @Test
  void shouldHandleTextAndDocumentMix() {
    deployResources();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "I need two things: use your GetDateAndTime tool to tell me the current time,"
                        + " and use the Download_Report tool to download the project report and"
                        + " tell me its project name"))
            .send()
            .join();

    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    completeUserTask(processInstance.getProcessInstanceKey(), true);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText that includes both a specific time value"
                + " (hours and minutes) from the GetDateAndTime tool AND mentions 'Project"
                + " Zypherion' from the project report PDF, proving both tools were invoked");
  }

  // ---------------------------------------------------------------------------
  // Scenario 6 (negative): Process completes normally when no documents are returned
  // ---------------------------------------------------------------------------

  @Test
  void shouldCompleteWithoutDocumentMessageWhenNoDocumentsReturned() {
    deployResources();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(Map.of("inputText", "What is the current date and time in Berlin?"))
            .send()
            .join();

    // Agent should use GetDateAndTime (script task) only — no document tools involved
    assertThatProcessInstance(processInstance).hasActiveElements("User_Feedback");

    completeUserTask(processInstance.getProcessInstanceKey(), true);

    assertThatProcessInstance(processInstance).isCompleted();
    assertThatProcessInstance(processInstance)
        .hasVariableSatisfiesJudge(
            "agent",
            "The agent variable contains a responseText that includes a specific time value"
                + " (hours and minutes) and references the CET or CEST timezone or the city"
                + " name Berlin, with no document-related errors");
  }

  // ---------------------------------------------------------------------------
  // Scenario 7 (negative): Broken document reference causes process incident
  // ---------------------------------------------------------------------------

  @Test
  void shouldHandleBrokenDocumentReference() {
    deployResources();

    var processInstance =
        camundaClient
            .newCreateInstanceCommand()
            .bpmnProcessId(PROCESS_ID)
            .latestVersion()
            .variables(
                Map.of(
                    "inputText",
                    "Use the Download_Corrupted_Report tool to download the corrupted report"))
            .send()
            .join();

    // The broken document reference (non-existent document ID) should cause the AI Agent connector
    // to fail when it tries to fetch the document content. After retries are exhausted, an incident
    // should be raised on the AI Agent task.
    assertThatProcessInstance(processInstance).hasActiveIncidents();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void deployResources() {
    camundaClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(BPMN_RESOURCE)
        .addResourceFromClasspath(FORM_RESOURCE)
        .send()
        .join();
  }

  private void completeUserTask(long processInstanceKey, boolean satisfied) {
    var tasks =
        camundaClient
            .newUserTaskSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();
    long taskKey = tasks.items().getFirst().getUserTaskKey();

    camundaClient
        .newCompleteUserTaskCommand(taskKey)
        .variables(Map.of("userSatisfied", satisfied))
        .send()
        .join();
  }

  /**
   * Creates a Camunda document reference by storing the PDF resource in the embedded Zeebe engine.
   *
   * <p>The reference is wrapped in a {@link CamundaDocumentReferenceModel} so it serializes with
   * the {@code "camunda.document.type": "camunda"} discriminator field. The AI Agent connector
   * recognizes this and fetches the document content from embedded Zeebe via gRPC.
   */
  private CamundaDocumentReferenceModel createDocumentRef(String resourcePath) {
    try {
      var fileName = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
      var pdfBytes =
          Objects.requireNonNull(
                  AiAgentE2EDocumentTestIT.class.getResourceAsStream(resourcePath),
                  "PDF resource not found: " + resourcePath)
              .readAllBytes();

      var response =
          camundaClient
              .newCreateDocumentCommand()
              .content(new ByteArrayInputStream(pdfBytes))
              .fileName(fileName)
              .contentType("application/pdf")
              .send()
              .join();

      return new CamundaDocumentReferenceModel(
          response.getStoreId(),
          response.getDocumentId(),
          response.getContentHash(),
          new CamundaDocumentMetadataModel(
              "application/pdf", null, null, fileName, null, null, null));
    } catch (Exception e) {
      throw new RuntimeException("Failed to create document from resource: " + resourcePath, e);
    }
  }

  /**
   * Creates an inline document reference containing text content directly embedded in the variable.
   * No network call is needed to fetch the content; the AI Agent connector reads it inline.
   */
  private InlineDocumentReferenceModel createInlineDocumentRef(
      String content, String name, String contentType) {
    return new InlineDocumentReferenceModel(content, name, contentType);
  }

  /**
   * Creates a broken Camunda document reference with a non-existent document ID. When the AI Agent
   * connector attempts to fetch the content, it will fail, causing either a process incident or a
   * graceful error response from the agent.
   */
  private CamundaDocumentReferenceModel createBrokenDocumentRef() {
    return new CamundaDocumentReferenceModel(
        "in-memory",
        "non-existent-document-id-" + System.currentTimeMillis(),
        "0",
        new CamundaDocumentMetadataModel(
            "application/pdf", null, null, "corrupted.pdf", null, null, null));
  }
}
