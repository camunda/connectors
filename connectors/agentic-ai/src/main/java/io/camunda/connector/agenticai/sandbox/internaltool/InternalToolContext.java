/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import io.camunda.connector.agenticai.sandbox.skill.Skill;
import java.util.List;

/**
 * Per-invocation context carried through the internal-tool execution path. Holds data that is
 * resolved once per agent invocation (outside the internal sub-loop) and handed to every {@link
 * InternalToolHandler} on each call.
 *
 * <p>Currently only carries the resolved skill list, but is intentionally kept as a record so
 * additional per-invocation context can be added later without changing every handler signature.
 *
 * @param skills the skills available to the agent for this invocation; may be empty but never null
 */
public record InternalToolContext(List<Skill> skills) {

  /** Returns an empty context with no skills. */
  public static InternalToolContext empty() {
    return new InternalToolContext(List.of());
  }
}
