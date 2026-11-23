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

/**
 * OpenAPI 3.1 writes schema types as {@code types} (a set, which may include {@code "null"}), while
 * 3.0 writes a single {@code type}. These tests make sure a 3.1 spec produces the same fields as
 * the equivalent 3.0 spec.
 */
public class OpenApi31Test {

  private static final String SPEC_31 = "src/test/resources/example-openapi-3.1.yaml";

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
