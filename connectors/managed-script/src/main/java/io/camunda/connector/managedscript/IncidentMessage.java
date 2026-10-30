/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Builds the error message of a failed job, which is also the incident message: {@code <code>:
 * <message>}, then the stack trace of the script when there is one.
 *
 * <p>The runtime masks resolved secrets in this text, but only whole ones. Cuts therefore fall
 * between words of the message and between lines of the stack trace, so that a secret is either
 * kept whole or left out, never kept in part.
 */
final class IncidentMessage {

  /**
   * The runtime cuts longer error messages from the end, which would drop the part of a Python
   * traceback nearest the error ({@code SpringConnectorJobHandler.MAX_ERROR_MESSAGE_LENGTH}).
   */
  static final int MAX_LENGTH = 6_000;

  /** The length of {@code error.message} in the execution contract. */
  static final int MAX_MESSAGE_LENGTH = 1_024;

  static final String TRUNCATED = "... (truncated)";

  private IncidentMessage() {}

  /**
   * Keeps the code whole, cuts the message between words, and cuts the stack trace between lines:
   * keeping its end for Python, whose tracebacks end with the failing frame, and its start for
   * JavaScript, whose stacks start with it.
   *
   * @param language {@code null} when the failure happened before the language was known
   */
  static String format(ErrorCode code, String message, String stackTrace, ScriptLanguage language) {
    final var head =
        code.name()
            + ": "
            + (message == null || message.isBlank() ? "Managed script failed" : bounded(message));
    if (stackTrace == null || stackTrace.isBlank()) {
      return head;
    }
    final var budget = MAX_LENGTH - head.length() - 1;
    if (stackTrace.length() <= budget) {
      return head + "\n" + stackTrace;
    }
    final var lines = stackTrace.split("\n", -1);
    final var room = budget - TRUNCATED.length() - 1;
    final Deque<String> kept = new ArrayDeque<>();
    int size = 0;
    final boolean keepEnd = language == ScriptLanguage.PYTHON;
    for (int i = 0; i < lines.length; i++) {
      final var line = lines[keepEnd ? lines.length - 1 - i : i];
      final var added = line.length() + (kept.isEmpty() ? 0 : 1);
      if (size + added > room) {
        break;
      }
      if (keepEnd) {
        kept.addFirst(line);
      } else {
        kept.addLast(line);
      }
      size += added;
    }
    if (kept.isEmpty()) {
      return head + "\n" + TRUNCATED;
    }
    final var trace = String.join("\n", kept);
    return keepEnd
        ? head + "\n" + TRUNCATED + "\n" + trace
        : head + "\n" + trace + "\n" + TRUNCATED;
  }

  /** The message within {@link #MAX_MESSAGE_LENGTH}, cut after its last whole word. */
  private static String bounded(String message) {
    if (message.length() <= MAX_MESSAGE_LENGTH) {
      return message;
    }
    final var limit = MAX_MESSAGE_LENGTH - TRUNCATED.length() - 1;
    int cut = limit;
    while (cut > 0 && !Character.isWhitespace(message.charAt(cut))) {
      cut--;
    }
    return cut == 0 ? TRUNCATED : message.substring(0, cut) + " " + TRUNCATED;
  }
}
