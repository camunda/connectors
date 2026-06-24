/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

/**
 * Thrown when a skill bundle or its {@code SKILL.md} file cannot be parsed into a valid {@link
 * Skill}. Callers (e.g. {@link SkillResolver}) should catch this, log a warning, and skip the
 * offending skill so that other skills in the same batch are unaffected.
 */
public class InvalidSkillException extends RuntimeException {

  public InvalidSkillException(String message) {
    super(message);
  }

  public InvalidSkillException(String message, Throwable cause) {
    super(message, cause);
  }
}
