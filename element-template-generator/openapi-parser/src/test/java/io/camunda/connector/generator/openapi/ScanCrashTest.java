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

import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.http.HttpAuthentication;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.generator.dsl.http.OperationParseResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Specs that used to fail the whole {@code congen scan openapi-outbound} run (issue #10767). Each
 * spec reproduces one crash in a minimal form.
 */
class ScanCrashTest {

  private final OpenApiOutboundTemplateGenerator generator = new OpenApiOutboundTemplateGenerator();

  @SuppressWarnings("unchecked")
  private List<OperationParseResult> scanOperations(String spec) {
    var scanResult = generator.scan(new OpenApiGenerationSource(List.of(spec)));
    return (List<OperationParseResult>) scanResult.additionalData();
  }

  private ElementTemplate generate(String spec) {
    var templates =
        generator.generate(
            new OpenApiGenerationSource(List.of(spec)), GeneratorConfiguration.DEFAULT);
    assertThat(templates).hasSize(1);
    return templates.getFirst();
  }

  private static OperationParseResult operation(List<OperationParseResult> results, String id) {
    return results.stream().filter(r -> id.equals(r.id())).findFirst().orElseThrow();
  }

  private static List<Property> propertiesWithIdEndingIn(ElementTemplate template, String suffix) {
    return template.properties().stream()
        .filter(p -> p.getId() != null && p.getId().endsWith(suffix))
        .toList();
  }

  @Test
  void sameSecuritySchemeInTwoOperationRequirements_isAddedOnce() {
    // Mailgun POST /v1/analytics/logs: security: [{basicAuth: []}, {basicAuth: []}]
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        components:
          securitySchemes:
            basicAuth:
              type: http
              scheme: basic
        paths:
          /logs:
            post:
              operationId: getLogs
              security:
                - basicAuth: []
                - basicAuth: []
              responses:
                '200':
                  description: OK
        """;

    var results = scanOperations(spec);
    var getLogs = operation(results, "getLogs");
    assertThat(getLogs.supported()).isTrue();
    assertThat(getLogs.builder().build().authenticationOverride())
        .extracting(HttpAuthentication::id)
        .containsExactly("basic.basicAuth");

    var template = generate(spec);
    assertThat(propertiesWithIdEndingIn(template, "authentication.username.basic.basicAuth"))
        .hasSize(1);
  }

  @Test
  void sameSecuritySchemeInTwoGlobalRequirements_isAddedOnce() {
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        security:
          - bearerAuth: []
          - bearerAuth: []
        components:
          securitySchemes:
            bearerAuth:
              type: http
              scheme: bearer
        paths:
          /items:
            get:
              operationId: listItems
              responses:
                '200':
                  description: OK
        """;

    var template = generate(spec);
    assertThat(propertiesWithIdEndingIn(template, "authentication.token")).hasSize(1);
  }

  @Test
  void securitySchemesSharedBetweenAlternativeRequirements_areAddedOnce() {
    // Bird disconnectRealtimeAppMember: {BearerAuth, RealtimeKey, RealtimeSecret} and
    // {CookieAuth, RealtimeKey, RealtimeSecret}
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        components:
          securitySchemes:
            BearerAuth:
              type: http
              scheme: bearer
            CookieAuth:
              type: apiKey
              in: header
              name: Cookie
            RealtimeKey:
              type: apiKey
              in: header
              name: X-Realtime-Key
            RealtimeSecret:
              type: apiKey
              in: header
              name: X-Realtime-Secret
        paths:
          /members/{memberId}:
            delete:
              operationId: disconnectRealtimeAppMember
              parameters:
                - name: memberId
                  in: path
                  required: true
                  schema:
                    type: string
              security:
                - BearerAuth: []
                  RealtimeKey: []
                  RealtimeSecret: []
                - CookieAuth: []
                  RealtimeKey: []
                  RealtimeSecret: []
              responses:
                '204':
                  description: No Content
        """;

    var results = scanOperations(spec);
    var operation = operation(results, "disconnectRealtimeAppMember");
    assertThat(operation.supported()).isTrue();
    assertThat(operation.builder().build().authenticationOverride())
        .extracting(HttpAuthentication::id)
        .containsExactly(
            "bearer", "apiKey.X-Realtime-Key", "apiKey.X-Realtime-Secret", "apiKey.Cookie");

    generate(spec);
  }

  @Test
  void explicitContentTypeHeaderWithMultipartBody_isReplacedByMultipartHeader() {
    // Mailgun POST /v3/{domain_name}/.../import: multipart body plus a Content-Type header param
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /import:
            post:
              operationId: importList
              parameters:
                - name: Content-Type
                  in: header
                  required: true
                  schema:
                    type: string
                    example: multipart/form-data
                - name: X-Other
                  in: header
                  schema:
                    type: string
              requestBody:
                content:
                  multipart/form-data:
                    schema:
                      type: object
                      properties:
                        file:
                          type: string
                          format: binary
              responses:
                '200':
                  description: OK
        """;

    var results = scanOperations(spec);
    var importList = operation(results, "importList");
    assertThat(importList.supported()).isTrue();
    var headers =
        importList.builder().getProperties().stream()
            .filter(p -> p.target() == Target.HEADER)
            .toList();
    assertThat(headers)
        .extracting(HttpOperationProperty::id)
        .containsExactly("X-Other", "Content-Type");
    assertThat(headers.getLast().type()).isEqualTo(HttpOperationProperty.Type.HIDDEN);
    assertThat(headers.getLast().example()).isEqualTo("multipart/form-data");

    var template = generate(spec);
    var contentType = propertiesWithIdEndingIn(template, "_header_Content-Type");
    assertThat(contentType).hasSize(1);
    assertThat(contentType.getFirst()).isInstanceOf(HiddenProperty.class);
  }

  @Test
  void lowercaseContentTypeHeaderWithMultipartBody_isReplacedByMultipartHeader() {
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /import:
            post:
              operationId: importList
              parameters:
                - name: content-type
                  in: header
                  schema:
                    type: string
              requestBody:
                content:
                  multipart/form-data:
                    schema:
                      type: object
                      properties:
                        file:
                          type: string
                          format: binary
              responses:
                '200':
                  description: OK
        """;

    var importList = operation(scanOperations(spec), "importList");
    assertThat(importList.builder().getProperties())
        .filteredOn(p -> p.target() == Target.HEADER)
        .extracting(HttpOperationProperty::id)
        .containsExactly("Content-Type");
  }

  @Test
  void explicitContentTypeHeaderWithJsonBody_isKept() {
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /items:
            post:
              operationId: createItem
              parameters:
                - name: Content-Type
                  in: header
                  schema:
                    type: string
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      properties:
                        name:
                          type: string
              responses:
                '200':
                  description: OK
        """;

    var createItem = operation(scanOperations(spec), "createItem");
    assertThat(createItem.builder().getProperties())
        .filteredOn(p -> p.target() == Target.HEADER)
        .singleElement()
        .satisfies(p -> {
          assertThat(p.id()).isEqualTo("Content-Type");
          assertThat(p.type()).isEqualTo(HttpOperationProperty.Type.STRING);
        });
  }

  @Test
  void remainingPropertyConflict_marksOnlyThatOperationUnsupported() {
    // The same header is declared twice with different descriptions.
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /broken:
            get:
              operationId: broken
              parameters:
                - name: X-Trace
                  in: header
                  description: first
                  schema:
                    type: string
                - name: X-Trace
                  in: header
                  description: second
                  schema:
                    type: string
              responses:
                '200':
                  description: OK
          /healthy:
            get:
              operationId: healthy
              responses:
                '200':
                  description: OK
        """;

    var results = scanOperations(spec);
    var broken = operation(results, "broken");
    assertThat(broken.supported()).isFalse();
    assertThat(broken.info()).contains("X-Trace");
    assertThat(operation(results, "healthy").supported()).isTrue();

    var template = generate(spec);
    assertThat(template.properties()).noneMatch(p -> "broken".equals(p.getValue()));
  }

  @Test
  void cookieParameter_marksOperationUnsupportedWithClearReason() {
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /session:
            get:
              operationId: withCookie
              parameters:
                - name: session
                  in: cookie
                  schema:
                    type: string
              responses:
                '200':
                  description: OK
          /healthy:
            get:
              operationId: healthy
              responses:
                '200':
                  description: OK
        """;

    var results = scanOperations(spec);
    var withCookie = operation(results, "withCookie");
    assertThat(withCookie.supported()).isFalse();
    assertThat(withCookie.info()).isEqualTo("Parameter 'session' in 'cookie' is not supported");
    assertThat(operation(results, "healthy").supported()).isTrue();

    generate(spec);
  }

  @Test
  void duplicateOperationId_marksLaterOperationUnsupported() {
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /first:
            get:
              operationId: sameId
              responses:
                '200':
                  description: OK
          /second:
            get:
              operationId: sameId
              responses:
                '200':
                  description: OK
        """;

    var results = scanOperations(spec);
    assertThat(results).hasSize(2);
    assertThat(results.get(0).supported()).isTrue();
    assertThat(results.get(0).path()).isEqualTo("/first");
    assertThat(results.get(1).supported()).isFalse();
    assertThat(results.get(1).info()).contains("sameId");

    generate(spec);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        // unchanged behaviour: all digits concatenated when they fit in an int
        "1.0.0|100",
        "1.2.3|123",
        "v2|2",
        "2024-01-15|20240115",
        "abc|294",
        // digits overflow an int: leading number is used instead
        "1001.0.0-SNAPSHOT-e92d688328ae175f94e88e52762c76ef7566d5c3|1001",
        "2020-09-14_1.822.2|2020",
        // even the leading number overflows: character sum
        "99999999999.0|721"
      })
  void templateVersion_isDerivedWithoutOverflow(String specVersion, long expected) {
    var spec =
        """
        openapi: 3.0.3
        info:
          title: Test API
          version: "%s"
        servers:
          - url: https://api.example.com
        paths:
          /items:
            get:
              operationId: listItems
              responses:
                '200':
                  description: OK
        """
            .formatted(specVersion);

    var scanResult = generator.scan(new OpenApiGenerationSource(List.of(spec)));
    assertThat(scanResult.templateVersion()).isEqualTo(expected);
    assertThat(generate(spec).version()).isEqualTo(expected);
  }
}
