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
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.http.OperationParseResult;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OpenAPI 3.1 writes schema types as {@code types} (a set, which may include {@code "null"}), while
 * 3.0 writes a single {@code type}. These tests make sure a 3.1 spec produces the same fields as
 * the equivalent 3.0 spec.
 */
public class OpenApi31Test {

  private static final String SPEC_31 = "src/test/resources/example-openapi-3.1.yaml";

  // language=yaml
  private static final String NULLABLE_SPEC_30 =
      """
      openapi: 3.0.3
      info:
        title: Pet Store
        version: 1.0.0
      servers:
        - url: https://api.example.com
      paths:
        /pets:
          get:
            operationId: listPets
            parameters:
              - name: vaccinated
                in: query
                schema:
                  type: boolean
                  nullable: true
            responses:
              '200':
                description: The pets.
          post:
            operationId: createPet
            requestBody:
              content:
                application/json:
                  schema:
                    type: object
                    properties:
                      nickname:
                        type: string
                        nullable: true
                        example: Rex
                      age:
                        type: integer
                        nullable: true
            responses:
              '201':
                description: Created.
      """;

  // the same API as NULLABLE_SPEC_30, written with OpenAPI 3.1 type arrays
  // language=yaml
  private static final String NULLABLE_SPEC_31 =
      """
      openapi: 3.1.0
      info:
        title: Pet Store
        version: 1.0.0
      servers:
        - url: https://api.example.com
      paths:
        /pets:
          get:
            operationId: listPets
            parameters:
              - name: vaccinated
                in: query
                schema:
                  type: [boolean, "null"]
            responses:
              '200':
                description: The pets.
          post:
            operationId: createPet
            requestBody:
              content:
                application/json:
                  schema:
                    type: object
                    properties:
                      nickname:
                        type: [string, "null"]
                        examples: [null, Rex]
                      age:
                        type: [integer, "null"]
            responses:
              '201':
                description: Created.
      """;

  private final OpenApiOutboundTemplateGenerator generator = new OpenApiOutboundTemplateGenerator();

  @Test
  void scan_supportsAllOperations() {
    var result = generator.scan(new OpenApiGenerationSource(List.of(SPEC_31)));

    assertThat(result.additionalData())
        .asInstanceOf(InstanceOfAssertFactories.list(OperationParseResult.class))
        .extracting(OperationParseResult::id, OperationParseResult::supported)
        .containsExactlyInAnyOrder(
            tuple("getPet", true),
            tuple("updatePet", true),
            tuple("createPet", true),
            tuple("replaceTags", true));
  }

  @Test
  void generate_typesParametersFromTypeArrays() {
    var template = generate(SPEC_31);

    assertThat(property(template, "getPet_path_petId").getType()).isEqualTo("String");
    assertThat(property(template, "getPet_path_petId").getValue()).isEqualTo("42");
    assertThat(property(template, "getPet_query_verbose"))
        .isInstanceOfSatisfying(
            DropdownProperty.class,
            dropdown ->
                assertThat(dropdown.getChoices())
                    .extracting(DropdownChoice::value)
                    .containsExactly("true", "false"));
    // type: [string, "null"]
    assertThat(property(template, "getPet_query_status").getType()).isEqualTo("String");
    assertThat(property(template, "getPet_query_status").getValue()).isEqualTo("available");
  }

  @Test
  void generate_breaksDownRequestBodyIntoFields() {
    var template = generate(SPEC_31);

    assertThat(property(template, "createPet_$body").getValue())
        .isEqualTo("={name:name,nickname:nickname,vaccinated:vaccinated,age:age}");
    assertThat(property(template, "createPet_body_name").getValue()).isEqualTo("Rex");
    // type: [string, "null"]
    assertThat(property(template, "createPet_body_nickname").getType()).isEqualTo("String");
    assertThat(property(template, "createPet_body_vaccinated"))
        .isInstanceOf(DropdownProperty.class);
    // type: [integer, "null"]
    assertThat(property(template, "createPet_body_age").getType()).isEqualTo("String");
  }

  @Test
  void generate_keepsRawBodyWhenPropertyReferencesAnotherPath() {
    var template = generate(SPEC_31);

    assertThat(property(template, "updatePet_body").getValue())
        .isEqualTo("={\"name\":\"Rex\",\"tags\":[{\"name\":\"good boy\"}]}");
  }

