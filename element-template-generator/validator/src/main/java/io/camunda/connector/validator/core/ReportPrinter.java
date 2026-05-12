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
package io.camunda.connector.validator.core;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class ReportPrinter {

  private static final String RESET = "\033[0m";
  private static final String BOLD = "\033[1m";
  private static final String RED = "\033[31m";
  private static final String YELLOW = "\033[33m";
  private static final String CYAN = "\033[36m";
  private static final String GREEN = "\033[32m";

  private ReportPrinter() {}

  /**
   * Prints the validation report. Color is enabled when a real console is attached; pass {@code
   * colorEnabled = false} to suppress (e.g. via {@code --no-color}).
   */
  public static void print(
      List<Finding> findings, int filesScanned, PrintStream out, boolean colorEnabled) {
    Map<Path, List<Finding>> byFile = new TreeMap<>();
    for (Finding f : findings) {
      byFile.computeIfAbsent(f.file(), k -> new ArrayList<>()).add(f);
    }

    for (Map.Entry<Path, List<Finding>> e : byFile.entrySet()) {
      out.println(color(BOLD, e.getKey().toString(), colorEnabled));
      for (Finding f : e.getValue()) {
        String severity = colorBySeverity(f.severity(), colorEnabled);
        String ruleId = color(CYAN, f.ruleId(), colorEnabled);
        out.printf("  %-5s  %-40s  %s%n", severity, ruleId, f.jsonPointer());
        out.printf("         %s%n", f.message());
      }
      out.println();
    }

    if (findings.isEmpty()) {
      out.println(
          color(GREEN, "Scanned " + filesScanned + " template(s). No findings.", colorEnabled));
    } else {
      out.println(
          color(
              RED,
              "Scanned "
                  + filesScanned
                  + " template(s). "
                  + findings.size()
                  + " finding(s) in "
                  + byFile.size()
                  + " file(s). Run failed.",
              colorEnabled));
    }
  }

  /** Convenience overload — auto-detects color support via {@code System.console()}. */
  public static void print(List<Finding> findings, int filesScanned, PrintStream out) {
    print(findings, filesScanned, out, System.console() != null);
  }

  private static String colorBySeverity(Severity severity, boolean colorEnabled) {
    String label = severity.name();
    return switch (severity) {
      case ERROR -> color(RED, label, colorEnabled);
      case WARN -> color(YELLOW, label, colorEnabled);
    };
  }

  private static String color(String ansi, String text, boolean colorEnabled) {
    return colorEnabled ? ansi + text + RESET : text;
  }
}
