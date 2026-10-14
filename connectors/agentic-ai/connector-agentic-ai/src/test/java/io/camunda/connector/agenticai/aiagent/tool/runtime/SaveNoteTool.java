/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A stateful runtime tool used to prove the API: it appends a note to the {@code notes} property of
 * the agent context. It is available while the agent has a memory configuration, binds its
 * arguments to a record, answers an invalid call with an error result and changes the agent state
 * through a context update.
 */
public final class SaveNoteTool implements RuntimeTool {

  public static final String NAME = "c8__save_note";
  public static final String NOTES_PROPERTY = "notes";

  private static final ToolDefinition DEFINITION =
      new ToolDefinition(
          NAME,
          "Saves a note for later.",
          ordered(
              "type",
              "object",
              "properties",
              ordered("text", ordered("type", "string", "description", "The text of the note")),
              "required",
              List.of("text")));

  public record Note(@Nullable String text) {}

  private static Map<String, Object> ordered(Object... keysAndValues) {
    final var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return Collections.unmodifiableMap(map);
  }

  @Override
  public ToolDefinition definition() {
    return DEFINITION;
  }

  @Override
  public boolean isAvailable(AgentExecutionContext executionContext, AgentContext agentContext) {
    return executionContext.configuration().memory() != null;
  }

  @Override
  public RuntimeToolResult call(
      RuntimeToolCall call,
      AgentExecutionContext executionContext,
      RuntimeToolConversation conversation) {
    final var note = call.argumentsAs(Note.class, TestObjectMapperSupplier.INSTANCE);
    if (note.text() == null || note.text().isBlank()) {
      return RuntimeToolResult.error("The note must not be blank.");
    }
    if (note.text().equals("explode")) {
      throw new IllegalStateException("unexpected failure");
    }
    if (note.text().equals("reject")) {
      throw new RuntimeToolException("This note is not allowed.");
    }
    final var count = notesOf(conversation.toAgentContext().properties()).size() + 1;
    return RuntimeToolResult.success("Saved note #" + count)
        .withContextUpdate(
            agentContext -> {
              final var notes = new ArrayList<>(notesOf(agentContext.properties()));
              notes.add(note.text());
              return agentContext.withProperty(NOTES_PROPERTY, List.copyOf(notes));
            });
  }

  @SuppressWarnings("unchecked")
  public static List<Object> notesOf(Map<String, Object> properties) {
    return properties.get(NOTES_PROPERTY) instanceof List<?> list ? (List<Object>) list : List.of();
  }
}
