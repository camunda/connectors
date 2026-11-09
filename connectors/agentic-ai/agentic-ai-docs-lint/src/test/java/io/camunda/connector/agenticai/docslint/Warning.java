/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.nio.file.Path;

/**
 * A remark that does not fail a check. A line of 0 means the remark has no position in the file.
 */
record Warning(Path file, int line, String what, String why, String fix) implements Finding {

  /** The remark as one line for the build log. */
  String logLine() {
    return "WARN " + position() + ": " + what;
  }

  @Override
  public String toString() {
    return position() + ": " + what + "\n    why: " + why + "\n    fix: " + fix;
  }

  private String position() {
    return line > 0 ? file + ":" + line : file.toString();
  }
}
