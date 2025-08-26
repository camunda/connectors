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

import static io.camunda.connector.generator.openapi.util.SecurityUtil.parseAuthentication;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.http.HttpAuthentication;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SecurityUtilTest {

  void assertThatDefaultAuthSectionIsUsed(List<HttpAuthentication> auth) {
    assertThat(auth).hasSize(5);
  }

  @Test
  void shouldHandleNoAuthCorrectly() {
    // language=yaml
    String yaml =
        """
      openapi: 3.0.0
      info:
        title: test
        version: 1.0.0
      servers:
        - url: https://example.com
      paths:
        /health:
          get:
            summary: Health check, no auth
            responses:
              '200': { description: OK }
      """;

    SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, null);
    OpenAPI openAPI = result.getOpenAPI();

    var authHealthPath =
        parseAuthentication(
            openAPI.getPaths().get("/health").getGet().getSecurity(), openAPI.getComponents());
    var authGlobal = parseAuthentication(openAPI.getSecurity(), openAPI.getComponents());

    assertThatDefaultAuthSectionIsUsed(authHealthPath);
    assertThatDefaultAuthSectionIsUsed(authGlobal);
  }

  @Test
  void shouldHandleOperationAuthCorrectly() {
    // language=yaml
    String yaml =
        """
      openapi: 3.0.0
      info:
        title: test
        version: 1.0.0
      servers:
        - url: https://example.com
      paths:
        /secure:
          get:
            summary: Returns a greeting
            security:
              - bearerAuth: []
            responses:
              '200': { description: OK }
      components:
        securitySchemes:
          bearerAuth:
            type: http
            scheme: bearer
            bearerFormat: JWT
      """;

    SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, null);
    OpenAPI openAPI = result.getOpenAPI();

    var authSecurePath =
        parseAuthentication(
            openAPI.getPaths().get("/secure").getGet().getSecurity(), openAPI.getComponents());
    var authGlobal = parseAuthentication(openAPI.getSecurity(), openAPI.getComponents());

    assertThat(authSecurePath).hasSize(1);
    assertThat(authGlobal).hasSize(0);
  }

  @Test
  void shouldHandleGlobalAuthCorrectly() {
    // language=yaml
    String yaml =
        """
      openapi: 3.0.0
      info:
        title: test
        version: 1.0.0
      servers:
        - url: https://example.com
      paths:
        /secure:
          get:
            summary: Returns a greeting
            responses:
              '200': { description: OK }
      security:
        - bearerAuth: []
      components:
        securitySchemes:
          bearerAuth:
            type: http
            scheme: bearer
            bearerFormat: JWT
      """;

    SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, null);
    OpenAPI openAPI = result.getOpenAPI();

    var authSecurePath =
        parseAuthentication(
            openAPI.getPaths().get("/secure").getGet().getSecurity(), openAPI.getComponents());
    var authGlobal = parseAuthentication(openAPI.getSecurity(), openAPI.getComponents());

    assertThat(authSecurePath).hasSize(0);
    assertThat(authGlobal).hasSize(1);
  }

  @Test
  void shouldHandleAuthAndNoAuthMixedCorrectly() {
    // language=yaml
    String yaml =
        """
      openapi: 3.0.0
      info:
        title: test
        version: 1.0.0
      servers:
        - url: https://example.com
      components:
        securitySchemes:
          bearerAuth:
            type: http
            scheme: bearer
      security:
        - bearerAuth: []
      paths:
        /secure:
          get:
            summary: Needs token
            responses:
              '200': { description: OK }
        /health:
          get:
            summary: Health check, no auth
            security: []
            responses:
              '200': { description: OK }
      """;

    SwaggerParseResult result = new OpenAPIV3Parser().readContents(yaml, null, null);
    OpenAPI openAPI = result.getOpenAPI();

    var authSecurePath =
        parseAuthentication(
            openAPI.getPaths().get("/secure").getGet().getSecurity(), openAPI.getComponents());
    var authHealthPath =
        parseAuthentication(
            openAPI.getPaths().get("/health").getGet().getSecurity(), openAPI.getComponents());
    var authGlobal = parseAuthentication(openAPI.getSecurity(), openAPI.getComponents());

    assertThat(authSecurePath).hasSize(0); // empty value will be overwritten by global
    assertThatDefaultAuthSectionIsUsed(authHealthPath);
    assertThat(authGlobal).hasSize(1);
  }
}
