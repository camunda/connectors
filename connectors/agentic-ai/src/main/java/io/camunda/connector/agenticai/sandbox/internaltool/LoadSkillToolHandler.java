/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.sandbox.skill.Skill;
import io.camunda.connector.agenticai.sandbox.spi.FileEntry;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements the {@code load_skill} internal tool: materializes a skill bundle into the sandbox
 * filesystem and returns the skill's instructions as the tool result so the LLM can read them.
 *
 * <p>The {@code name} parameter is intentionally a plain string, NOT an enum, because valid skill
 * names are resolved per-invocation from user configuration. The tool definition is built once at
 * startup and must therefore remain generic; the LLM is guided to valid names via the {@code
 * <available_skills>} section in the system prompt (T7c).
 *
 * <p>The handler is idempotent: if a skill's {@code SKILL.md} already exists in the workspace it
 * returns a short "already loaded" note instead of re-writing files.
 */
public class LoadSkillToolHandler implements InternalToolHandler {

  private final ToolDefinition definition;

  public LoadSkillToolHandler() {
    this.definition = buildDefinition();
  }

  @Override
  public String name() {
    return InternalToolNames.LOAD_SKILL;
  }

  @Override
  public ToolDefinition definition() {
    return definition;
  }

  @Override
  public ToolCallResult execute(
      ToolCall toolCall, SandboxSession session, InternalToolContext context) {
    String skillName = (String) toolCall.arguments().get("name");
    if (skillName == null || skillName.isBlank()) {
      return errorResult(toolCall, "missing required argument: name");
    }

    if (context.skills().isEmpty()) {
      return errorResult(toolCall, "no skills are configured for this agent.");
    }

    Skill skill =
        context.skills().stream().filter(s -> s.name().equals(skillName)).findFirst().orElse(null);
    if (skill == null) {
      String available =
          context.skills().stream().map(Skill::name).reduce((a, b) -> a + ", " + b).orElse("");
      return errorResult(
          toolCall, "unknown skill '%s'. Available skills: %s.".formatted(skillName, available));
    }

    // Materialize under the sandbox working directory rather than a hardcoded mount point — the
    // writable root varies by provider/image (see SandboxSession#workDir).
    String dir = session.workDir() + "/skills/" + skill.name();
    String skillMdPath = dir + "/SKILL.md";

    // Idempotency: if SKILL.md already exists the skill is already loaded — return a short note.
    try {
      session.fs().stat(skillMdPath);
      // stat succeeded → already materialized
      String alreadyNote =
          ("Skill '%s' is already loaded at %s. If you no longer have its instructions in your "
                  + "context, read %s/SKILL.md with fs_read to retrieve them. Read bundled files "
                  + "with fs_read and run scripts with bash.")
              .formatted(skill.name(), dir, dir);
      return successResult(toolCall, alreadyNote);
    } catch (Exception ignored) {
      // stat failed → not yet materialized, proceed with writing
    }

    // Materialize all files from the bundle.
    List<FileEntry> entries = new ArrayList<>(skill.files().size());
    for (Skill.SkillFile file : skill.files()) {
      entries.add(new FileEntry(dir + "/" + file.relativePath(), file.content()));
    }
    session.fs().writeBatch(entries);

    // Build the result content.
    StringBuilder sb = new StringBuilder();
    sb.append("<skill_content name=\"")
        .append(skill.name())
        .append("\" location=\"")
        .append(dir)
        .append("\">\n");
    sb.append(skill.skillMdBody());
    if (!skill.skillMdBody().endsWith("\n")) {
      sb.append("\n");
    }
    sb.append("</skill_content>\n");

    sb.append("<skill_resources location=\"").append(dir).append("\">\n");
    for (Skill.SkillFile file : skill.files()) {
      sb.append("- ").append(file.relativePath()).append("\n");
    }
    sb.append("</skill_resources>\n");

    sb.append("Bundled files live under ")
        .append(dir)
        .append(". Read them with fs_read and run scripts with bash.");

    return successResult(toolCall, sb.toString());
  }

  private static ToolCallResult successResult(ToolCall toolCall, String content) {
    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content(content)
        .properties(
            Map.of(
                InternalToolExecutor.PROPERTY_EXECUTED_BY,
                InternalToolExecutor.EXECUTED_BY_SANDBOX))
        .build();
  }

  private static ToolCallResult errorResult(ToolCall toolCall, String message) {
    return ToolCallResult.builder()
        .id(toolCall.id())
        .name(toolCall.name())
        .content("Error: " + message)
        .properties(
            Map.of(
                InternalToolExecutor.PROPERTY_EXECUTED_BY,
                InternalToolExecutor.EXECUTED_BY_SANDBOX))
        .build();
  }

  private static ToolDefinition buildDefinition() {
    Map<String, Object> nameProp = new LinkedHashMap<>();
    nameProp.put(PROPERTY_TYPE, TYPE_STRING);
    nameProp.put(
        PROPERTY_DESCRIPTION,
        "The name of the skill to load. Must be one of the skills listed under "
            + "<available_skills> in the system prompt.");

    Map<String, Object> properties = Map.of("name", nameProp);

    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(PROPERTY_PROPERTIES, properties);
    schema.put(PROPERTY_REQUIRED, List.of("name"));

    return ToolDefinition.builder()
        .name(InternalToolNames.LOAD_SKILL)
        .description(
            "Materialize a skill bundle into the workspace and return its instructions. "
                + "The result reports the exact workspace location of the skill's files. "
                + "Read bundled files with fs_read and execute scripts with bash. "
                + "Calling this tool a second time for the same skill is a no-op.")
        .inputSchema(schema)
        .build();
  }
}
