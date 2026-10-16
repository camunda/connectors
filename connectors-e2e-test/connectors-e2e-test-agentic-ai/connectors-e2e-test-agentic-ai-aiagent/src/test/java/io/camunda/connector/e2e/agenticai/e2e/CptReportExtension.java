/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

public final class CptReportExtension
    implements BeforeEachCallback, AfterEachCallback, TestWatcher {

  static final String REPORT_DIRECTORY_PROPERTY = "cpt.report.directory";
  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(CptReportExtension.class);
  private static final String START_NANOS = "startNanos";

  @Override
  public void beforeEach(ExtensionContext context) {
    context.getStore(NAMESPACE).put(START_NANOS, System.nanoTime());
  }

  @Override
  public void afterEach(ExtensionContext context) {
    var cause = context.getExecutionException().orElse(null);
    var status =
        cause == null
            ? "passed"
            : (cause.getClass().getName().equals("org.opentest4j.TestAbortedException")
                ? "skipped"
                : "failed");
    write(context, status, cause, durationSeconds(context));
  }

  @Override
  public void testDisabled(ExtensionContext context, Optional<String> reason) {
    if (context.getTestMethod().isPresent()) {
      write(context, "skipped", null, 0);
    }
  }

  private static synchronized void write(
      ExtensionContext context, String status, Throwable cause, double durationSeconds) {
    var reportDirectory = System.getProperty(REPORT_DIRECTORY_PROPERTY);
    if (reportDirectory == null || reportDirectory.isBlank()) {
      return;
    }

    var outputDirectory = Path.of(reportDirectory);
    var outputFile =
        outputDirectory.resolve("cpt-results-" + ProcessHandle.current().pid() + ".jsonl");
    try {
      Files.createDirectories(outputDirectory);
      Files.writeString(
          outputFile,
          toJson(context, status, cause, durationSeconds) + System.lineSeparator(),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException error) {
      System.err.printf("Failed to write CPT result to %s: %s%n", outputFile, error.getMessage());
    }
  }

  static String toJson(
      ExtensionContext context, String status, Throwable cause, double durationSeconds) {
    var tags = context.getTags().stream().sorted(Comparator.naturalOrder()).toList();
    var failure = cause == null ? "" : cause.getClass().getSimpleName();

    return String.format(
        Locale.ROOT,
        """
            {"class":"%s","method":"%s","displayName":"%s","tags":"%s","status":"%s","durationSeconds":%.3f,"failureClass":"%s"}\
            """,
        jsonEscape(context.getRequiredTestClass().getSimpleName()),
        jsonEscape(context.getRequiredTestMethod().getName()),
        jsonEscape(context.getDisplayName()),
        jsonEscape(String.join(", ", tags)),
        status,
        durationSeconds,
        jsonEscape(failure));
  }

  private static double durationSeconds(ExtensionContext context) {
    var started = context.getStore(NAMESPACE).remove(START_NANOS, Long.class);
    return started == null ? 0 : (System.nanoTime() - started) / 1_000_000_000.0;
  }

  private static String jsonEscape(String value) {
    var escaped = new StringBuilder(value.length());
    for (var character : value.toCharArray()) {
      switch (character) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append("\\u%04x".formatted((int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
