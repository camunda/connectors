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
package io.camunda.connector.http.client.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class HttpHeaderFilenameResolverTest {
  private static final UUID defaultUuid = UUID.fromString("8d8b30e3-de52-4f1c-a71c-9905a8043dac");
  private static MockedStatic<UUID> mockedUuid;

  @BeforeAll()
  static void setupUUID() {
    mockedUuid = Mockito.mockStatic(UUID.class);
    mockedUuid.when(UUID::randomUUID).thenReturn(defaultUuid);
  }

  @AfterAll()
  static void tearDown() {
    mockedUuid.close();
  }

  private static Stream<Arguments> provideHeaders() {
    return Stream.of(
        Arguments.of(
            Map.of(
                "Content-Disposition",
                "attachment; filename=\"report\"",
                "Content-Type",
                "application/pdf"),
            "report.pdf"),
        Arguments.of(
            Map.of(
                "content-disposition",
                "attachment; filename=\"report\"",
                "content-type",
                "application/pdf"),
            "report.pdf"),
        Arguments.of(
            Map.of(
                "CONTENT-DISPOSITION",
                "attachment; filename=\"report\"",
                "CONTENT-TYPE",
                "application/pdf"),
            "report.pdf"),
        Arguments.of(
            Map.of(
                "Content-Disposition",
                "attachment; filename=report",
                "Content-Type",
                "application/pdf"),
            "report.pdf"),
        Arguments.of(
            Map.of(
                "Content-Disposition",
                "attachment; filename=report.png",
                "Content-Type",
                "application/pdf"),
            "report.png"),
        Arguments.of(Map.of("Content-Disposition", "attachment; filename=\"report\""), "report"),
        Arguments.of(
            Map.of("Content-Disposition", "attachment; filename=\"report.pdf\""), "report.pdf"),
        Arguments.of(Map.of("Content-Type", "text/csv"), defaultUuid.toString() + ".csv"),
        Arguments.of(Map.of(), defaultUuid.toString()),
        Arguments.of(
            Map.of(
                "Content-Disposition",
                "attachment; filename=\"report\"",
                "content-type",
                "image/png"),
            "report.png"),
        Arguments.of(Map.of("Content-Type", "image/svg+xml"), defaultUuid.toString() + ".svg"));
  }

  @ParameterizedTest
  @MethodSource("provideHeaders")
  void testGetFilename(Map<String, Object> headers, String expectedFilename) {
    assertThat(expectedFilename).isEqualTo(HttpHeaderFilenameResolver.getFilename(headers));
  }
}
