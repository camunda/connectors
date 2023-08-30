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

import io.camunda.connector.generator.core.example.MyConnectorFunction;
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.OutboundElementTemplate.ElementType;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class OutboundTemplateGeneratorTest {

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
      var property =
          template.properties().stream()
              .filter(p -> "Not annotated string property".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

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
      var property =
          template.properties().stream()
              .filter(p -> "annotatedStringProperty".equals(p.getName()))
              .findFirst()
              .orElseThrow();

      assertThat(property).isInstanceOf(TextProperty.class);
      assertThat(property.getType()).isEqualTo("Text");
      assertThat(property.isOptional()).isFalse();
      assertThat(property.getLabel()).isEqualTo("Annotated and renamed string property");
      assertThat(property.getGroup()).isEqualTo("message");
      assertThat(property.getDescription()).isEqualTo("description");
      assertThat(property.getFeel()).isEqualTo(FeelMode.optional);
      assertThat(property.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeInput("annotatedStringProperty"));
    }

    @Test
    void objectProperty_hasRequiredFeelByDefault() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);

      var objectProperty =
          template.properties().stream()
              .filter(p -> "Object property".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(objectProperty.getFeel()).isEqualTo(FeelMode.required);

      var jsonNodeProperty =
          template.properties().stream()
              .filter(p -> "Json node property".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(jsonNodeProperty.getFeel()).isEqualTo(FeelMode.required);
    }

    @Test
    void notAnnotated_EnumProperty_hasCorrectDefaults() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property =
          template.properties().stream()
              .filter(p -> "Enum property".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

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

      template.properties().stream()
          .filter(p -> "nestedProperty.nestedA".equals(p.getName()))
          .findFirst()
          .orElseThrow();

      assertThat(
              template.properties().stream().filter(p -> "nestedA".equals(p.getName())).findAny())
          .isEmpty();
    }

    @Test
    void nested_disableAddPrefixPath() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);

      template.properties().stream()
          .filter(p -> "nestedB".equals(p.getName()))
          .findFirst()
          .orElseThrow();

      assertThat(
              template.properties().stream()
                  .filter(p -> "customPathNestedProperty.nestedB".equals(p.getName()))
                  .findAny())
          .isEmpty();
    }

    @Test
    void ignoredProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      assertThat(template.properties()).noneMatch(p -> "ignoredField".equals(p.getName()));
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
          .isEqualTo(new Equals(discriminatorProperty.getName(), "firstsubtype"));

      var secondSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "Second sub type value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(secondSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getName(), "secondsubtype"));
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
          .isEqualTo(new Equals(discriminatorProperty.getName(), "firstAnnotatedOverride"));

      var secondSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "Second annotated override value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(secondSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getName(), "secondAnnotatedOverride"));
    }
  }
}