  @Test
  void generate_buildsExampleForComplexRequestBody() {
    var template = generate(SPEC_31);

    assertThat((String) property(template, "replaceTags_body").getValue())
        .startsWith("=[")
        .contains("\"name\" : \"string\"")
        .contains("\"color\" : \"string\"");
  }

  @Test
  void generate_sameSpecAs30And31_producesSameProperties() throws Exception {
    var mapper = new ObjectMapper();

    var properties30 = mapper.writeValueAsString(generateFromContent(specWithVersion("3.0.3")));
    var properties31 = mapper.writeValueAsString(generateFromContent(specWithVersion("3.1.0")));

    assertThat(properties31).isEqualTo(properties30);
  }

  @Test
  void generate_nullableTypeArrays_matchNullableIn30() throws Exception {
    var mapper = new ObjectMapper();

    var properties30 = mapper.writeValueAsString(generateFromContent(NULLABLE_SPEC_30));
    var properties31 = mapper.writeValueAsString(generateFromContent(NULLABLE_SPEC_31));

    assertThat(properties31).isEqualTo(properties30);
  }

  @Test
  void generate_arrayBodyWithoutItems_staysSupportedWithRawBody() {
    // language=yaml
    var spec =
        """
        openapi: 3.1.0
        info:
          title: Pet Store
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /pets/batch:
            post:
              operationId: createPets
              requestBody:
                content:
                  application/json:
                    schema:
                      type: array
              responses:
                '201':
                  description: Created.
        """;

    var scan = generator.scan(new OpenApiGenerationSource(List.of(spec)));
    assertThat(scan.additionalData())
        .asInstanceOf(InstanceOfAssertFactories.list(OperationParseResult.class))
        .extracting(OperationParseResult::id, OperationParseResult::supported)
        .containsExactly(tuple("createPets", true));

    var properties = generateFromContent(spec);
    assertThat(properties).extracting(Property::getId).contains("createPets_body");
  }

  @ParameterizedTest
  @ValueSource(strings = {"3.0.3", "3.1.0"})
  void generate_recursiveArrayBody_staysSupportedWithRawBody(String version) {
    // language=yaml
    var spec =
        """
        openapi: %s
        info:
          title: Nested
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /with-example:
            post:
              operationId: withExample
              requestBody:
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/NestedWithExample'
              responses:
                '204':
                  description: Done.
          /without-example:
            post:
              operationId: withoutExample
              requestBody:
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/Nested'
              responses:
                '204':
                  description: Done.
        components:
          schemas:
            NestedWithExample:
              type: array
              items:
                $ref: '#/components/schemas/NestedWithExample'
              example: [[]]
            Nested:
              type: array
              items:
                $ref: '#/components/schemas/Nested'
        """
            .formatted(version);

    var scan = generator.scan(new OpenApiGenerationSource(List.of(spec)));
    assertThat(scan.additionalData())
        .asInstanceOf(InstanceOfAssertFactories.list(OperationParseResult.class))
        .extracting(OperationParseResult::id, OperationParseResult::supported)
        .containsExactlyInAnyOrder(tuple("withExample", true), tuple("withoutExample", true));

    var properties = generateFromContent(spec);
    assertThat(property(properties, "withExample_body").getValue())
        .asString()
        .isEqualToIgnoringWhitespace("=[[]]");
    assertThat(property(properties, "withoutExample_body").getValue())
        .asString()
        .isEqualToIgnoringWhitespace("=[[]]");
  }

  @ParameterizedTest
  @ValueSource(strings = {"3.0.3", "3.1.0"})
  void generate_rawStringBody_keepsQuotes(String version) {
    var example = version.startsWith("3.0") ? "example: hello" : "examples: [hello]";
    // language=yaml
    var spec =
        """
        openapi: %s
        info:
          title: Messages
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /messages:
            post:
              operationId: sendMessage
              requestBody:
                content:
                  text/plain:
                    schema:
                      type: string
                      %s
              responses:
                '204':
                  description: Sent.
        """
            .formatted(version, example);

    var properties =
        generator
            .generate(
                new OpenApiGenerationSource(List.of(spec, "--raw-body")),
                (GeneratorConfiguration) null)
            .getFirst()
            .properties();

    assertThat(property(properties, "sendMessage_body").getValue()).isEqualTo("=\"hello\"");
  }

