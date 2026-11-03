/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.managedscript.api.ErrorCode;
import io.camunda.connector.managedscript.api.ScriptLanguage;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class IncidentMessageTest {

  private static final String SECRET = "s3cr3t-value-0123456789";

  @Test
  void isTheCodeAndTheMessageWithoutAStackTrace() {
    assertThat(IncidentMessage.format(ErrorCode.TIMEOUT, "script exceeded 30000 ms", null, null))
        .isEqualTo("TIMEOUT: script exceeded 30000 ms");
  }

  @Test
  void appendsAShortStackTraceWhole() {
    assertThat(
            IncidentMessage.format(
                ErrorCode.SCRIPT_ERROR, "Error: boom", "Error: boom\n    at execute", null))
        .isEqualTo("SCRIPT_ERROR: Error: boom\nError: boom\n    at execute");
  }

  @Test
  void keepsWholeLinesAtTheEndOfALongPythonTraceback() {
    final var traceback =
        "Traceback (most recent call last):\n"
            + "  wrapper frame\n".repeat(1_000)
            + "ValueError: x";

    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR, "ValueError: x", traceback, ScriptLanguage.PYTHON);

    assertThat(text).hasSizeLessThanOrEqualTo(IncidentMessage.MAX_LENGTH);
    assertThat(text).startsWith("SCRIPT_ERROR: ValueError: x\n" + IncidentMessage.TRUNCATED + "\n");
    assertThat(text).endsWith("ValueError: x");
    assertThat(traceLines(text)).allMatch(lines(traceback)::contains);
  }

  @Test
  void keepsWholeLinesAtTheStartOfALongJavaScriptStack() {
    final var stack =
        "Error: boom\n    at execute (user_script.mjs:2:9)\n" + "    at frame\n".repeat(1_000);

    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR, "Error: boom", stack, ScriptLanguage.JAVASCRIPT);

    assertThat(text).hasSizeLessThanOrEqualTo(IncidentMessage.MAX_LENGTH);
    assertThat(text)
        .startsWith("SCRIPT_ERROR: Error: boom\nError: boom\n    at execute (user_script.mjs:2:9)");
    assertThat(text).endsWith("\n" + IncidentMessage.TRUNCATED);
    assertThat(traceLines(text)).allMatch(lines(stack)::contains);
  }

  @Test
  void neverKeepsPartOfASecretAcrossTheCutOfAStackTrace() {
    for (var language : ScriptLanguage.values()) {
      for (int offset = 0; offset < 40; offset++) {
        final var filler = "x".repeat(offset);
        final var stack =
            (filler + "\n" + ("frame " + SECRET + " frame\n").repeat(400) + "last").strip();

        final var text = IncidentMessage.format(ErrorCode.SCRIPT_ERROR, "m", stack, language);

        assertWholeOrAbsent(text);
      }
    }
  }

  @Test
  void neverKeepsPartOfASecretAcrossTheCutOfTheMessage() {
    for (int offset = 980; offset < 1_030; offset++) {
      final var message = "w ".repeat(offset / 2) + SECRET + " end";

      final var text = IncidentMessage.format(ErrorCode.SCRIPT_ERROR, message, null, null);

      assertThat(text.length() - "SCRIPT_ERROR: ".length())
          .isLessThanOrEqualTo(IncidentMessage.MAX_MESSAGE_LENGTH);
      assertWholeOrAbsent(text);
    }
  }

  @Test
  void dropsAMessageWithoutAWordBreakBeforeTheLimit() {
    final var text = IncidentMessage.format(ErrorCode.SCRIPT_ERROR, "m".repeat(5_000), null, null);

    assertThat(text).isEqualTo("SCRIPT_ERROR: " + IncidentMessage.TRUNCATED);
  }

  @Test
  void hasADefaultMessage() {
    assertThat(IncidentMessage.format(ErrorCode.SCRIPT_ERROR, null, null, null))
        .isEqualTo("SCRIPT_ERROR: Managed script failed");
  }

  @Test
  void appendsShortLogsWholeAfterTheStackTrace() {
    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR,
            "KeyError: 'total'",
            "Traceback\nKeyError: 'total'",
            ScriptLanguage.PYTHON,
            "loaded 3 order lines\n",
            false);

    assertThat(text)
        .isEqualTo(
            "SCRIPT_ERROR: KeyError: 'total'\nTraceback\nKeyError: 'total'\n"
                + IncidentMessage.LOGS_HEADER
                + "\nloaded 3 order lines\n");
  }

  @Test
  void appendsLogsAfterTheMessageWhenThereIsNoStackTrace() {
    final var text =
        IncidentMessage.format(
            ErrorCode.TIMEOUT, "script exceeded 30000 ms", null, null, "step 1\nstep 2", false);

    assertThat(text)
        .isEqualTo(
            "TIMEOUT: script exceeded 30000 ms\n"
                + IncidentMessage.LOGS_HEADER
                + "\nstep 1\nstep 2");
  }

  @Test
  void appendsNothingForEmptyLogs() {
    for (var logs : new String[] {null, "", " \n "}) {
      assertThat(IncidentMessage.format(ErrorCode.TIMEOUT, "late", null, null, logs, false))
          .isEqualTo("TIMEOUT: late");
    }
  }

  @Test
  void keepsWholeLinesAtTheEndOfLongLogs() {
    final var logs = new StringBuilder();
    for (int i = 1; i <= 1_000; i++) {
      logs.append("line ").append(i).append(" of the script output\n");
    }
    logs.append("last");

    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR,
            "ValueError: x",
            "ValueError: x",
            ScriptLanguage.PYTHON,
            logs.toString(),
            false);

    assertThat(text).hasSizeLessThanOrEqualTo(IncidentMessage.MAX_LENGTH);
    assertThat(text)
        .startsWith(
            "SCRIPT_ERROR: ValueError: x\nValueError: x\n"
                + IncidentMessage.LOGS_HEADER
                + "\n"
                + IncidentMessage.TRUNCATED
                + "\n");
    assertThat(text).endsWith("line 1000 of the script output\nlast");
    assertThat(logLines(text)).allMatch(lines(logs.toString())::contains);
  }

  @Test
  void givesTheLogsOnlyTheRoomThatTheStackTraceLeaves() {
    final var traceback = "Traceback\n" + "  frame\n".repeat(1_000) + "ValueError: x";

    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR,
            "ValueError: x",
            traceback,
            ScriptLanguage.PYTHON,
            "the logs\n".repeat(10),
            false);

    assertThat(text)
        .isEqualTo(
            IncidentMessage.format(
                ErrorCode.SCRIPT_ERROR, "ValueError: x", traceback, ScriptLanguage.PYTHON));
  }

  @Test
  void dropsTheFirstLineOfLogsThatTheExecutorCut() {
    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR, "m", null, null, "rt of a line\nwhole line\nlast", true);

    assertThat(text)
        .isEqualTo(
            "SCRIPT_ERROR: m\n"
                + IncidentMessage.LOGS_HEADER
                + "\n"
                + IncidentMessage.TRUNCATED
                + "\nwhole line\nlast");
  }

  @Test
  void leavesOutEachLineOfTheLogsWithAControlCharacterOtherThanACarriageReturnOrATab() {
    final var lines =
        new String[] {
          "a\u001b[31mred",
          "nul\u0000",
          "next\u0085line",
          "del\u007f",
          "line\u2028separator",
          "bidi\u202eoverride",
          "zero\u200bwidth",
          "crlf\r",
          "\tindented"
        };

    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR, "m", null, null, String.join("\n", lines), false);

    assertThat(text)
        .isEqualTo(
            "SCRIPT_ERROR: m\n"
                + IncidentMessage.LOGS_HEADER
                + ("\n" + IncidentMessage.CONTROL_LINE).repeat(lines.length - 2)
                + "\ncrlf\r\n\tindented");
  }

  @Test
  void neverKeepsASecretWithAControlCharacterWithoutIt() {
    for (var secret :
        new String[] {
          SECRET + "\r", "\ufeff" + SECRET, "s3cr3t\u200b-value-0123456789", "user:pass\r\ntok3n"
        }) {
      final var text =
          IncidentMessage.format(
              ErrorCode.SCRIPT_ERROR, "m", null, null, "before\n" + secret + "\r\nafter", false);

      if (!text.contains(secret)) {
        assertThat(text)
            .doesNotContain("s3cr3t")
            .doesNotContain("0123456789")
            .doesNotContain("tok3n");
      }
      assertThat(text).endsWith("\nafter");
    }
  }

  @Test
  void keepsASecretWithCrlfLineEndingsWhole() {
    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR, "m", null, null, "before\nuser:pass\r\ntok3n\nafter", false);

    assertThat(text).contains("user:pass\r\ntok3n");
  }

  @Test
  void leavesOutEachLineOfTheMessageAndTheStackTraceWithAControlCharacter() {
    final var text =
        IncidentMessage.format(
            ErrorCode.SCRIPT_ERROR,
            "boom\u001b[2J",
            "Traceback\nline\u202eevil\nValueError: boom",
            ScriptLanguage.PYTHON);

    assertThat(text)
        .isEqualTo(
            "SCRIPT_ERROR: "
                + IncidentMessage.CONTROL_LINE
                + "\nTraceback\n"
                + IncidentMessage.CONTROL_LINE
                + "\nValueError: boom");
  }

  @Test
  void neverKeepsPartOfASecretAcrossTheCutOfTheLogs() {
    for (var truncated : new boolean[] {false, true}) {
      for (int offset = 0; offset < 40; offset++) {
        final var logs =
            ("x".repeat(offset) + "\n" + ("printed " + SECRET + " here\n").repeat(400) + "last")
                .strip();

        final var text =
            IncidentMessage.format(
                ErrorCode.SCRIPT_ERROR, "m", "y".repeat(offset + 1), null, logs, truncated);

        assertWholeOrAbsent(text);
      }
    }
  }

  @Test
  void neverKeepsPartOfASecretThatTheExecutorCutAtTheStartOfTheLogs() {
    for (int offset = 1; offset < SECRET.length(); offset++) {
      final var text =
          IncidentMessage.format(
              ErrorCode.SCRIPT_ERROR, "m", null, null, SECRET.substring(offset) + "\nlast", true);

      assertWholeOrAbsent(text);
    }
  }

  private static List<String> logLines(String text) {
    final var logs = text.substring(text.indexOf(IncidentMessage.LOGS_HEADER));
    return Arrays.stream(logs.split("\n"))
        .skip(1)
        .filter(line -> !line.equals(IncidentMessage.TRUNCATED))
        .toList();
  }

  /** The secret is either kept whole, so that the runtime can mask it, or not at all. */
  private static void assertWholeOrAbsent(String text) {
    final var whole = text.split(Pattern.quote(SECRET), -1);
    final var rest = String.join("", whole);
    for (int length = 4; length < SECRET.length(); length++) {
      assertThat(rest).doesNotContain(SECRET.substring(0, length) + "\n");
      assertThat(rest).doesNotContain("\n" + SECRET.substring(SECRET.length() - length));
      assertThat(rest).doesNotEndWith(SECRET.substring(0, length));
    }
    assertThat(rest).doesNotContain("s3cr3t").doesNotContain("0123456789");
  }

  private static List<String> traceLines(String text) {
    return Arrays.stream(text.split("\n"))
        .skip(1)
        .filter(line -> !line.equals(IncidentMessage.TRUNCATED))
        .toList();
  }

  private static List<String> lines(String trace) {
    return Arrays.asList(trace.split("\n"));
  }
}
