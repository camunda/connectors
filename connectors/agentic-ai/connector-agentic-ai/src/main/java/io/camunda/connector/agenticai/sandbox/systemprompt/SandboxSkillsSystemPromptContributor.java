/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.systemprompt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptContributor;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxState;
import io.camunda.connector.agenticai.sandbox.discovery.SkillCatalogEntry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contributes a skills catalog block to the agent system prompt when sandbox skills are available.
 *
 * <p>When the sandbox gateway tool's {@link SkillCatalogEntry} list is present in the agent's
 * {@link SandboxState} (stored in {@code agentContext.properties} under {@link
 * SandboxGatewayToolHandler#PROPERTY_SANDBOX}), this contributor renders an {@code
 * <available_skills>} XML block listing each skill's name, description, location, and directory.
 * The model activates a skill by calling {@code sandbox_fs_read} on the catalog {@code location} to
 * read the {@code SKILL.md}, then follows its instructions.
 */
public class SandboxSkillsSystemPromptContributor implements SystemPromptContributor {

  private static final Logger LOG =
      LoggerFactory.getLogger(SandboxSkillsSystemPromptContributor.class);

  public static final int ORDER = 90;

  private final ObjectMapper objectMapper;

  public SandboxSkillsSystemPromptContributor(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public String contribute(AgentExecutionContext executionContext, AgentContext agentContext) {
    List<SkillCatalogEntry> catalog = extractCatalog(agentContext);
    if (catalog == null || catalog.isEmpty()) {
      return null;
    }
    return buildSkillsCatalog(catalog);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  private List<SkillCatalogEntry> extractCatalog(AgentContext agentContext) {
    final var raw = agentContext.properties().get(SandboxGatewayToolHandler.PROPERTY_SANDBOX);
    if (raw == null) {
      return null;
    }
    try {
      final var state =
          raw instanceof SandboxState s ? s : objectMapper.convertValue(raw, SandboxState.class);
      return state.catalog();
    } catch (Exception e) {
      LOG.warn("Failed to read skill catalog from sandbox state: {}", e.getMessage());
      return null;
    }
  }

  private String buildSkillsCatalog(List<SkillCatalogEntry> catalog) {
    StringBuilder sb = new StringBuilder();
    sb.append("<available_skills>\n");
    sb.append(
        "The following skills are available in the sandbox workspace. Each skill is a directory"
            + " containing a SKILL.md plus optional bundled scripts and resources. To use a skill,"
            + " read its SKILL.md at the given location with sandbox_fs_read, then follow its"
            + " instructions. Read bundled files with sandbox_fs_read and run scripts with"
            + " sandbox_bash. Bundled resources are referenced by paths relative to the skill"
            + " directory (the directory that contains SKILL.md); resolve them against that"
            + " directory and use absolute paths in your tool calls.\n");
    for (SkillCatalogEntry entry : catalog) {
      String location = entry.location();
      int lastSlash = location.lastIndexOf('/');
      String directory = lastSlash > 0 ? location.substring(0, lastSlash) : location;
      sb.append("\n<skill name=\"")
          .append(entry.name())
          .append("\" location=\"")
          .append(location)
          .append("\" directory=\"")
          .append(directory)
          .append("\">\n")
          .append(entry.description())
          .append("\n</skill>");
    }
    sb.append("\n</available_skills>");
    return sb.toString();
  }
}
