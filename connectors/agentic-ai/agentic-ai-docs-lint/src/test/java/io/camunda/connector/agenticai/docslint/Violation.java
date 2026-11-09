/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.nio.file.Path;

/**
 * A problem that fails a check. A line of 0 means the problem has no position in the file. The
 * message tells what is wrong, why it matters and what to verify.
 */
record Violation(Path file, int line, String what, String why, String fix) implements Finding {

  @Override
  public String toString() {
    final String position = line > 0 ? file + ":" + line : file.toString();
    return position + ": " + what + "\n    why: " + why + "\n    fix: " + fix;
  }
}
