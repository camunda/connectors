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

  private ReportPrinter() {}

  public static void print(List<Finding> findings, int filesScanned, PrintStream out) {
    Map<Path, List<Finding>> byFile = new TreeMap<>();
    for (Finding f : findings) {
      byFile.computeIfAbsent(f.file(), k -> new ArrayList<>()).add(f);
    }

    for (Map.Entry<Path, List<Finding>> e : byFile.entrySet()) {
      out.println(e.getKey());
      for (Finding f : e.getValue()) {
        out.printf("  %-5s  %-32s  %s%n", f.severity(), f.ruleId(), f.jsonPointer());
        out.printf("         %s%n", f.message());
      }
      out.println();
    }

    if (findings.isEmpty()) {
      out.printf("Scanned %d template(s). No findings.%n", filesScanned);
    } else {
      out.printf(
          "Scanned %d template(s). %d finding(s) in %d file(s). Run failed.%n",
          filesScanned, findings.size(), byFile.size());
    }
  }
}
