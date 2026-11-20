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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess;

import static io.camunda.connector.e2e.agenticai.BpmnUtil.updateInputMappings;
import static io.camunda.connector.e2e.agenticai.aiagent.DocumentFlowFixtures.withUploadTool;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.EXTRACTED_DOCUMENTS_PREAMBLE;
import static io.camunda.connector.e2e.agenticai.aiagent.ToolCallResultDocumentAssertions.firstDocumentReference;
import static io.camunda.connector.e2e.agenticai.aiagent.fixtures.AgentTestFixtures.AI_AGENT_ELEMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag.CamundaDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag.ExternalDocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.e2e.agenticai.aiagent.fixtures.DocumentSetup;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * An AI Agent Sub-process with "Use document references" cleared keeps the document handling of
 * Camunda 8.10: a document of a tool result is its reference followed by a tag in the older
 * attribute format and the content, external documents are accepted, there are no agent documents
 * and no built-in tools, and a tool cannot take a document as a parameter.
 */
@SlowTest
public class AgentSubProcessDocumentReferencesOffTests extends BaseAgentSubProcessTest {

  private boolean withUploadTool;
  private boolean withUserPromptDeliveryReference;

  @BeforeEach
  void resetModel() {
    withUploadTool = false;
    withUserPromptDeliveryReference = false;
  }

  @Override
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    if (withUserPromptDeliveryReference) {
      // the template hides the delivery while document references are off, so only an edited
      // process has both
      updateInputMappings(
          model, AI_AGENT_ELEMENT_ID, Map.of("data.documents.userPromptDelivery", "reference"));
    }
    return withUploadTool ? withUploadTool(model, AI_AGENT_ELEMENT_ID) : model;
  }

  @Test
  void keepsToolResultDocumentsAsTheReferenceFollowedByTheOlderTagAndTheContent() throws Exception {
    final var fileUrl = wireMock.getHttpBaseUrl() + "/test.pdf";
    final var aiFinalResponseText = "The content type is 'application/pdf'";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will download the document.",
            10,
            20,
            ToolCall.of("aaa111", "Download_A_File", "{\"url\": \"%s\"}".formatted(fileUrl))),
        Turn.text(aiFinalResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    final var zeebeTest =
        awaitProcessCompletion(
            createProcessInstance(
                DocumentSetup.REFERENCES_OFF, Map.of("userPrompt", "Go and download a document!")));

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(2);
    assertThat(recorded.lastRequest().toolDefinitions())
        .containsExactlyInAnyOrderElementsOf(expectedTools());

    final var lastMessages = recorded.lastRequest().messages();
    assertThat(lastMessages).hasSize(5);
    assertThat(lastMessages.get(3).role()).isEqualTo("tool");
    assertThat(lastMessages.get(3).toolCallId()).isEqualTo("aaa111");

    // tool result: the document serialized as its Camunda document reference
    final var reference = firstDocumentReference(lastMessages.get(3).content(), "camunda");
    assertThat(lastMessages.get(3).content()).doesNotContain("<doc");

    // synthetic user message: preamble, the tag in the older attribute format, the content
    final var expectedXmlTag =
        new CamundaDocumentReferenceXmlTag(
                "aaa111",
                "Download_A_File",
                reference.path("documentId").asText(),
                reference.path("storeId").asText(),
                reference.path("metadata").path("contentType").asText(),
                reference.path("metadata").path("fileName").asText(null))
            .toXml();
    final var parts = lastMessages.get(4).contentParts();
    assertThat(lastMessages.get(4).role()).isEqualTo("user");
    assertThat(parts).hasSize(3);
    assertThat(parts.get(0).path("text").asText()).isEqualTo(EXTRACTED_DOCUMENTS_PREAMBLE);
    assertThat(parts.get(1).path("text").asText()).isEqualTo(expectedXmlTag);
    assertThat(parts.get(2).path("type").asText()).isEqualTo("file");

    assertAgentResponse(
        zeebeTest,
        agentResponse -> {
          AgentSubProcessResponseAssert.assertThat(agentResponse)
              .isReady()
              .hasResponseText(aiFinalResponseText)
              .hasFeatures(AgentFeatures.RUNTIME_TOOLS)
              .hasNoAgentDocuments();
        });
  }

  @Test
  void acceptsAnExternalToolResultDocument() throws Exception {
    final var docUrl = wireMock.getHttpBaseUrl() + "/test.pdf";
    final var docName = "Quarterly Report";
    final var aiFinalResponseText = "Referenced the external document.";

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            "I will reference an externally hosted file.",
            10,
            20,
            ToolCall.of(
                "ext111",
                "External_File_Reference",
                "{\"url\": \"%s\", \"name\": \"%s\"}".formatted(docUrl, docName))),
        Turn.text(aiFinalResponseText, 11, 22));
    enqueueUserFeedback(userSatisfiedFeedback());

    awaitProcessCompletion(
        createProcessInstance(
            DocumentSetup.REFERENCES_OFF, Map.of("userPrompt", "Reference an external document!")));

    final var lastMessages =
        OpenAiCompletionsRecordedConversation.recorded().lastRequest().messages();
    assertThat(lastMessages).hasSize(5);

    // tool result: the external reference serialized as { url, name }
    final var reference = firstDocumentReference(lastMessages.get(3).content(), "external");
    assertThat(reference.path("url").asText()).isEqualTo(docUrl);
    assertThat(reference.path("name").asText()).isEqualTo(docName);

    // synthetic user message: the tag with the URL and the name, followed by the content
    final var parts = lastMessages.get(4).contentParts();
    assertThat(parts).hasSize(3);
    assertThat(parts.get(1).path("text").asText())
        .isEqualTo(
            new ExternalDocumentReferenceXmlTag(
                    "ext111", "External_File_Reference", docUrl, docName)
                .toXml());
  }

  @Test
  void raisesAnIncidentForAToolWithADocumentParameter() throws Exception {
    withUploadTool = true;
    final var message =
        "Invalid tool 'Upload_A_File'. Parameter 'document': The type 'document' requires an agent instance with document reference support.";

    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(
                DocumentSetup.REFERENCES_OFF, Map.of("userPrompt", "Upload a document!")));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorMessage())
              .startsWith(message)
              .contains("code=DOCUMENT_PARAMETER_SCHEMA_INVALID");
        });
    assertThat(OpenAiCompletionsRecordedConversation.recorded().modelCallCount()).isZero();
  }

  @Test
  void raisesAnIncidentForTheUserPromptDeliveryReference() throws Exception {
    withUserPromptDeliveryReference = true;

    final var zeebeTest =
        awaitActiveIncidents(
            createProcessInstance(DocumentSetup.REFERENCES_OFF, Map.of("userPrompt", "Hello")));

    assertIncident(
        zeebeTest,
        incident -> {
          assertThat(incident.getElementId()).isEqualTo(AI_AGENT_ELEMENT_ID);
          assertThat(incident.getErrorMessage())
              .contains(
                  "Property: data.documents.userPromptDelivery: Validation failed. Original message: The user prompt delivery \"Reference\" requires the option \"Use document references\". Select this option, or select the user prompt delivery \"Content\".");
        });
    assertThat(OpenAiCompletionsRecordedConversation.recorded().modelCallCount()).isZero();
  }
}
