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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;

class CptReportExtensionTest {

  @TempDir Path reportDirectory;

  @Test
  void formatsJsonNumbersIndependentlyOfDefaultLocale() throws NoSuchMethodException {
    var context = mock(ExtensionContext.class);
    when(context.getTags()).thenReturn(Set.of("core-smoke"));
    doReturn(CptReportExtensionTest.class).when(context).getRequiredTestClass();
    when(context.getRequiredTestMethod())
        .thenReturn(
            CptReportExtensionTest.class.getDeclaredMethod(
                "formatsJsonNumbersIndependentlyOfDefaultLocale"));
    when(context.getDisplayName()).thenReturn("provider/model");

    var defaultLocale = Locale.getDefault();
    try {
      Locale.setDefault(Locale.GERMANY);

      var result = CptReportExtension.toJson(context, "passed", null, 1.234);

      assertThat(result).contains("\"durationSeconds\":1.234");
    } finally {
      Locale.setDefault(defaultLocale);
    }
  }

  @Test
  void recordsDisabledMethodsAsSkipped() throws NoSuchMethodException, IOException {
    var context = mockContext("recordsDisabledMethodsAsSkipped", "disabled scenario");
    var previousReportDirectory = System.getProperty(CptReportExtension.REPORT_DIRECTORY_PROPERTY);
    try {
      System.setProperty(CptReportExtension.REPORT_DIRECTORY_PROPERTY, reportDirectory.toString());

      new CptReportExtension().testDisabled(context, Optional.of("not implemented"));

      final Path reportPath;
      try (var reports = Files.list(reportDirectory)) {
        reportPath =
            reports
                .filter(path -> path.getFileName().toString().endsWith(".jsonl"))
                .findFirst()
                .orElseThrow();
      }
      var report = Files.readString(reportPath);
      assertThat(report)
          .contains("\"method\":\"recordsDisabledMethodsAsSkipped\"")
          .contains("\"status\":\"skipped\"")
          .contains("\"durationSeconds\":0.000");
    } finally {
      if (previousReportDirectory == null) {
        System.clearProperty(CptReportExtension.REPORT_DIRECTORY_PROPERTY);
      } else {
        System.setProperty(CptReportExtension.REPORT_DIRECTORY_PROPERTY, previousReportDirectory);
      }
    }
  }

  private ExtensionContext mockContext(String methodName, String displayName)
      throws NoSuchMethodException {
    var context = mock(ExtensionContext.class);
    var method = CptReportExtensionTest.class.getDeclaredMethod(methodName);
    when(context.getTags()).thenReturn(Set.of("core-smoke"));
    when(context.getTestMethod()).thenReturn(Optional.of(method));
    doReturn(CptReportExtensionTest.class).when(context).getRequiredTestClass();
    when(context.getRequiredTestMethod()).thenReturn(method);
    when(context.getDisplayName()).thenReturn(displayName);
    return context;
  }
}
