/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolCallAdmissionTest {

  private static final ToolDefinition PROCESS_TOOL =
      new ToolDefinition("getWeather", "Weather", Map.of("type", "object"));
  private static final ToolDefinition RUNTIME_TOOL =
      ToolDefinition.runtime("acme_note", "Note", Map.of("type", "object"));

  private static final ToolDefinition PROCESS_UPLOAD_TOOL =
      new ToolDefinition("upload", "Upload", documentSchema());
  private static final ToolDefinition RUNTIME_UPLOAD_TOOL =
      ToolDefinition.runtime("acme_upload", "Upload", documentSchema());

  private static final ToolDefinition PROCESS_ATTACH_TOOL =
      new ToolDefinition("attach", "Attach", attachSchema());

  private static final String UNKNOWN_DOC = rejection("doc", "doc_000000000000");

  private static final String BOGUS_DOC = mustBeHandle("doc");

  private static final AgentContext WITHOUT_DOCUMENTS =
      AgentContext.empty().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS));

  private static final Document REPORT = createDocument("report data", "report.txt");
  private static final Map<String, Object> REPORT_HANDLE =
      Map.of("type", "camunda.agenticai.document", "id", AgentDocumentId.of(REPORT).value());

  private static final Map<String, Object> UNKNOWN_HANDLE =
      Map.of("type", "camunda.agenticai.document", "id", "doc_000000000000");

  private static final AgentContext KNOWING_REPORT =
      AgentContext.empty()
          .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS))
          .withDocuments(
              AgentDocuments.empty()
                  .register(
                      List.of(
                          AgentDocument.of(
                              REPORT, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)))));

  private final ToolCallAdmission admission =
      new ToolCallAdmission(new DocumentParameterResolver(TestObjectMapperSupplier.INSTANCE));

  @Test
  void eachCallGetsTheKindOfTheOfferedToolOfItsName() {
    final var message =
        messageWith(
            new ToolCall("p1", "getWeather", Map.of("city", "Berlin")),
            new ToolCall("r1", "acme_note", Map.of("text", "x")));

    final var admitted =
        admission.admit(message, List.of(PROCESS_TOOL, RUNTIME_TOOL), WITHOUT_DOCUMENTS);

    assertThat(admitted)
        .isEqualTo(
            message.withToolCalls(
                List.of(
                    new ToolCall("p1", "getWeather", Map.of("city", "Berlin")),
                    new ToolCall("r1", "acme_note", Map.of("text", "x"))
                        .withKind(ToolKind.RUNTIME))));
  }

  @Test
  void aCallOfAToolThatIsNotOfferedIsRejected() {
    final var message =
        messageWith(
            new ToolCall("x1", "acme_unknown", Map.of()),
            new ToolCall("p1", "getWeather", Map.of()));

    final var admitted = admission.admit(message, List.of(PROCESS_TOOL), WITHOUT_DOCUMENTS);

    assertThat(admitted.toolCalls())
        .containsExactly(
            new ToolCall("x1", "acme_unknown", Map.of())
                .withRejection("The tool 'acme_unknown' does not exist."),
            new ToolCall("p1", "getWeather", Map.of()));
  }

  @Test
  void aMessageWithoutCallsIsReturnedAsItIs() {
    final var message = AssistantMessage.builder().content(List.of(textContent("Hi"))).build();

    assertThat(admission.admit(message, List.of(PROCESS_TOOL), WITHOUT_DOCUMENTS))
        .isSameAs(message);
  }

  @Test
  void aProcessCallWithAnInvalidDocumentParameterIsRejectedAndKeepsItsKind() {
    final var call = new ToolCall("p1", "upload", Map.of("doc", "bogus"));

    final var admitted =
        admission.admit(KNOWING_REPORT, List.of(PROCESS_UPLOAD_TOOL), messageWith(call));

    assertThat(admitted.toolCalls()).containsExactly(call.withRejection(BOGUS_DOC));
  }

  @Test
  void aCallOfEitherKindWithADocumentParameterIsRejectedWhenTheAgentKnowsNoDocuments() {
    final var knowingNothing =
        AgentContext.empty()
            .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS));
    final var processCall = new ToolCall("p1", "upload", Map.of("doc", REPORT_HANDLE));
    final var runtimeCall = new ToolCall("r1", "acme_upload", Map.of("doc", "bogus"));
    final var noDocuments =
        "Parameter 'doc' takes a document, but the conversation has no documents. Do not pass a"
            + " document.";

    final var admitted =
        admission.admit(
            knowingNothing,
            List.of(PROCESS_UPLOAD_TOOL, RUNTIME_UPLOAD_TOOL),
            messageWith(processCall, runtimeCall));

    assertThat(admitted.toolCalls())
        .containsExactly(
            processCall.withRejection(noDocuments),
            runtimeCall.withKind(ToolKind.RUNTIME).withRejection(noDocuments));
  }

  @Test
  void aRuntimeCallWithAnInvalidDocumentParameterIsRejectedAndKeepsItsKind() {
    final var call = new ToolCall("r1", "acme_upload", Map.of("doc", "bogus"));

    final var admitted =
        admission.admit(KNOWING_REPORT, List.of(RUNTIME_UPLOAD_TOOL), messageWith(call));

    assertThat(admitted.toolCalls())
        .containsExactly(call.withKind(ToolKind.RUNTIME).withRejection(BOGUS_DOC));
  }

  @Test
  void aCallOfEitherKindWithAHandleOfAKnownDocumentIsAdmittedWithItsArgumentsUnchanged() {
    final var processCall = new ToolCall("p1", "upload", Map.of("doc", REPORT_HANDLE));
    final var runtimeCall = new ToolCall("r1", "acme_upload", Map.of("doc", REPORT_HANDLE));

    final var admitted =
        admission.admit(
            KNOWING_REPORT,
            List.of(PROCESS_UPLOAD_TOOL, RUNTIME_UPLOAD_TOOL),
            messageWith(processCall, runtimeCall));

    assertThat(admitted.toolCalls())
        .containsExactly(processCall, runtimeCall.withKind(ToolKind.RUNTIME));
  }

  @Test
  void aCallOfEitherKindWithTheBareIdOfADocumentIsRejected() {
    final var knownId = AgentDocumentId.of(REPORT).value();
    final var processKnown = new ToolCall("p1", "upload", Map.of("doc", knownId));
    final var runtimeKnown = new ToolCall("r1", "acme_upload", Map.of("doc", " " + knownId + "\n"));
    final var processUnknown = new ToolCall("p2", "upload", Map.of("doc", "doc_000000000000"));

    final var admitted =
        admission.admit(
            KNOWING_REPORT,
            List.of(PROCESS_UPLOAD_TOOL, RUNTIME_UPLOAD_TOOL),
            messageWith(processKnown, runtimeKnown, processUnknown));

    assertThat(admitted.toolCalls())
        .containsExactly(
            processKnown.withRejection(BOGUS_DOC),
            runtimeKnown.withKind(ToolKind.RUNTIME).withRejection(BOGUS_DOC),
            processUnknown.withRejection(BOGUS_DOC));
  }

  @Test
  void rejectsOnlyTheCallsWithAnInvalidDocumentParameterOfABatch() {
    final var invalidProcessCall = new ToolCall("p1", "upload", Map.of("doc", "bogus"));
    final var validRuntimeCall = new ToolCall("r1", "acme_upload", Map.of("doc", REPORT_HANDLE));
    final var invalidRuntimeCall = new ToolCall("r2", "acme_upload", Map.of("doc", "bogus"));
    final var otherCall = new ToolCall("p2", "getWeather", Map.of("city", "Berlin"));

    final var admitted =
        admission.admit(
            KNOWING_REPORT,
            List.of(PROCESS_UPLOAD_TOOL, RUNTIME_UPLOAD_TOOL, PROCESS_TOOL),
            messageWith(invalidProcessCall, validRuntimeCall, invalidRuntimeCall, otherCall));

    assertThat(admitted.toolCalls())
        .containsExactly(
            invalidProcessCall.withRejection(BOGUS_DOC),
            validRuntimeCall.withKind(ToolKind.RUNTIME),
            invalidRuntimeCall.withKind(ToolKind.RUNTIME).withRejection(BOGUS_DOC),
            otherCall);
  }

  @Test
  void doesNotCheckDocumentParametersWithoutTheDocumentsFeature() {
    final var processCall = new ToolCall("p1", "upload", Map.of("doc", "bogus"));
    final var runtimeCall = new ToolCall("r1", "acme_upload", Map.of("doc", "bogus"));

    final var admitted =
        admission.admit(
            NEW_INSTANCE,
            List.of(PROCESS_UPLOAD_TOOL, RUNTIME_UPLOAD_TOOL),
            WITHOUT_DOCUMENTS);

    assertThat(admitted.toolCalls())
        .containsExactly(processCall, runtimeCall.withKind(ToolKind.RUNTIME));
  }

  @Test
  void doesNotCheckTheArgumentsOfAToolWithoutDocumentParameters() {
    final var processCall = new ToolCall("p1", "getWeather", Map.of("doc", "bogus"));
    final var runtimeCall = new ToolCall("r1", "acme_note", Map.of("doc", "bogus"));

    final var admitted =
        admission.admit(
            KNOWING_REPORT,
            List.of(PROCESS_TOOL, RUNTIME_TOOL),
            messageWith(processCall, runtimeCall));

    assertThat(admitted.toolCalls())
        .containsExactly(processCall, runtimeCall.withKind(ToolKind.RUNTIME));
  }

  @Test
  void aCallOfEitherKindWithAHandleOfAnUnknownDocumentIsRejectedAndKeepsItsKind() {
    final var processCall = new ToolCall("p1", "upload", Map.of("doc", UNKNOWN_HANDLE));
    final var runtimeCall = new ToolCall("r1", "acme_upload", Map.of("doc", UNKNOWN_HANDLE));

    final var admitted =
        admission.admit(
            KNOWING_REPORT,
            List.of(PROCESS_UPLOAD_TOOL, RUNTIME_UPLOAD_TOOL),
            messageWith(processCall, runtimeCall));

    assertThat(admitted.toolCalls())
        .containsExactly(
            processCall.withRejection(UNKNOWN_DOC),
            runtimeCall.withKind(ToolKind.RUNTIME).withRejection(UNKNOWN_DOC));
  }

  @Test
  void aCallWithAnUnknownHandleNestedInAnObjectOrAnArrayIsRejectedWithTheFullPath() {
    final var call =
        new ToolCall(
            "p1", "attach", Map.of("message", Map.of("file", UNKNOWN_HANDLE, "caption", "c")));
    final var list =
        new ToolCall("p2", "attach", Map.of("files", List.of(REPORT_HANDLE, UNKNOWN_HANDLE)));

    final var admitted =
        admission.admit(KNOWING_REPORT, List.of(PROCESS_ATTACH_TOOL), messageWith(call, list));

    assertThat(admitted.toolCalls())
        .containsExactly(
            call.withRejection(rejection("message.file", "doc_000000000000")),
            list.withRejection(rejection("files[1]", "doc_000000000000")));
  }

  private static String mustBeHandle(String path) {
    return ("Parameter '%s' must be the handle of a document from the conversation: a JSON object"
            + " such as {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.")
        .formatted(path);
  }

  private static String rejection(String path, String id) {
    return ("Parameter '%s' has the id '%s', but the conversation has no document with this id."
            + " Pass the handle of a document from the conversation: a JSON object such as"
            + " {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.")
        .formatted(path, id);
  }

  /** A file with a caption in an object, and a list of documents. */
  private static Map<String, Object> attachSchema() {
    final var message =
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "file",
                DocumentParameter.documentSchema(null),
                "caption",
                Map.of("type", "string")));
    final var files = Map.of("type", "array", "items", DocumentParameter.documentSchema(null));
    return Map.of("type", "object", "properties", Map.of("message", message, "files", files));
  }

  private static Map<String, Object> documentSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of("doc", DocumentParameter.documentSchema("The file")));
  }

  private static AssistantMessage messageWith(ToolCall... calls) {
    return AssistantMessage.builder()
        .content(List.of(textContent("calling tools")))
        .toolCalls(List.of(calls))
        .build();
  }
}
