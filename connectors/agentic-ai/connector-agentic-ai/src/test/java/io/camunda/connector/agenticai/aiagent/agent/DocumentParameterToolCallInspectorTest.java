/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentParameterToolCallInspectorTest {

  private static final ToolDefinition UPLOAD =
      ToolDefinition.builder()
          .name("upload")
          .inputSchema(Map.of())
          .documentParameters(List.of("/properties/doc"))
          .build();
  private static final ToolDefinition PLAIN =
      ToolDefinition.builder().name("plain").inputSchema(Map.of()).build();

  private final DocumentParameterToolCallInspector inspector =
      new DocumentParameterToolCallInspector();

  private final io.camunda.connector.api.document.Document document =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
          .create(
              DocumentCreationRequest.from("data".getBytes(StandardCharsets.UTF_8))
                  .contentType("text/plain")
                  .fileName("report.txt")
                  .build());

  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(List.of(new IngestedDocument(document, "call-1", "search")));

  @Test
  void passesAToolCallWithKnownDocuments() {
    final var verdict =
        inspector.inspect(
            new ToolCall("1", "upload", Map.of("doc", DocumentHandle.idFor(document))),
            new LocalToolCallContext(List.of(UPLOAD), registry));

    assertThat(verdict).isEqualTo(new ToolCallVerdict.Pass());
  }

  @Test
  void passesAToolCallOfAToolWithoutDocumentParameters() {
    final var verdict =
        inspector.inspect(
            new ToolCall("1", "plain", Map.of("doc", "nope")),
            new LocalToolCallContext(List.of(PLAIN), registry));

    assertThat(verdict).isEqualTo(new ToolCallVerdict.Pass());
  }

  @Test
  void passesAToolCallOfAnUnknownTool() {
    final var verdict =
        inspector.inspect(
            new ToolCall("1", "MCP_x___y", Map.of()),
            new LocalToolCallContext(List.of(UPLOAD), registry));

    assertThat(verdict).isEqualTo(new ToolCallVerdict.Pass());
  }

  @Test
  void answersAnUnknownIdWithTheParameterAndTheKnownDocuments() {
    final var verdict =
        inspector.inspect(
            new ToolCall("1", "upload", Map.of("doc", "nope")),
            new LocalToolCallContext(List.of(UPLOAD), registry));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ToolCallVerdict.Answer.class,
            answer ->
                assertThat(answer.content())
                    .isEqualTo(
                        "Unknown document id 'nope' for parameter 'doc'. Known documents:\n"
                            + "<doc id=\"%s\" fileName=\"report.txt\" contentType=\"text/plain\" toolName=\"search\" toolCallId=\"call-1\" />"
                                .formatted(DocumentHandle.idFor(document))));
  }

  @Test
  void saysSoWhenNoDocumentIsKnown() {
    final var verdict =
        inspector.inspect(
            new ToolCall("1", "upload", Map.of("doc", "nope")),
            new LocalToolCallContext(List.of(UPLOAD), DocumentRegistry.empty()));

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ToolCallVerdict.Answer.class,
            answer ->
                assertThat(answer.content())
                    .isEqualTo(
                        "Unknown document id 'nope' for parameter 'doc'. No documents are known."));
  }
}
