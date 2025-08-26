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
package io.camunda.connector.generator.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.http.OperationParseResult;
import io.camunda.connector.generator.openapi.util.OperationUtil;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class OperationUtilTest {

  static Stream<Arguments> getMultipartHeaderTestData() {
    return Stream.of(
        Arguments.of("multipart/form-data", true), Arguments.of("application/json", false));
  }

  @ParameterizedTest
  @MethodSource("getMultipartHeaderTestData")
  void shouldAddMultipartHeader_WhenMultipartBody(String type, boolean expectHeaderIsAdded) {
    // given
    var source =
        "openapi: 3.0.0\n"
            + "info:\n"
            + "  title: test\n"
            + "  version: 1.0.0\n"
            + "servers:\n"
            + "  - url: https://camunda.proxy.beeceptor.com\n"
            + "paths:\n"
            + "  /mypath:\n"
            + "    post:\n"
            + "      summary: Uploads a profile image\n"
            + "      requestBody:\n"
            + "        content:\n"
            + "          "
            + type
            + ":\n"
            + "            schema:\n"
            + "              type: object\n"
            + "              properties:\n"
            + "                id:\n"
            + "                  type: string\n"
            + "                  format: uuid\n"
            + "                profileImage:\n"
            + "                  type: string\n"
            + "                  format: binary\n"
            + "      responses:\n"
            + "        '200': { description: OK }";
    var input = new OpenApiGenerationSource(List.of(source));

    List<OperationParseResult> operationParseResults =
        OperationUtil.extractOperations(
            input.openAPI(), input.includeOperations(), input.options());

    assertThat(operationParseResults).hasSize(1);

    boolean hasMatch =
        operationParseResults.getFirst().builder().getProperties().stream()
            .anyMatch(
                p -> p.id().equals("Content-Type") && p.example().equals("multipart/form-data"));

    assertThat(hasMatch).isEqualTo(expectHeaderIsAdded);
  }
}
