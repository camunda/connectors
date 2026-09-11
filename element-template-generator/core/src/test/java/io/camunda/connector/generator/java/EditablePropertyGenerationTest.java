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
package io.camunda.connector.generator.java;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.dsl.BooleanProperty;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.ElementTemplateBuilder;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DefaultValueType;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class EditablePropertyGenerationTest {

  private static final String SCHEMA_URL =
      "https://unpkg.com/@camunda/zeebe-element-templates-json-schema@0.44.0/resources/schema.json";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().registerModule(new ElementTemplateModule());

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  @Test
  void emitsEditableOnlyForReadOnlyPropertiesAndPreservesItWhenCopied() throws Exception {
    assertThat(NullableBoolean.NULL.toBoolean()).isNull();

    var template = generator.generate(ReadOnlyBooleanConnector.class).getFirst();
    var readOnlyProperty = (BooleanProperty) property(template, "readOnly");
    var explicitlyEditableProperty = property(template, "explicitlyEditable");
    var defaultEditableProperty = property(template, "defaultEditable");

    assertThat(readOnlyProperty.getEditable()).isFalse();
    assertThat(readOnlyProperty.getType()).isEqualTo("Boolean");
    assertThat(readOnlyProperty.getValue()).isEqualTo(true);
    assertThat(readOnlyProperty.getBinding().type()).isEqualTo("zeebe:input");
    assertThat(readOnlyProperty.getFeel()).isEqualTo(FeelMode.staticFeel);
    assertThat(explicitlyEditableProperty.getEditable()).isNull();
    assertThat(defaultEditableProperty.getEditable()).isNull();

    JsonNode templateJson = MAPPER.valueToTree(template);
    JsonNode readOnlyPropertyJson = property(templateJson, "readOnly");
    assertThat(readOnlyPropertyJson.get("editable").booleanValue()).isFalse();
    assertThat(readOnlyPropertyJson.get("feel").textValue()).isEqualTo("static");
    assertThat(property(templateJson, "explicitlyEditable").has("editable")).isFalse();
    assertThat(property(templateJson, "defaultEditable").has("editable")).isFalse();
    assertThat(schema().validate(templateJson)).isEmpty();

    var copiedProperty = (BooleanProperty) readOnlyProperty.toBuilder().build();
    assertThat(copiedProperty.getEditable()).isFalse();

    var copiedTemplate = ElementTemplateBuilder.from(template).build();
    assertThat(property(copiedTemplate, "readOnly").getEditable()).isFalse();
  }

  private static io.camunda.connector.generator.dsl.Property property(
      ElementTemplate template, String id) {
    return template.properties().stream()
        .filter(property -> id.equals(property.getId()))
        .findFirst()
        .orElseThrow();
  }

  private static JsonNode property(JsonNode template, String id) {
    for (JsonNode property : template.get("properties")) {
      if (id.equals(property.path("id").textValue())) {
        return property;
      }
    }
    throw new IllegalStateException(id + " property not found");
  }

  private static JsonSchema schema() throws Exception {
    var client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    var request =
        HttpRequest.newBuilder(URI.create(SCHEMA_URL))
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build();
    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isBetween(200, 299);
    return JsonSchemaFactory.getInstance(VersionFlag.V7)
        .getSchema(MAPPER.readTree(response.body()));
  }

  record ReadOnlyBooleanInput(
      @TemplateProperty(
              type = TemplateProperty.PropertyType.Boolean,
              editable = NullableBoolean.FALSE,
              defaultValue = "true",
              defaultValueType = DefaultValueType.Boolean)
          Boolean readOnly,
      @TemplateProperty(
              type = TemplateProperty.PropertyType.Boolean,
              editable = NullableBoolean.TRUE,
              defaultValue = "true",
              defaultValueType = DefaultValueType.Boolean)
          Boolean explicitlyEditable,
      @TemplateProperty(
              type = TemplateProperty.PropertyType.Boolean,
              defaultValue = "false",
              defaultValueType = DefaultValueType.Boolean)
          Boolean defaultEditable) {}

  @OutboundConnector(name = "Read-only Boolean", type = "test:read-only-boolean")
  @io.camunda.connector.generator.java.annotation.ElementTemplate(
      id = "test-read-only-boolean",
      name = "Read-only Boolean",
      version = 1,
      inputDataClass = ReadOnlyBooleanInput.class)
  static class ReadOnlyBooleanConnector implements OutboundConnectorFunction {

    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }
}
