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
package io.camunda.connector.generator.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.generator.core.example.MyConnectorFunction;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.OutboundElementTemplate.ElementType;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class OutboundTemplateGeneratorTest extends BaseTest {

  private final OutboundElementTemplateGenerator generator = new OutboundElementTemplateGenerator();

  @Nested
  class Basic {

    @Test
    void schemaPresent() {
      assertThat(generator.generate(MyConnectorFunction.FullyAnnotated.class).schema())
          .isEqualTo(
              "https://unpkg.com/@camunda/zeebe-element-templates-json-schema/resources/schema.json");
    }

    @Test
    void elementType_isServiceTask() {
      assertThat(generator.generate(MyConnectorFunction.FullyAnnotated.class).elementType())
          .isEqualTo(new ElementType(BpmnType.SERVICE_TASK));
    }

    @Test
    void appliesTo_isTask() {
      assertThat(generator.generate(MyConnectorFunction.FullyAnnotated.class).appliesTo())
          .isEqualTo(Set.of(BpmnType.TASK));
    }

    @Test
    void elementTemplateAnnotation_canDefineBasicFields() {
      var template = generator.generate(MyConnectorFunction.FullyAnnotated.class);
      assertThat(template.id()).isEqualTo(MyConnectorFunction.ID);
      assertThat(template.name()).isEqualTo(MyConnectorFunction.NAME);
      assertThat(template.version()).isEqualTo(MyConnectorFunction.VERSION);
      assertThat(template.documentationRef()).isEqualTo(MyConnectorFunction.DOCUMENTATION_REF);
      assertThat(template.description()).isEqualTo(MyConnectorFunction.DESCRIPTION);
    }

    @Test
    void elementTemplateAnnotation_providesCorrectDefaultValues() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      assertThat(template.id()).isEqualTo(MyConnectorFunction.ID);
      assertThat(template.name()).isEqualTo(MyConnectorFunction.NAME);
      assertThat(template.version()).isEqualTo(0);
      assertThat(template.documentationRef()).isNull();
      assertThat(template.description()).isNull();
    }
  }

  @Nested
  class Properties {

    @Test
    void notAnnotated_StringProperty_hasCorrectDefaults() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property = getPropertyByLabel("Not annotated string property", template);

      assertThat(property).isInstanceOf(StringProperty.class);
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.isOptional()).isNull();
      assertThat(property.getGroup()).isEqualTo(null);
      assertThat(property.getFeel()).isEqualTo(FeelMode.optional);
      assertThat(property.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeInput("notAnnotatedStringProperty"));
    }

    @Test
    void annotated_StringProperty_definedByAnnotation() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property = getPropertyById("annotatedStringProperty", template);

      assertThat(property).isInstanceOf(TextProperty.class);
      assertThat(property.getType()).isEqualTo("Text");
      assertThat(property.isOptional()).isFalse();
      assertThat(property.getLabel()).isEqualTo("Annotated and renamed string property");
      assertThat(property.getGroup()).isEqualTo("group1");
      assertThat(property.getDescription()).isEqualTo("description");
      assertThat(property.getFeel()).isEqualTo(FeelMode.optional);
      assertThat(property.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeInput("annotatedStringProperty"));
    }

    @Test
    void objectProperty_hasRequiredFeelByDefault() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);

      var objectProperty = getPropertyByLabel("Object property", template);
      assertThat(objectProperty.getFeel()).isEqualTo(FeelMode.required);

      var jsonNodeProperty = getPropertyByLabel("Json node property", template);
      assertThat(jsonNodeProperty.getFeel()).isEqualTo(FeelMode.required);
    }

    @Test
    void notAnnotated_EnumProperty_hasCorrectDefaults() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property = getPropertyByLabel("Enum property", template);

      assertThat(property).isInstanceOf(DropdownProperty.class);
      assertThat(property.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) property).getChoices())
          .containsExactly(
              new DropdownChoice("VALUE1", "VALUE1"), new DropdownChoice("VALUE2", "VALUE2"));
      assertThat(property.isOptional()).isNull();
      assertThat(property.getGroup()).isEqualTo(null);
      assertThat(property.getFeel()).isEqualTo(null);
      assertThat(property.getBinding()).isEqualTo(new PropertyBinding.ZeebeInput("enumProperty"));
    }

    @Test
    void nested_addsPrefixPathByDefault() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      assertDoesNotThrow(() -> getPropertyById("nestedProperty.nestedA", template));
      assertThrows(Exception.class, () -> getPropertyById("nestedProperty.nestedB", template));
    }

    @Test
    void nested_disableAddPrefixPath() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      assertDoesNotThrow(() -> getPropertyById("nestedB", template));
      assertThrows(
          Exception.class, () -> getPropertyById("customPathNestedProperty.nestedB", template));
    }

    @Test
    void ignoredProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      assertThat(template.properties()).noneMatch(p -> "ignoredField".equals(p.getId()));
    }

    @Test
    void conditionalProperty_valid_equals() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property = getPropertyByLabel("Conditional property equals", template);

      assertThat(property.getCondition())
          .isEqualTo(new PropertyCondition.Equals("annotatedStringProperty", "value"));
    }

    @Test
    void conditionalProperty_valid_oneOf() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property = getPropertyByLabel("Conditional property one of", template);

      assertThat(property.getCondition())
          .isEqualTo(
              new PropertyCondition.OneOf("annotatedStringProperty", List.of("value1", "value2")));
    }
  }

  @Nested
  class SealedTypes {

    @Test
    void nonAnnotated_sealedType_hasCorrectDefaults() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var discriminatorProperty =
          template.properties().stream()
              .filter(p -> "Non annotated sealed type".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(discriminatorProperty).isInstanceOf(DropdownProperty.class);
      assertThat(discriminatorProperty.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) discriminatorProperty).getChoices())
          .containsExactly(
              new DropdownChoice("First sub type", "firstsubtype"),
              new DropdownChoice("Second sub type", "secondsubtype"));

      var firstSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "First sub type value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(firstSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "firstsubtype"));

      var secondSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "Second sub type value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(secondSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "secondsubtype"));
    }

    @Test
    void annotated_sealedType_followsAnnotations() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var discriminatorProperty =
          template.properties().stream()
              .filter(p -> "Annotated type override".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(discriminatorProperty).isInstanceOf(DropdownProperty.class);
      assertThat(discriminatorProperty.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) discriminatorProperty).getChoices())
          .containsExactly(
              new DropdownChoice("First annotated override", "firstAnnotatedOverride"),
              new DropdownChoice("Second annotated override", "secondAnnotatedOverride"));

      var firstSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "First annotated override value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(firstSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "firstAnnotatedOverride"));

      var secondSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "Second annotated override value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(secondSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "secondAnnotatedOverride"));
    }
  }

  @Nested
  class PropertyGroups {

    @Test
    void propertyGroups_unordered() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      checkPropertyGroups(
          List.of(
              Map.entry("group1", "Group 1"),
              Map.entry("group2", "Group 2"),
              Map.entry("output", "Output mapping"),
              Map.entry("error", "Error handling")),
          template,
          false);
    }

    @Test
    void propertyGroups_orderedAndLabeledByAnnotation() {
      var template = generator.generate(MyConnectorFunction.FullyAnnotated.class);
      checkPropertyGroups(
          List.of(
              Map.entry("group2", "Group Two"),
              Map.entry("group1", "Group One"),
              Map.entry("output", "Output mapping"),
              Map.entry("error", "Error handling")),
          template,
          true);
    }

    @Test
    void propertyGroupContents_definedByTemplatePropertyAnnotation() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var group1 =
          template.properties().stream()
              .filter(p -> "group1".equals(p.getGroup()))
              .collect(Collectors.toList());
      assertThat(group1).hasSize(2);
      assertThat(group1)
          .containsExactlyInAnyOrder(
              getPropertyByLabel("Annotated and renamed string property", template),
              getPropertyByLabel("Property for group 1", template));
    }
  }
}
