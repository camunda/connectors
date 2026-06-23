/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.List;

/**
 * An {@link InternalToolHandler} whose tool definition depends on the skills configured for the
 * agent. Implemented by {@code load_skill}, which constrains its {@code name} parameter to the set
 * of configured skill names (a JSON-schema {@code enum}) so the model cannot hallucinate
 * nonexistent skills.
 *
 * <p>The skill-aware definition is built once at agent initialization (when the configured skills
 * are known) and frozen into the agent context tool definitions alongside the ad-hoc tools — it is
 * NOT recomputed per invocation. See {@link InternalToolRegistry#toolDefinitions(List)}.
 */
public interface SkillAwareInternalToolHandler extends InternalToolHandler {

  /**
   * Builds the tool definition specialized to the given configured skill names. When {@code
   * skillNames} is empty, the definition is equivalent to the generic {@link #definition()} (no
   * enum constraint).
   *
   * @param skillNames the names of the skills configured for the agent; never {@code null}
   * @return the specialized tool definition
   */
  ToolDefinition definition(List<String> skillNames);
}
