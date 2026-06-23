/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptContributor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contributes a Tier-1 skills catalog to the system prompt when skills are configured and a sandbox
 * is present.
 *
 * <p>Emits an {@code <available_skills>} XML block listing each skill's name and description. The
 * workspace location is intentionally NOT advertised here: this contributor runs while composing
 * the system prompt, before any sandbox session exists, so the writable base directory is not yet
 * known. The {@code load_skill} tool reports the exact materialized location in its result. The
 * full SKILL.md body is likewise omitted here (Tier-2) — it is returned by {@code load_skill} on
 * demand.
 *
 * <p>Returns {@code null} (no contribution) when:
 *
 * <ul>
 *   <li>No sandbox is configured (skills can't be used without a sandbox)
 *   <li>No skills are configured on the agent
 *   <li>All configured skill documents fail to resolve
 * </ul>
 *
 * <p>Order {@value ORDER} places this contributor before the A2A contributor (order 100).
 */
public class SkillsSystemPromptContributor implements SystemPromptContributor {

  private static final Logger LOGGER = LoggerFactory.getLogger(SkillsSystemPromptContributor.class);

  /** Order value — runs before A2A contributor (order 100). */
  public static final int ORDER = 90;

  private final SkillResolver skillResolver;

  public SkillsSystemPromptContributor(SkillResolver skillResolver) {
    this.skillResolver = skillResolver;
  }

  @Override
  public String contribute(AgentExecutionContext executionContext, AgentContext agentContext) {
    // Skills require a sandbox — without one there is no workspace to load into
    if (executionContext.configuration().sandboxConfiguration().isEmpty()) {
      LOGGER.debug("No sandbox configured, skipping skills catalog contribution");
      return null;
    }

    var skillDocs = executionContext.configuration().skills();
    if (skillDocs == null || skillDocs.isEmpty()) {
      LOGGER.debug("No skills configured, skipping skills catalog contribution");
      return null;
    }

    List<Skill> skills;
    try {
      skills = skillResolver.resolve(skillDocs);
    } catch (Exception e) {
      LOGGER.warn(
          "Unexpected error resolving skills for system prompt contribution: {}",
          e.getMessage(),
          e);
      return null;
    }

    if (skills.isEmpty()) {
      LOGGER.debug(
          "All configured skill documents failed to resolve, skipping skills catalog contribution");
      return null;
    }

    LOGGER.debug("Contributing skills catalog with {} skill(s) to system prompt", skills.size());
    return buildSkillsCatalog(skills);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  private String buildSkillsCatalog(List<Skill> skills) {
    var sb = new StringBuilder();
    sb.append("<available_skills>\n");
    sb.append(
        "The following skills are available. Each provides instructions and (optionally) bundled"
            + " scripts/resources. To use a skill, call the load_skill tool with its name; this"
            + " materializes the skill into the workspace and returns its instructions. Then read"
            + " bundled files with fs_read and run scripts with bash.\n");
    sb.append("\n");

    for (Skill skill : skills) {
      sb.append("<skill name=\"").append(skill.name()).append("\">\n");
      sb.append(skill.description()).append("\n");
      sb.append("</skill>\n");
    }

    sb.append("</available_skills>");
    return sb.toString();
  }
}
