/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.api.tool.AgentDocument;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolDocuments;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.runtime.core.document.InlineDocument;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A runtime tool used to prove the API: it returns each note as a new document, which stays in its
 * tool result, so the notes of the earlier turns in the context window are the documents the agent
 * knows. It is enabled while the agent has a memory configuration, binds its arguments to a record,
 * answers an invalid call with an error result and changes the agent state through the documents of
 * its result.
 */
public final class SaveNoteTool implements RuntimeTool {

  public static final String NAME = "c8__save_note";
  private static final String FILE_NAME_PREFIX = "note-";

  private static final ToolDefinition DEFINITION =
      new ToolDefinition(NAME, "Saves a note for later.", schema());

  public record Note(@Nullable String text) {}

  private static Map<String, Object> schema() {
    final var text = new LinkedHashMap<String, Object>();
    text.put("type", "string");
    text.put("description", "The text of the note");
    final var properties = new LinkedHashMap<String, Object>();
    properties.put("text", text);
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.of("text"));
    return schema;
  }

  @Override
  public ToolDefinition definition() {
    return DEFINITION;
  }

  @Override
  public boolean isEnabled(RuntimeToolContext context) {
    return context.executionContext().configuration().memory() != null;
  }

  @Override
  public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
    final var note = call.argumentsAs(Note.class);
    if (note.text() == null || note.text().isBlank()) {
      return RuntimeToolResult.error("The note must not be blank.");
    }
    if (note.text().equals("explode")) {
      throw new IllegalStateException("unexpected failure");
    }
    if (note.text().equals("reject")) {
      throw new RuntimeToolException("This note is not allowed.");
    }
    final var number =
        notesOf(context.documents().all().stream().map(AgentDocument::document)).size() + 1;
    final var document =
        new InlineDocument(note.text(), FILE_NAME_PREFIX + number + ".txt", "text/plain");
    return RuntimeToolResult.success(
        List.of(
            TextContent.textContent("Saved note #" + number),
            DocumentContent.documentContent(document)));
  }

  /** The texts of the notes among the given documents, in the order of the conversation. */
  public static List<String> notesOf(RuntimeToolDocuments documents) {
    return notesOf(documents.all().stream().map(AgentDocument::document));
  }

  private static List<String> notesOf(java.util.stream.Stream<Document> documents) {
    return documents
        .filter(document -> document.metadata().getFileName().startsWith(FILE_NAME_PREFIX))
        .map(document -> new String(document.asByteArray(), StandardCharsets.UTF_8))
        .toList();
  }
}
