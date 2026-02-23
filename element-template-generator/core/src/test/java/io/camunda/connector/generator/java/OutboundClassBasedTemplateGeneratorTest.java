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

import static io.camunda.connector.generator.java.util.TemplateGenerationStringUtil.camelCaseToSpaces;
import static java.nio.file.Files.readAllBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.generator.BaseTest;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate.ElementTypeWrapper;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.NumberProperty;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskHeader;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyConstraints.Pattern;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.example.outbound.MyConnectorFunction;
import io.camunda.connector.generator.java.example.outbound.OperationAnnotatedConnector;
import io.camunda.connector.generator.java.example.outbound.OperationAnnotatedConnectorWithPrimitiveTypes;
import io.camunda.connector.generator.java.example.outbound.SingleOperationAnnotatedConnector;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class OutboundClassBasedTemplateGeneratorTest extends BaseTest {

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  @Nested
  class Basic {

    @Test
    void schemaPresent() {
      assertThat(generator.generate(MyConnectorFunction.FullyAnnotated.class).getFirst().schema())
          .isEqualTo(
              "https://unpkg.com/@camunda/zeebe-element-templates-json-schema/resources/schema.json");
    }

    @Test
    void elementType_default_isServiceTask() {
      assertThat(
              generator
                  .generate(MyConnectorFunction.MinimallyAnnotated.class)
                  .getFirst()
                  .elementType())
          .isEqualTo(ElementTypeWrapper.from(BpmnType.SERVICE_TASK));
    }

    @Test
    void elementType_customizable() {
      assertThat(
              generator.generate(MyConnectorFunction.FullyAnnotated.class).getFirst().elementType())
          .isEqualTo(ElementTypeWrapper.from(BpmnType.SCRIPT_TASK));
    }

    @Test
    void appliesTo_default_isTask() {
      assertThat(
              generator
                  .generate(MyConnectorFunction.MinimallyAnnotated.class)
                  .getFirst()
                  .appliesTo())
          .isEqualTo(Set.of(BpmnType.TASK.getName()));
    }

    @Test
    void appliesTo_customizable() {
      assertThat(
              generator.generate(MyConnectorFunction.FullyAnnotated.class).getFirst().appliesTo())
          .isEqualTo(Set.of(BpmnType.SERVICE_TASK.getName()));
    }

    @Test
    void elementTemplateAnnotation_canDefineBasicFields() {
      var template = generator.generate(MyConnectorFunction.FullyAnnotated.class).getFirst();
      assertThat(template.id()).isEqualTo(MyConnectorFunction.ID);
      assertThat(template.name()).isEqualTo(MyConnectorFunction.NAME);
      assertThat(template.version()).isEqualTo(MyConnectorFunction.VERSION);
      assertThat(template.documentationRef()).isEqualTo(MyConnectorFunction.DOCUMENTATION_REF);
      assertThat(template.description()).isEqualTo(MyConnectorFunction.DESCRIPTION);
    }

    @Test
    void elementTemplateAnnotation_providesCorrectDefaultValues() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      assertThat(template.id()).isEqualTo(MyConnectorFunction.ID);
      assertThat(template.name()).isEqualTo(MyConnectorFunction.NAME);
      assertThat(template.version()).isEqualTo(0);
      assertThat(template.documentationRef()).isNull();
      assertThat(template.description()).isNull();
    }

    @Test
    void resultVariableProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Result variable", template);
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskHeader");
      assertThat(property.getFeel()).isNull();
    }

    @Test
    void resultVariablePropertyWithValue() {
      var template =
          generator
              .generate(MyConnectorFunction.MinimallyAnnotatedWithResultVariable.class)
              .getFirst();
      var property = getPropertyByLabel("Result variable", template);
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskHeader");
      assertThat(property.getFeel()).isNull();
      assertThat(property.getValue()).isEqualTo("myResultVariable");
    }

    @Test
    void resultExpressionProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Result expression", template);
      assertThat(property.getType()).isEqualTo("Text");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskHeader");
      assertThat(property.getFeel()).isEqualTo(FeelMode.required);
    }

    @Test
    void staticFeelProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyById("feeModelStaticProperty", template);
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.getFeel()).isEqualTo(FeelMode.staticFeel);
    }

    @Test
    void resultExpressionPropertyWithValue() {
      var template =
          generator
              .generate(MyConnectorFunction.MinimallyAnnotatedWithResultExpression.class)
              .getFirst();
      var property = getPropertyByLabel("Result expression", template);
      assertThat(property.getType()).isEqualTo("Text");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskHeader");
      assertThat(property.getFeel()).isEqualTo(FeelMode.required);
      assertThat(property.getValue()).isEqualTo("={ myResponse: response }");
    }

    @Test
    void errorExpressionProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Error expression", template);
      assertThat(property.getType()).isEqualTo("Text");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskHeader");
      assertThat(property.getFeel()).isEqualTo(FeelMode.required);
    }

    @Test
    void retryBackoffProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Retry backoff", template);
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskHeader");
      assertThat(((ZeebeTaskHeader) property.getBinding()).key()).isEqualTo("retryBackoff");
      assertThat(property.getGroup()).isEqualTo("retries");
      assertThat(property.getValue()).isEqualTo("PT0S");
    }

    @Test
    void retryCountProperty() {
      var templates = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property = getPropertyByLabel("Retries", templates.getFirst());
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskDefinition");
      assertThat(((ZeebeTaskDefinition) property.getBinding()).property()).isEqualTo("retries");
      assertThat(property.getGroup()).isEqualTo("retries");
      assertThat(property.getValue()).isEqualTo("3");
    }

    @Test
    void normalMode_taskDefinitionTypeProperty_hidden() {
      var templates = generator.generate(MyConnectorFunction.MinimallyAnnotated.class);
      var property =
          templates.getFirst().properties().stream()
              .filter(p -> "zeebe:taskDefinition".equals(p.getBinding().type()))
              .findFirst()
              .orElseThrow();
      assertThat(property.getType()).isEqualTo("Hidden");
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskDefinition");
      assertThat(((ZeebeTaskDefinition) property.getBinding()).property()).isEqualTo("type");
    }

    @Test
    void hybridMode_taskDefinitionTypePropertyPresent() {
      var template =
          generator
              .generate(
                  MyConnectorFunction.MinimallyAnnotated.class,
                  new GeneratorConfiguration(
                      ConnectorMode.HYBRID, null, null, null, null, Map.of()))
              .getFirst();
      var property = getPropertyById("taskDefinitionType", template);
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.getGroup()).isEqualTo("taskDefinitionType");
      assertThat(property.getFeel()).isEqualTo(null);
      assertThat(property.getBinding().type()).isEqualTo("zeebe:taskDefinition");
      assertThat(((ZeebeTaskDefinition) property.getBinding()).property()).isEqualTo("type");
    }
  }

  @Nested
  class ElementTypes {

    @Test
    void singleElementType_hasCorrectNameAndId() {
      // when single element type is defined
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      // then no suffixes are added
      assertThat(template.id()).isEqualTo(MyConnectorFunction.ID);
      assertThat(template.name()).isEqualTo(MyConnectorFunction.NAME);
    }

    @Test
    void multipleElementTypes_definedInAnnotation() {
      var config =
          new GeneratorConfiguration(ConnectorMode.HYBRID, null, null, null, null, Map.of());
      var templates =
          generator.generate(MyConnectorFunction.WithMultipleElementTypes.class, config);
      boolean hasServiceTask = false,
          hasScriptTask = false,
          hasSendTask = false,
          hasMessageThrowEvent = false,
          hasMessageEndEvent = false;
      for (var template : templates) {
        if (template.elementType().equals(ElementTypeWrapper.from(BpmnType.SERVICE_TASK))) {
          hasServiceTask = true;
        } else if (template.elementType().equals(ElementTypeWrapper.from(BpmnType.SCRIPT_TASK))) {
          hasScriptTask = true;
        } else if (template.elementType().equals(ElementTypeWrapper.from(BpmnType.SEND_TASK))) {
          hasSendTask = true;
        } else if (template
            .elementType()
            .equals(ElementTypeWrapper.from(BpmnType.INTERMEDIATE_THROW_EVENT))) {
          hasMessageThrowEvent = true;
        } else if (template
            .elementType()
            .equals(ElementTypeWrapper.from(BpmnType.MESSAGE_END_EVENT))) {
          hasMessageEndEvent = true;
        }
      }
      assertThat(templates.size()).isEqualTo(5);
      assertTrue(hasServiceTask);
      assertTrue(hasScriptTask);
      assertTrue(hasSendTask);
      assertTrue(hasMessageThrowEvent);
      assertTrue(hasMessageEndEvent);
    }

    @Test
    void multipleElementTypes_definedInAnnotation_haveCorrectNamesAndIds() {
      // when
      var templates = generator.generate(MyConnectorFunction.WithMultipleElementTypes.class);

      // then
      var templateMap =
          templates.stream()
              .collect(Collectors.toMap(t -> t.elementType().originalType().getId(), t -> t));

      for (var elementType : BpmnType.values()) {
        var template = templateMap.get(elementType.getId());
        if (template == null) {
          continue;
        }
        if (elementType == BpmnType.INTERMEDIATE_THROW_EVENT) {
          assertThat(template.id()).isEqualTo("my-custom-id-for-intermediate-event");
          assertThat(template.name()).isEqualTo("My custom name for intermediate event");
        } else {
          assertThat(template.id()).isEqualTo(MyConnectorFunction.ID + ":" + elementType.getId());
          assertThat(template.name())
              .isEqualTo(
                  MyConnectorFunction.NAME + " (" + camelCaseToSpaces(elementType.getId() + ")"));
        }
      }
    }

    @Test
    void multipleElementTypes_definedInConfig() {
      var config =
          new GeneratorConfiguration(
              ConnectorMode.HYBRID,
              null,
              null,
              null,
              Set.of(
                  new ConnectorElementType(
                      Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null),
                  new ConnectorElementType(
                      Set.of(BpmnType.INTERMEDIATE_THROW_EVENT),
                      BpmnType.INTERMEDIATE_THROW_EVENT,
                      null,
                      null)),
              Map.of());
      var templates = generator.generate(MyConnectorFunction.FullyAnnotated.class, config);
      boolean hasServiceTask = false, hasMessageThrowEvent = false;
      for (var template : templates) {
        if (template.elementType().equals(ElementTypeWrapper.from(BpmnType.SERVICE_TASK))) {
          hasServiceTask = true;
        } else if (template
            .elementType()
            .equals(ElementTypeWrapper.from(BpmnType.INTERMEDIATE_THROW_EVENT))) {
          hasMessageThrowEvent = true;
        }
      }
      assertThat(templates.size()).isEqualTo(2);
      assertTrue(hasServiceTask);
      assertTrue(hasMessageThrowEvent);
    }

    @Test
    void multipleElementTypes_overriddenInConfig() {
      var config =
          new GeneratorConfiguration(
              ConnectorMode.HYBRID,
              null,
              null,
              null,
              Set.of(
                  new ConnectorElementType(
                      Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null)),
              Map.of());
      var templates =
          generator.generate(MyConnectorFunction.WithMultipleElementTypes.class, config);
      boolean hasServiceTask = false,
          hasScriptTask = false,
          hasMessageThrowEvent = false,
          hasMessageEndEvent = false;
      for (var template : templates) {
        if (template.elementType().equals(ElementTypeWrapper.from(BpmnType.SERVICE_TASK))) {
          hasServiceTask = true;
        } else if (template.elementType().equals(ElementTypeWrapper.from(BpmnType.SCRIPT_TASK))) {
          hasScriptTask = true;
        } else if (template
            .elementType()
            .equals(ElementTypeWrapper.from(BpmnType.INTERMEDIATE_THROW_EVENT))) {
          hasMessageThrowEvent = true;
        } else if (template
            .elementType()
            .equals(ElementTypeWrapper.from(BpmnType.MESSAGE_END_EVENT))) {
          hasMessageEndEvent = true;
        }
      }
      assertThat(templates.size()).isEqualTo(1);
      assertTrue(hasServiceTask);
      assertFalse(hasScriptTask);
      assertFalse(hasMessageThrowEvent);
      assertFalse(hasMessageEndEvent);
    }

    @Test
    void invalidElementType_throwsException() {
      var config =
          new GeneratorConfiguration(
              ConnectorMode.HYBRID,
              null,
              null,
              null,
              Set.of(
                  new ConnectorElementType(
                      Set.of(BpmnType.TASK), BpmnType.SERVICE_TASK, null, null),
                  new ConnectorElementType(
                      Set.of(BpmnType.INTERMEDIATE_CATCH_EVENT),
                      BpmnType.INTERMEDIATE_CATCH_EVENT,
                      null,
                      null)),
              Map.of());
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> generator.generate(MyConnectorFunction.MinimallyAnnotated.class, config));
      assertThat(exception.getMessage()).contains("Unsupported element type");
    }
  }

  @Nested
  class Properties {

    @Test
    void notAnnotated_StringProperty_hasCorrectDefaults() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Not annotated string property", template);

      assertThat(property).isInstanceOf(StringProperty.class);
      assertThat(property.getType()).isEqualTo("String");
      assertThat(property.isOptional()).isNull();
      assertThat(property.getGroup()).isEqualTo(null);
      assertThat(property.getFeel()).isEqualTo(FeelMode.optional);
      assertThat(property.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeInput("notAnnotatedStringProperty"));
      assertThat(property.getConstraints()).isNull();
    }

    @Test
    void annotated_StringProperty_definedByAnnotation() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyById("annotatedStringProperty", template);

      assertThat(property).isInstanceOf(TextProperty.class);
      assertThat(property.getType()).isEqualTo("Text");
      assertThat(property.isOptional()).isFalse();
      assertThat(property.getLabel()).isEqualTo("Annotated and renamed string property");
      assertThat(property.getGroup()).isEqualTo("group1");
      assertThat(property.getDescription()).isEqualTo("description");
      assertThat(property.getFeel()).isEqualTo(FeelMode.optional);
      assertThat(property.getBinding()).isEqualTo(new PropertyBinding.ZeebeInput("customBinding"));
      assertThat(property.getConstraints().notEmpty()).isTrue();
      assertThat(property.getConstraints().minLength()).isNull();
      assertThat(property.getConstraints().maxLength()).isNull();
      assertThat(property.getConstraints().pattern()).isNull();
    }

    @Test
    void objectProperty_hasRequiredFeelByDefault() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();

      var objectProperty = getPropertyByLabel("Object property", template);
      assertThat(objectProperty.getFeel()).isEqualTo(FeelMode.required);

      var jsonNodeProperty = getPropertyByLabel("Json node property", template);
      assertThat(jsonNodeProperty.getFeel()).isEqualTo(FeelMode.required);
    }

    @Test
    void notAnnotated_EnumProperty_hasCorrectDefaults() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Enum property", template);

      assertThat(property).isInstanceOf(DropdownProperty.class);
      assertThat(property.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) property).getChoices())
          .containsExactly(
              new DropdownChoice("Value one", "VALUE1"), new DropdownChoice("Value two", "VALUE2"));
      assertThat(property.isOptional()).isNull();
      assertThat(property.getGroup()).isEqualTo(null);
      assertThat(property.getFeel()).isEqualTo(null);
      assertThat(property.getBinding()).isEqualTo(new PropertyBinding.ZeebeInput("enumProperty"));
    }

    @Test
    void nested_addsPrefixPathByDefault() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      assertDoesNotThrow(() -> getPropertyById("nestedProperty.nestedA", template));
      assertThrows(Exception.class, () -> getPropertyById("nestedProperty.nestedB", template));
    }

    @Test
    void nested_disableAddPrefixPath() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      assertDoesNotThrow(() -> getPropertyById("nestedB", template));
      assertThrows(
          Exception.class, () -> getPropertyById("customPathNestedProperty.nestedB", template));
    }

    @Test
    void nested_groupOverride() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyById("nestedPropertyWithGroup.nestedA", template);
      assertThat(property.getGroup()).isEqualTo("customGroup");
    }

    @Test
    void nested_groupOverrideWhenChildGroupIsSet() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyById("nestedPropertyWithGroupOverride.nestedB", template);
      assertThat(property.getGroup()).isEqualTo("customGroup");
    }

    @Test
    void ignoredProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      assertThat(template.properties()).noneMatch(p -> "ignoredField".equals(p.getId()));
    }

    @Test
    void conditionalProperty_valid_equals() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Conditional property equals", template);

      assertThat(property.getCondition())
          .isEqualTo(new PropertyCondition.Equals("annotatedStringProperty", "value"));
    }

    @Test
    void conditionalProperty_valid_oneOf() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Conditional property one of", template);

      assertThat(property.getCondition())
          .isEqualTo(
              new PropertyCondition.OneOf("annotatedStringProperty", List.of("value1", "value2")));
    }

    @Test
    void duplicatePropertyId_throwsException() {
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> generator.generate(MyConnectorFunction.WithDuplicatePropertyIds.class));

      assertThat(exception.getMessage()).contains("duplicate property prop");
    }

    @Test
    void propertyWithDifferentIdAndBinding_isSupported() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyById("idNotEqualToBinding", template);

      assertThat(property.getBinding()).isInstanceOf(ZeebeInput.class);
      assertThat(((ZeebeInput) property.getBinding()).name())
          .isEqualTo("propertyWithDifferentIdAndBinding");
    }

    @Test
    void nested_conditionIsNotChanged() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("First nested sub type override value", template);

      assertThat(property.getCondition()).isInstanceOf(AllMatch.class);
      assertThat(((AllMatch) property.getCondition()).allMatch())
          .contains(new PropertyCondition.Equals("annotatedStringProperty", "value"));
    }

    @Test
    void containerType_withManualTypeOverride() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Property with type override", template);
      assertThat(property.getType()).isEqualTo("String");
    }

    @Test
    void dateProperty_defaultsToStringType() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Date property", template);
      assertThat(property.getType()).isEqualTo("String");
    }

    @Test
    void annotatedProperty_tooltipPresent() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyById("annotatedStringProperty", template);
      assertThat(property.getTooltip()).isEqualTo("tooltip");
    }

    @Test
    void booleanProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyById("booleanProperty", template);
      assertThat(property.getType()).isEqualTo("Boolean");
      assertThat(property.getBinding()).isEqualTo(new ZeebeInput("booleanProperty"));
      assertThat(property.getValue()).isEqualTo(Boolean.FALSE);
    }

    @Test
    void booleanProperty_dependants() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var dependsOnTrue = getPropertyById("dependsOnBooleanPropertyTrue", template);
      assertThat(dependsOnTrue.getCondition()).isEqualTo(new Equals("booleanProperty", true));
      var dependsOnFalse = getPropertyById("dependsOnBooleanPropertyFalse", template);
      assertThat(dependsOnFalse.getCondition()).isEqualTo(new Equals("booleanProperty", false));
    }

    @Test
    void supportsExtensionProperties() {
      var template =
          generator
              .generate(MyConnectorFunction.MinimallyAnnotatedWithExtensionProperties.class)
              .getFirst();

      assertThat(template.properties())
          .filteredOn(
              p ->
                  p.getBinding().type().equals("zeebe:property")
                      && ((PropertyBinding.ZeebeProperty) p.getBinding())
                          .name()
                          .startsWith("myExtensionProperty"))
          .hasSize(2)
          .satisfiesExactlyInAnyOrder(
              p -> {
                assertThat(p).isInstanceOf(HiddenProperty.class);
                assertThat(p.getBinding())
                    .isEqualTo(new PropertyBinding.ZeebeProperty("myExtensionProperty1"));
                assertThat(p.getValue()).isEqualTo("value1");
                assertThat(p.getCondition()).isNull();
              },
              p -> {
                assertThat(p).isInstanceOf(HiddenProperty.class);
                assertThat(p.getBinding())
                    .isEqualTo(new PropertyBinding.ZeebeProperty("myExtensionProperty2"));
                assertThat(p.getValue()).isEqualTo("value2");
                assertThat(p.getCondition()).isEqualTo(new Equals("booleanProperty", false));
              });
    }
  }

  @Nested
  class SealedTypes {

    @Test
    void nonAnnotated_sealedType_hasCorrectDefaults() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var discriminatorProperty =
          template.properties().stream()
              .filter(p -> "Non annotated sealed type".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(discriminatorProperty).isInstanceOf(DropdownProperty.class);
      assertThat(discriminatorProperty.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) discriminatorProperty).getChoices())
          .containsExactlyInAnyOrder(
              new DropdownChoice("First sub type", "firstsubtype"),
              new DropdownChoice("Second sub type", "secondsubtype"),
              new DropdownChoice("Nested sealed type", "nestedsealedtype"));

      var firstSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "First sub type value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(firstSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "firstsubtype"));

      var secondSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "Nested sealed type".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(secondSubTypeValueProperty.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "nestedsealedtype"));

      var nestedSubTypeDiscriminator =
          template.properties().stream()
              .filter(p -> "Nested sealed type".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();
      assertThat(nestedSubTypeDiscriminator.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "nestedsealedtype"));

      var nestedSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "Third sub type value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(nestedSubTypeValueProperty.getCondition()).isInstanceOf(AllMatch.class);
      assertThat(((AllMatch) nestedSubTypeValueProperty.getCondition()).allMatch())
          .containsExactlyInAnyOrder(
              new Equals(discriminatorProperty.getId(), "nestedsealedtype"),
              new Equals(nestedSubTypeDiscriminator.getId(), "nestedsubtype"));
    }

    @Test
    void annotated_sealedType_followsAnnotations() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var discriminatorProperty =
          template.properties().stream()
              .filter(p -> "Annotated type override".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(discriminatorProperty).isInstanceOf(DropdownProperty.class);
      assertThat(discriminatorProperty.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) discriminatorProperty).getChoices())
          .containsExactlyInAnyOrder(
              new DropdownChoice("First annotated override", "firstAnnotatedOverride"),
              new DropdownChoice("Second annotated override", "secondAnnotatedOverride"),
              new DropdownChoice(
                  "Nested annotated sealed type override", "nestedAnnotatedSealedType"));

      assertThat(discriminatorProperty.getId())
          .isEqualTo("annotatedSealedType.annotatedTypeOverrideCustomId");
      assertThat(((ZeebeInput) discriminatorProperty.getBinding()).name())
          .isEqualTo("annotatedSealedType.annotatedTypeOverride");

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

      var nestedSubTypeDiscriminator =
          template.properties().stream()
              .filter(p -> "Nested discriminator property".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(nestedSubTypeDiscriminator.getCondition())
          .isEqualTo(new Equals(discriminatorProperty.getId(), "nestedAnnotatedSealedType"));

      var nestedSubTypeValueProperty =
          template.properties().stream()
              .filter(p -> "First nested sub type override value".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(nestedSubTypeValueProperty.getCondition()).isInstanceOf(AllMatch.class);
      assertThat(((AllMatch) nestedSubTypeValueProperty.getCondition()).allMatch())
          .containsExactlyInAnyOrder(
              new Equals(discriminatorProperty.getId(), "nestedAnnotatedSealedType"),
              new Equals(nestedSubTypeDiscriminator.getId(), "firstNestedSubTypeOverride"),
              new Equals("annotatedStringProperty", "value"));
    }

    @Test
    void discriminatorProperty_withCondition() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var discriminatorProperty =
          template.properties().stream()
              .filter(p -> "Conditional discriminator".equals(p.getLabel()))
              .findFirst()
              .orElseThrow();

      assertThat(discriminatorProperty).isInstanceOf(DropdownProperty.class);
      assertThat(discriminatorProperty.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) discriminatorProperty).getChoices())
          .containsExactlyInAnyOrder(
              new DropdownChoice("Conditional sub type", "conditionalSubType"));

      assertThat(discriminatorProperty.getCondition())
          .isEqualTo(new Equals("annotatedStringProperty", "value"));
    }
  }

  @Nested
  class PropertyGroups {

    @Test
    void propertyGroups_unordered() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      checkPropertyGroups(
          List.of(
              Map.entry("customGroup", "Custom group"),
              Map.entry("group1", "Group 1"),
              Map.entry("group2", "Group 2"),
              Map.entry("output", "Output mapping"),
              Map.entry("error", "Error handling"),
              Map.entry("retries", "Retries"),
              Map.entry("connector", "Connector")),
          template,
          false);
    }

    @Test
    void allInPredefinedGroups() {
      var template =
          generator.generate(MyConnectorFunction.AllPropertiesInPredefinedGroups.class).getFirst();
      checkPropertyGroups(
          List.of(
              Map.entry("predefinedGroup1", "Predefined Group One"),
              Map.entry("predefinedGroup2", "Predefined Group Two"),
              Map.entry("predefinedGroup3", "Predefined Group Three"),
              Map.entry("connector", "Connector"),
              Map.entry("output", "Output mapping"),
              Map.entry("error", "Error handling"),
              Map.entry("retries", "Retries")),
          template,
          false);
    }

    @Test
    void propertyGroups_orderedAndLabeledByAnnotation() {
      var template = generator.generate(MyConnectorFunction.FullyAnnotated.class).getFirst();
      checkPropertyGroups(
          List.of(
              Map.entry("group2", "Group Two"),
              Map.entry("group1", "Group One"),
              Map.entry("customGroup", "Custom group"),
              Map.entry("connector", "Connector"),
              Map.entry("output", "Output mapping"),
              Map.entry("error", "Error handling"),
              Map.entry("retries", "Retries")),
          template,
          true);
    }

    @Test
    void propertyGroupContents_definedByTemplatePropertyAnnotation() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
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

    @Test
    void hybridMode_groupPresentAndIsOnTop() {
      var template =
          generator
              .generate(
                  MyConnectorFunction.MinimallyAnnotated.class,
                  new GeneratorConfiguration(
                      ConnectorMode.HYBRID, null, null, null, null, Map.of()))
              .getFirst();
      checkPropertyGroups(
          List.of(
              Map.entry("taskDefinitionType", "Task definition type"),
              Map.entry("customGroup", "Custom group"),
              Map.entry("group2", "Group 2"),
              Map.entry("group1", "Group 1"),
              Map.entry("connector", "Connector"),
              Map.entry("output", "Output mapping"),
              Map.entry("error", "Error handling"),
              Map.entry("retries", "Retries")),
          template,
          true);
    }

    @Test
    void tooltip_definedByPropertyGroupAnnotation() {
      var template = generator.generate(MyConnectorFunction.FullyAnnotated.class).getFirst();
      var group1 =
          template.groups().stream().filter(g -> "group1".equals(g.id())).findFirst().orElseThrow();
      assertThat(group1.tooltip()).isEqualTo("Group One Tooltip");

      var group2 =
          template.groups().stream().filter(g -> "group2".equals(g.id())).findFirst().orElseThrow();
      assertThat(group2.tooltip()).isNull();
    }

    @Test
    void openByDefault_definedByPropertyGroupAnnotation() {
      var template = generator.generate(MyConnectorFunction.FullyAnnotated.class).getFirst();
      var group1 =
          template.groups().stream().filter(g -> "group1".equals(g.id())).findFirst().orElseThrow();
      assertThat(group1.openByDefault()).isFalse();

      var group2 =
          template.groups().stream().filter(g -> "group2".equals(g.id())).findFirst().orElseThrow();
      assertThat(group2.openByDefault()).isNull();
    }
  }

  @Nested
  class ValidationConstraints {

    @Test
    void validationPresent_onlyPattern() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Property with pattern", template);
      assertThat(property.getConstraints()).isNotNull();
      assertThat(property.getConstraints().pattern())
          .isEqualTo(new Pattern("^(=.*|[0-9]+|\\{\\{secrets\\..+}})$", "Pattern violated"));
      assertThat(property.getConstraints().minLength()).isNull();
      assertThat(property.getConstraints().maxLength()).isNull();
      assertThat(property.getConstraints().notEmpty()).isNull();
    }

    @Test
    void validationNotPresent() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Not annotated string property", template);
      assertThat(property.getConstraints()).isNull();
    }

    @Test
    void validationPresent_minMaxSize() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Property with min max", template);
      assertThat(property.getConstraints()).isNotNull();
      assertThat(property.getConstraints().minLength()).isEqualTo(1);
      assertThat(property.getConstraints().maxLength()).isEqualTo(10);
    }

    @Test
    void validationPresent_maxSizeOnly() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var property = getPropertyByLabel("Property with max size", template);
      assertThat(property.getConstraints()).isNotNull();
      assertThat(property.getConstraints().minLength()).isNull();
      assertThat(property.getConstraints().maxLength()).isEqualTo(10);
    }

    @Test
    void validationPresent_notEmpty_stringProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var notEmptyProperty = getPropertyByLabel("String property with not empty", template);
      assertThat(notEmptyProperty.getConstraints()).isNotNull();
      assertThat(notEmptyProperty.getConstraints().notEmpty()).isTrue();
      assertThat(notEmptyProperty.getConstraints().minLength()).isNull();
      assertThat(notEmptyProperty.getConstraints().maxLength()).isNull();

      var notBlankProperty = getPropertyByLabel("String property with not blank", template);
      assertThat(notBlankProperty.getConstraints()).isNotNull();
      assertThat(notBlankProperty.getConstraints().notEmpty()).isTrue();
      assertThat(notBlankProperty.getConstraints().minLength()).isNull();
      assertThat(notBlankProperty.getConstraints().maxLength()).isNull();
    }

    @Test
    void validationPresent_notEmpty_objectProperty() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var notEmptyProperty = getPropertyByLabel("Object property with not null", template);
      assertThat(notEmptyProperty.getConstraints()).isNotNull();
      assertThat(notEmptyProperty.getConstraints().notEmpty()).isTrue();
      assertThat(notEmptyProperty.getConstraints().minLength()).isNull();
      assertThat(notEmptyProperty.getConstraints().maxLength()).isNull();
    }

    @Test
    void validationPresent_Pattern_optional() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var mayBeEmptyOrRegexValidated = getPropertyById("mayBeEmptyOrRegexValidated", template);
      assertThat(mayBeEmptyOrRegexValidated.getConstraints()).isNotNull();
      assertThat(mayBeEmptyOrRegexValidated.getConstraints().notEmpty()).isFalse();
      assertThat(mayBeEmptyOrRegexValidated.getConstraints().minLength()).isNull();
      assertThat(mayBeEmptyOrRegexValidated.getConstraints().maxLength()).isNull();
    }

    @Test
    void validationPresent_Pattern_optional_jakarta() {
      var template = generator.generate(MyConnectorFunction.MinimallyAnnotated.class).getFirst();
      var mayBeEmptyOrRegexValidated =
          getPropertyById("mayBeEmptyOrRegexValidatedJakartaStyle", template);
      assertThat(mayBeEmptyOrRegexValidated.getConstraints()).isNotNull();
      assertThat(mayBeEmptyOrRegexValidated.getConstraints().notEmpty()).isFalse();
      assertThat(mayBeEmptyOrRegexValidated.getConstraints().minLength()).isNull();
      assertThat(mayBeEmptyOrRegexValidated.getConstraints().maxLength()).isNull();
    }
  }

  @Nested
  class Icons {

    @Test
    void svgIcon_classpathFile() throws IOException {
      Path expectedIconPath =
          new File(
                  OutboundClassBasedTemplateGeneratorTest.class
                      .getClassLoader()
                      .getResource("my-connector-icon.svg")
                      .getFile())
              .toPath();
      var expectedIconString =
          "data:image/svg+xml;base64,"
              + Base64.getEncoder().encodeToString(readAllBytes(expectedIconPath));

      var template =
          generator.generate(MyConnectorFunction.MinimallyAnnotatedWithSvgIcon.class).getFirst();
      var icon = template.icon();

      assertThat(icon.contents()).isEqualTo(expectedIconString);
    }

    @Test
    void pngIcon_classpathFile() throws IOException {
      Path expectedIconPath =
          new File(
                  OutboundClassBasedTemplateGeneratorTest.class
                      .getClassLoader()
                      .getResource("my-connector-icon.png")
                      .getFile())
              .toPath();
      var expectedIconString =
          "data:image/png;base64,"
              + Base64.getEncoder().encodeToString(readAllBytes(expectedIconPath));

      var template =
          generator.generate(MyConnectorFunction.MinimallyAnnotatedWithPngIcon.class).getFirst();
      var icon = template.icon();

      assertThat(icon.contents()).isEqualTo(expectedIconString);
    }
  }

  @Nested
  class OperationAnnotated {

    @Test
    void operationAnnotated() {
      var template = generator.generate(OperationAnnotatedConnector.class).getFirst();
      assertThat(template.id()).isNotNull();
      assertThat(template.id()).isEqualTo(OperationAnnotatedConnector.ID);
      assertThat(template.name()).isEqualTo(OperationAnnotatedConnector.NAME);
      assertThat(template.properties()).hasSize(14);

      DropdownProperty operationProperty =
          (DropdownProperty) getPropertyById("operation", template);
      assertThat(operationProperty.getChoices()).isNotNull();
      assertThat(operationProperty.getValue()).isEqualTo("operation-1");
      assertThat(operationProperty.getChoices())
          .containsExactlyInAnyOrder(
              new DropdownChoice("Operation 1", "operation-1"),
              new DropdownChoice("Operation 2", "operation-2"),
              new DropdownChoice("Operation 3", "operation-3"));

      var propOp1P1 = getPropertyById("operation-1:p1", template);
      assertThat(propOp1P1.getCondition()).isNotNull();
      assertThat(propOp1P1.getGroup()).isNotNull();
      assertThat(propOp1P1.getGroup()).isEqualTo("customGroup");

      var customGroup =
          template.groups().stream().filter(g -> g.id().equals("customGroup")).findFirst().get();
      assertThat(customGroup.id()).isEqualTo("customGroup");
      assertThat(customGroup.label()).isEqualTo("Custom Group");

      // Verify that the referenced operation property is properly prefixed
      var propOp1P2 = getPropertyById("operation-1:param2", template);
      assertThat(propOp1P2.getGroup()).isEqualTo("operation");
      assertThat(propOp1P2.getCondition()).isInstanceOf(AllMatch.class);
      assertThat(((AllMatch) propOp1P2.getCondition()).allMatch())
          .containsExactlyInAnyOrder(
              new Equals("operation-1:p1", "myValue"), new Equals("operation", "operation-1"));

      var propOp3P1 = getPropertyById("operation-3:p1", template);
      assertThat(propOp3P1).isNotNull();
      var propOp3P2 = getPropertyById("operation-3:param2", template);
      assertThat(propOp3P2).isNotNull();

      StringProperty propOp3Header =
          (StringProperty) getPropertyById("operation-3:myHeader", template);
      assertThat(propOp3Header.getBinding()).isInstanceOf(PropertyBinding.ZeebeTaskHeader.class);
      assertThat(((ZeebeTaskHeader) propOp3Header.getBinding()).key()).isEqualTo("test-header");
      assertThat(propOp3Header.getLabel()).isEqualTo("My Header");
      assertThat(propOp3Header.getFeel()).isEqualTo(FeelMode.optional);
      assertThat(propOp3Header.getValue()).isEqualTo("my-default-value");
    }

    @Test
    void singleOperationAnnotated() {
      var template = generator.generate(SingleOperationAnnotatedConnector.class).getFirst();
      assertThat(template.id()).isNotNull();
      assertThat(template.id()).isEqualTo(SingleOperationAnnotatedConnector.ID);
      assertThat(template.name()).isEqualTo(SingleOperationAnnotatedConnector.NAME);

      HiddenProperty operationProperty = (HiddenProperty) getPropertyById("operation", template);
      assertThat(operationProperty.getValue()).isEqualTo("operation-1");
    }

    @Test
    void operationWithPrimitiveParametersAnnotated() {
      var template =
          generator.generate(OperationAnnotatedConnectorWithPrimitiveTypes.class).getFirst();
      assertThat(template.id()).isNotNull();

      NumberProperty propertyA = (NumberProperty) getPropertyById("add:a", template);
      NumberProperty propertyB = (NumberProperty) getPropertyById("add:b", template);
      assertThat(propertyA.getType()).isEqualTo("Number");
      assertThat(propertyB.getType()).isEqualTo("Number");
      assertThat(propertyA.getBinding()).isEqualTo(new ZeebeInput("a"));
      assertThat(propertyB.getBinding()).isEqualTo(new ZeebeInput("b"));

      NumberProperty propertyAWithTemplateProperty =
          (NumberProperty) getPropertyById("addWithVariableAndTemplateProperty:a", template);
      NumberProperty propertyBWithTemplateProperty =
          (NumberProperty) getPropertyById("addWithVariableAndTemplateProperty:b", template);
      assertThat(propertyAWithTemplateProperty.getType()).isEqualTo("Number");
      assertThat(propertyBWithTemplateProperty.getType()).isEqualTo("Number");
      assertThat(propertyAWithTemplateProperty.getBinding()).isEqualTo(new ZeebeInput("a"));
      assertThat(propertyBWithTemplateProperty.getBinding()).isEqualTo(new ZeebeInput("b"));
      assertThat(propertyAWithTemplateProperty.getLabel()).isEqualTo("a prop");
      assertThat(propertyBWithTemplateProperty.getLabel()).isEqualTo("b prop");

      // TODO: all header params are currently forced to String, even if the method parameter is
      // of a different type. Do we want to change this?

      StringProperty propertyAWithHeader =
          (StringProperty) getPropertyById("addWithHeader:a", template);
      StringProperty propertyBWithHeader =
          (StringProperty) getPropertyById("addWithHeader:b", template);
      assertThat(propertyAWithHeader.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeTaskHeader("a"));
      assertThat(propertyBWithHeader.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeTaskHeader("b"));

      StringProperty propertyAWithHeaderAndTemplateProperty =
          (StringProperty) getPropertyById("addWithHeaderAndTemplateProperty:a", template);
      StringProperty propertyBWithHeaderAndTemplateProperty =
          (StringProperty) getPropertyById("addWithHeaderAndTemplateProperty:b", template);
      assertThat(propertyAWithHeaderAndTemplateProperty.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeTaskHeader("a"));
      assertThat(propertyBWithHeaderAndTemplateProperty.getBinding())
          .isEqualTo(new PropertyBinding.ZeebeTaskHeader("b"));
      assertThat(propertyAWithHeaderAndTemplateProperty.getLabel()).isEqualTo("a prop");
      assertThat(propertyBWithHeaderAndTemplateProperty.getLabel()).isEqualTo("b prop");
    }
  }
}
