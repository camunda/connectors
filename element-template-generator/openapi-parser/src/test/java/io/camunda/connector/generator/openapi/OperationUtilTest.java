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
import org.junit.jupiter.api.Test;
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

  /**
   * Regression / safety test. When {@code --no-resolve-refs} is active the swagger-parser leaves
   * external {@code $ref} values untouched. Downstream code ({@link
   * OperationUtil#extractOperations} wraps each operation in a try-catch, so the operation must be
   * reported as <em>unsupported</em> with a meaningful reason rather than propagating an unhandled
   * NPE or silent {@code null}.
   */
  @Test
  void extractOperations_specWithExternalRef_noResolve_operationMarkedUnsupported() {
    // language=yaml — the requestBody schema is an unresolved external $ref
    // language=yaml
    var specWithExternalRef =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com/v1
        paths:
          /users:
            post:
              operationId: createUser
              requestBody:
                required: true
                content:
                  application/json:
                    schema:
                      $ref: './user-schema.json'
              responses:
                '201':
                  description: Created
          /health:
            get:
              operationId: healthCheck
              responses:
                '200':
                  description: OK
        """;

    // Parse with --no-resolve-refs so external $refs are left unresolved
    var source = new OpenApiGenerationSource(List.of(specWithExternalRef, "--no-resolve-refs"));

    // when
    var results =
        OperationUtil.extractOperations(
            source.openAPI(), source.includeOperations(), source.options());

    // then – both operations are returned (not an exception), but the one with the external
    // $ref is marked unsupported while the plain GET is supported
    assertThat(results).hasSize(2);

    var createUser = results.stream().filter(r -> "createUser".equals(r.id())).findFirst();
    assertThat(createUser).isPresent();
    assertThat(createUser.get().supported())
        .as("createUser references an unresolved external $ref and must be unsupported")
        .isFalse();
    assertThat(createUser.get().info())
        .as("reason must mention the unresolved external $ref")
        .containsIgnoringCase("External $ref");

    var healthCheck = results.stream().filter(r -> "healthCheck".equals(r.id())).findFirst();
    assertThat(healthCheck).isPresent();
    assertThat(healthCheck.get().supported())
        .as("healthCheck has no external $ref and must still be supported")
        .isTrue();
  }
}
