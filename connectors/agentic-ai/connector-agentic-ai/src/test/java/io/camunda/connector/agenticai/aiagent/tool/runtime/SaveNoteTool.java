/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.tool.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A stateful runtime tool used to prove the API: it appends a note to the {@code notes} property of
 * the agent context. It is available while the agent has a memory configuration, binds its
 * arguments to a record, answers an invalid call with an error result and changes the agent state
 * through a context update.
 */
public final class SaveNoteTool {

  public static final String NAME = "c8__save_note";
  public static final String NOTES_PROPERTY = "notes";

  public record Note(@Nullable String text) {}

  private SaveNoteTool() {}

  public static RuntimeTool create() {
    return RuntimeTool.builder(NAME)
        .description("Saves a note for later.")
        .inputSchema(RuntimeToolSchema.object().string("text", "The text of the note", true))
        .available(configuration -> configuration.memory() != null)
        .handler(
            Note.class,
            (note, context) -> {
              if (note.text() == null || note.text().isBlank()) {
                return RuntimeToolResult.error("The note must not be blank.");
              }
              if (note.text().equals("explode")) {
                throw new IllegalStateException("unexpected failure");
              }
              if (note.text().equals("reject")) {
                throw new RuntimeToolException("This note is not allowed.");
              }
              final var count = notesOf(context.properties()).size() + 1;
              return RuntimeToolResult.success("Saved note #" + count)
                  .withContextUpdate(
                      agentContext -> {
                        final var notes = new ArrayList<>(notesOf(agentContext.properties()));
                        notes.add(note.text());
                        return agentContext.withProperty(NOTES_PROPERTY, List.copyOf(notes));
                      });
            });
  }

  @SuppressWarnings("unchecked")
  public static List<Object> notesOf(Map<String, Object> properties) {
    return properties.get(NOTES_PROPERTY) instanceof List<?> list ? (List<Object>) list : List.of();
  }
}