  // the parser returns 3.1 object and array examples as Map and List, not as JSON nodes
  @ParameterizedTest
  @ValueSource(strings = {"3.0.3", "3.1.0"})
  void generate_objectAndArrayExamples_areWrittenAsJson(String version) {
    // language=yaml
    var spec =
        """
        openapi: %s
        info:
          title: Examples
          version: 1.0.0
        servers:
          - url: https://api.example.com
        paths:
          /tags:
            post:
              operationId: replaceTags
              parameters:
                - name: filter
                  in: query
                  schema:
                    $ref: '#/components/schemas/Filter'
                - name: sort
                  in: query
                  schema:
                    type: object
                    properties:
                      field:
                        type: string
                  example:
                    field: name
                - name: cursor
                  in: query
                  schema:
                    type: string
                    format: byte
                    example: aGVsbG8K
              requestBody:
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/Tags'
              responses:
                '204':
                  description: Done.
          /apps:
            post:
              operationId: addApps
              requestBody:
                content:
                  application/json:
                    schema:
                      type: object
                      properties:
                        apps:
                          type: array
                          items:
                            type: string
                      example:
                        apps: [my-app]
                    examples:
                      default:
                        value:
                          apps: [octoapp]
              responses:
                '204':
                  description: Done.
          /matrix:
            post:
              operationId: setMatrix
              requestBody:
                content:
                  application/json:
                    schema:
                      $ref: '#/components/schemas/Matrix'
              responses:
                '204':
                  description: Done.
        components:
          schemas:
            Tags:
              type: array
              items:
                type: string
              example: [red, blue]
            Filter:
              type: object
              properties:
                a:
                  type: string
              example:
                a: red
            Matrix:
              type: array
              items:
                type: array
                items:
                  type: integer
              example: [[1, 2], [3]]
        """
            .formatted(version);

    var properties = generateFromContent(spec);

    assertThat(property(properties, "replaceTags_body").getValue())
        .isEqualTo("=[\"red\",\"blue\"]");
    assertThat(property(properties, "replaceTags_query_filter").getValue())
        .isEqualTo("{\"a\":\"red\"}");
    assertThat(property(properties, "replaceTags_query_sort").getValue())
        .isEqualTo("{\"field\":\"name\"}");
    assertThat(property(properties, "replaceTags_query_cursor").getValue()).isEqualTo("aGVsbG8K");
    assertThat(property(properties, "addApps_body").getValue())
        .isEqualTo("={\"apps\":[\"my-app\"]}");
    assertThat(property(properties, "setMatrix_body").getValue()).isEqualTo("=[[1,2],[3]]");
  }

  private static Property property(List<Property> properties, String id) {
    return properties.stream()
        .filter(p -> id.equals(p.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No property with id " + id));
  }

  private ElementTemplate generate(String source) {
    return generator.generate(new OpenApiGenerationSource(List.of(source))).getFirst();
  }

  private List<Property> generateFromContent(String content) {
    return generator
        .generate(new OpenApiGenerationSource(List.of(content)), (GeneratorConfiguration) null)
        .getFirst()
        .properties();
  }

  private static Property property(ElementTemplate template, String id) {
    return template.properties().stream()
        .filter(p -> id.equals(p.getId()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No property with id " + id));
  }

  // language=yaml
  private static String specWithVersion(String version) {
    return """
    openapi: %s
    info:
      title: Pet Store
      version: 1.0.0
    servers:
      - url: https://api.example.com
    paths:
      /pets/{petId}:
        get:
          operationId: getPet
          parameters:
            - name: petId
              in: path
              required: true
              schema:
                type: integer
                example: 42
            - name: verbose
              in: query
              schema:
                type: boolean
          responses:
            '200':
              description: The pet.
      /pets:
        post:
          operationId: createPet
          requestBody:
            content:
              application/json:
                schema:
                  type: object
                  properties:
                    name:
                      type: string
                      example: Rex
                    vaccinated:
                      type: boolean
                    age:
                      type: integer
          responses:
            '201':
              description: The created pet.
    """
        .formatted(version);
  }
}
