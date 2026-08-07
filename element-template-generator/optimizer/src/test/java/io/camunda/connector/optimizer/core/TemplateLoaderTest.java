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
package io.camunda.connector.optimizer.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyCondition;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateLoaderTest {

  @TempDir Path tempDir;

  @Test
  void roundTripsRealisticTemplateJson() throws Exception {
    String json =
        """
        {
          "$schema": "https://unpkg.com/@camunda/zeebe-element-templates-json-schema/resources/schema.json",
          "name": "Test",
          "id": "io.camunda.test",
          "version": 1,
          "category": {"id": "connectors", "name": "Connectors"},
          "appliesTo": ["bpmn:ServiceTask"],
          "elementType": {"value": "bpmn:ServiceTask"},
          "groups": [],
          "properties": [
            {"id": "operationId", "type": "Dropdown", "value": "a",
             "binding": {"type": "zeebe:input", "name": "operationId"},
             "choices": [{"name": "a", "value": "a"}, {"name": "b", "value": "b"}]},
            {"id": "a_locale", "type": "Hidden", "value": "en-US",
             "binding": {"type": "zeebe:input", "name": "locale"},
             "condition": {"property": "operationId", "equals": "a"}},
            {"id": "shared", "type": "Hidden", "value": "v",
             "binding": {"type": "zeebe:input", "name": "shared"},
             "condition": {"property": "operationId", "oneOf": ["a", "b"]}}
          ]
        }
        """;
    Path file = tempDir.resolve("template.json");
    Files.writeString(file, json);

    ElementTemplate template = TemplateLoader.load(file);

    assertThat(template.id()).isEqualTo("io.camunda.test");
    assertThat(template.properties()).hasSize(3);

    var operationId = template.properties().get(0);
    assertThat(operationId).isInstanceOf(DropdownProperty.class);
    assertThat(operationId.getBinding()).isInstanceOf(PropertyBinding.ZeebeInput.class);

    var aLocale = template.properties().get(1);
    assertThat(aLocale).isInstanceOf(HiddenProperty.class);
    assertThat(aLocale.getCondition()).isInstanceOf(PropertyCondition.Equals.class);

    var shared = template.properties().get(2);
    assertThat(shared.getCondition()).isInstanceOf(PropertyCondition.OneOf.class);
    assertThat(((PropertyCondition.OneOf) shared.getCondition()).oneOf()).containsExactly("a", "b");
  }

  @Test
  void rejectsPropertyMissingTypeDiscriminator() throws Exception {
    assertLoadingFails(
        """
        {"properties": [
           {"id": "no_type_here", "value": "v",
            "binding": {"type": "zeebe:input", "name": "x"}}
         ]}
        """,
        "type");
  }

  @Test
  void rejectsBindingWithMissingRequiredField() throws Exception {
    assertLoadingFails(
        """
        {"properties": [
           {"id": "x", "type": "Hidden", "value": "v",
            "binding": {"type": "zeebe:input"}}
         ]}
        """,
        "name");
  }

  @Test
  void rejectsConditionWithMultipleDiscriminators() throws Exception {
    assertLoadingFails(
        """
        {"properties": [
           {"id": "x", "type": "Hidden", "value": "v",
            "binding": {"type": "zeebe:input", "name": "x"},
            "condition": {"property": "op", "equals": "a", "oneOf": ["b"]}}
         ]}
        """,
        "multiple discriminator");
  }

  @Test
  void rejectsConditionWithNoDiscriminator() throws Exception {
    assertLoadingFails(
        """
        {"properties": [
           {"id": "x", "type": "Hidden", "value": "v",
            "binding": {"type": "zeebe:input", "name": "x"},
            "condition": {"property": "op"}}
         ]}
        """,
        "missing a discriminator");
  }

  @Test
  void rejectsDropdownChoiceWithMissingValue() throws Exception {
    assertLoadingFails(
        """
        {"properties": [
           {"id": "op", "type": "Dropdown", "value": "a",
            "binding": {"type": "zeebe:input", "name": "op"},
            "choices": [{"name": "A"}]}
         ]}
        """,
        "value");
  }

  @Test
  void rejectsUnknownFeelMode() throws Exception {
    assertLoadingFails(
        """
        {"properties": [
           {"id": "x", "type": "String", "value": "v", "feel": "totally-bogus",
            "binding": {"type": "zeebe:input", "name": "x"}}
         ]}
        """,
        "Unknown feel mode");
  }

  @Test
  void rejectsNonBooleanOptional() throws Exception {
    assertLoadingFails(
        """
        {"properties": [
           {"id": "x", "type": "String", "value": "v", "optional": "yes",
            "binding": {"type": "zeebe:input", "name": "x"}}
         ]}
        """,
        "must be a boolean");
  }

  private void assertLoadingFails(String propertiesFragment, String messageFragment)
      throws Exception {
    String json =
        """
        {
          "name": "Test",
          "id": "io.camunda.test",
          "version": 1,
          "appliesTo": ["bpmn:ServiceTask"],
          "elementType": {"value": "bpmn:ServiceTask"},
          "groups": [],
          %s
        }
        """
            .formatted(propertiesFragment.replaceAll("^\\s*\\{", "").replaceAll("\\}\\s*$", ""));
    Path file = tempDir.resolve("bad-" + messageFragment.hashCode() + ".json");
    Files.writeString(file, json);
    assertThatThrownBy(() -> TemplateLoader.load(file)).hasMessageContaining(messageFragment);
  }
}
