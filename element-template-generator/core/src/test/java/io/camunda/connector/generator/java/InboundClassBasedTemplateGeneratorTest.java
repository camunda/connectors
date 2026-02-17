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
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.generator.BaseTest;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.GeneratorConfiguration.GenerationFeature;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyBinding.MessageProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeSubscriptionProperty;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.IsActive;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.example.inbound.MyConnectorExecutable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class InboundClassBasedTemplateGeneratorTest extends BaseTest {

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  @Nested
  class Basic {

    @Test
    void connectorTypeProperty() {
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class);
      for (var template : templates) {
        var property =
            template.properties().stream()
                .filter(p -> p.getBinding().equals(ZeebeProperty.TYPE))
                .findFirst()
                .orElseThrow();
        assertThat(property.getType()).isEqualTo("Hidden");
        assertThat(property.getValue()).isEqualTo("my-inbound-connector-type");
      }
    }

    @Test
    void resultVariableProperty() {
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Result variable", template);
        assertThat(property.getType()).isEqualTo("String");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
        assertThat(property.getFeel()).isNull();
      }
    }

    @Test
    void resultVariablePropertyWithValue() {
      var templates =
          generator.generate(MyConnectorExecutable.MinimallyAnnotatedWithResultVariable.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Result variable", template);
        assertThat(property.getType()).isEqualTo("String");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
        assertThat(property.getFeel()).isNull();
        assertThat(property.getValue()).isEqualTo("myResultVariable");
      }
    }

    @Test
    void resultExpressionProperty() {
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Result expression", template);
        assertThat(property.getType()).isEqualTo("Text");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
        assertThat(property.getFeel()).isEqualTo(FeelMode.required);
      }
    }

    @Test
    void resultExpressionPropertyWithValue() {
      var templates =
          generator.generate(MyConnectorExecutable.MinimallyAnnotatedWithResultExpression.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Result expression", template);
        assertThat(property.getType()).isEqualTo("Text");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
        assertThat(property.getFeel()).isEqualTo(FeelMode.required);
        assertThat(property.getValue()).isEqualTo("={ myResponse: request }");
      }
    }

    @Test
    void activationConditionProperty() {
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Activation condition", template);
        assertThat(property.getType()).isEqualTo("String");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
        assertThat(((ZeebeProperty) property.getBinding()).name()).isEqualTo("activationCondition");
        assertThat(property.getFeel()).isEqualTo(FeelMode.required);
      }
    }
  }

  @Nested
  class ElementTypes {

    @Test
    void allInboundElementTypesAreGeneratedByDefault() {
      // given
      List<ConnectorElementType> expectedTypes =
          List.of(
              new ConnectorElementType(
                  Set.of(BpmnType.INTERMEDIATE_CATCH_EVENT, BpmnType.INTERMEDIATE_THROW_EVENT),
                  BpmnType.INTERMEDIATE_CATCH_EVENT,
                  null,
                  null),
              new ConnectorElementType(
                  Set.of(BpmnType.START_EVENT), BpmnType.START_EVENT, null, null),
              new ConnectorElementType(
                  Set.of(BpmnType.START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null),
              new ConnectorElementType(
                  Set.of(BpmnType.BOUNDARY_EVENT), BpmnType.BOUNDARY_EVENT, null, null));

      // when
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class);

      // then
      assertThat(templates).hasSize(expectedTypes.size());
      for (var template : templates) {
        var expectedType =
            expectedTypes.stream()
                .filter(t -> t.elementType().equals(template.elementType().originalType()))
                .findFirst()
                .orElseThrow();
        assertThat(template.appliesTo())
            .containsExactlyInAnyOrderElementsOf(
                expectedType.appliesTo().stream().map(BpmnType::getName).toList());
      }
    }

    @Test
    void messageTypes_haveMessageIdProperty() {
      // given
      var type =
          new ConnectorElementType(
              Set.of(BpmnType.START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null);
      var config =
          new GeneratorConfiguration(
              ConnectorMode.NORMAL, null, null, null, Set.of(type), Map.of());

      // when
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config);

      // then
      assertThat(templates).hasSize(1);
      var template = templates.getFirst();
      var property = getPropertyById("messageNameUuid", template);
      assertThat(property).isNotNull();
      assertThat(property.getType()).isEqualTo("Hidden");
      assertThat(property.getBinding().type()).isEqualTo("bpmn:Message#property");
      assertThat(((MessageProperty) property.getBinding()).name()).isEqualTo("name");
      assertThat(property.getGeneratedValue()).isNotNull();
    }

    @Test
    void nonMessageTypes_dontHaveMessageIdProperty() {
      // given
      var type =
          new ConnectorElementType(Set.of(BpmnType.START_EVENT), BpmnType.START_EVENT, null, null);
      var config =
          new GeneratorConfiguration(
              ConnectorMode.NORMAL, null, null, null, Set.of(type), Map.of());

      // when
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config);

      // then
      assertThat(templates).hasSize(1);
      var template = templates.getFirst();
      assertThrows(Exception.class, () -> getPropertyById("messageNameUuid", template));
    }

    @Test
    void intermediateMessageEvents_haveCorrelationProperties() {
      // given
      var type1 =
          new ConnectorElementType(
              Set.of(BpmnType.INTERMEDIATE_CATCH_EVENT),
              BpmnType.INTERMEDIATE_CATCH_EVENT,
              null,
              null);
      var type2 =
          new ConnectorElementType(
              Set.of(BpmnType.BOUNDARY_EVENT), BpmnType.BOUNDARY_EVENT, null, null);
      var config =
          new GeneratorConfiguration(
              ConnectorMode.NORMAL, null, null, null, Set.of(type1, type2), Map.of());

      // when
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config);

      // then
      assertThat(templates).hasSize(2);
      for (var template : templates) {
        var correlationKeyProperty = getPropertyById("correlationKeyProcess", template);
        assertThat(correlationKeyProperty).isNotNull();
        assertThat(correlationKeyProperty.getType()).isEqualTo("String");
        assertThat(correlationKeyProperty.getBinding().type())
            .isEqualTo("bpmn:Message#zeebe:subscription#property");
        assertThat(((ZeebeSubscriptionProperty) correlationKeyProperty.getBinding()).name())
            .isEqualTo("correlationKey");

        var correlationKeyExpressionProperty = getPropertyById("correlationKeyPayload", template);
        assertThat(correlationKeyExpressionProperty).isNotNull();
        assertThat(correlationKeyExpressionProperty.getType()).isEqualTo("String");
        assertThat(correlationKeyExpressionProperty.getBinding().type())
            .isEqualTo("zeebe:property");
        assertThat(((ZeebeProperty) correlationKeyExpressionProperty.getBinding()).name())
            .isEqualTo("correlationKeyExpression");
        assertThat(correlationKeyExpressionProperty.getCondition()).isNull();
      }
    }

    @Test
    void messageStartEvent_hasCorrelationProperties() {
      // given
      var type =
          new ConnectorElementType(
              Set.of(BpmnType.START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null);
      var config =
          new GeneratorConfiguration(
              ConnectorMode.NORMAL, null, null, null, Set.of(type), Map.of());

      // when
      var templates = generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config);

      // then
      assertThat(templates).hasSize(1);
      var template = templates.getFirst();
      var correlationRequiredProperty = getPropertyById("correlationRequired", template);
      assertThat(correlationRequiredProperty).isNotNull();
      assertThat(correlationRequiredProperty.getType()).isEqualTo("Dropdown");
      assertThat(((DropdownProperty) correlationRequiredProperty).getChoices())
          .containsExactlyInAnyOrder(
              new DropdownChoice("Correlation not required", "notRequired"),
              new DropdownChoice("Correlation required", "required"));

      var correlationKeyProcessProperty = getPropertyById("correlationKeyProcess", template);
      assertThat(correlationKeyProcessProperty).isNotNull();
      assertThat(correlationKeyProcessProperty.getType()).isEqualTo("String");
      assertThat(correlationKeyProcessProperty.getBinding().type())
          .isEqualTo("bpmn:Message#zeebe:subscription#property");
      assertThat(((ZeebeSubscriptionProperty) correlationKeyProcessProperty.getBinding()).name())
          .isEqualTo("correlationKey");
      assertThat(correlationKeyProcessProperty.getCondition())
          .isEqualTo(new Equals("correlationRequired", "required"));

      var correlationKeyPayloadProperty = getPropertyById("correlationKeyPayload", template);
      assertThat(correlationKeyPayloadProperty).isNotNull();
      assertThat(correlationKeyPayloadProperty.getType()).isEqualTo("String");
      assertThat(correlationKeyPayloadProperty.getBinding().type()).isEqualTo("zeebe:property");
      assertThat(((ZeebeProperty) correlationKeyPayloadProperty.getBinding()).name())
          .isEqualTo("correlationKeyExpression");
      assertThat(correlationKeyPayloadProperty.getCondition()).isNotNull();
      assertThat(correlationKeyPayloadProperty.getCondition())
          .isEqualTo(new Equals("correlationRequired", "required"));

      var messageIdExpressionProperty = getPropertyById("messageIdExpression", template);
      assertThat(messageIdExpressionProperty).isNotNull();
      assertThat(messageIdExpressionProperty.getType()).isEqualTo("String");
      assertThat(messageIdExpressionProperty.getBinding().type()).isEqualTo("zeebe:property");
      assertThat(((ZeebeProperty) messageIdExpressionProperty.getBinding()).name())
          .isEqualTo("messageIdExpression");
    }
  }

  @Test
  void stringProperty_hasCorrectDefaults() {
    // given
    var type =
        new ConnectorElementType(
            Set.of(BpmnType.START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null);
    var config =
        new GeneratorConfiguration(ConnectorMode.NORMAL, null, null, null, Set.of(type), Map.of());

    // when
    var template =
        generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config).getFirst();

    var property = getPropertyByLabel("Prop 1", template);

    assertThat(property).isInstanceOf(StringProperty.class);
    assertThat(property.getType()).isEqualTo("String");
    assertThat(property.isOptional()).isFalse();
    assertThat(property.getFeel()).isEqualTo(null);
    assertThat(property.getBinding()).isEqualTo(new PropertyBinding.ZeebeProperty("prop1"));
    assertThat(property.getConstraints()).isNull();
  }

  @Nested
  class Deduplication {

    @Test
    void deduplicationFeatureFlagNotSet_shouldNotAddDeduplicationProperties() {
      // given
      var type =
          new ConnectorElementType(
              Set.of(BpmnType.START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null);
      var config =
          new GeneratorConfiguration(
              ConnectorMode.NORMAL, null, null, null, Set.of(type), Map.of());

      // when
      var template =
          generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config).getFirst();

      // then
      assertThrows(Exception.class, () -> assertDeduplicationProperties(template));
    }

    @Test
    void deduplicationFeatureFlagTrue_shouldAddDeduplicationProperties() {
      // given
      var type =
          new ConnectorElementType(
              Set.of(BpmnType.START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null);
      var config =
          new GeneratorConfiguration(
              ConnectorMode.NORMAL,
              null,
              null,
              null,
              Set.of(type),
              Map.of(GenerationFeature.INBOUND_DEDUPLICATION, true));

      // when
      var template =
          generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config).getFirst();

      // then
      assertDeduplicationProperties(template);
    }

    @Test
    void deduplicationFeatureFlagFalse_shouldNotAddDeduplicationProperties() {
      // given
      var type =
          new ConnectorElementType(
              Set.of(BpmnType.START_EVENT), BpmnType.MESSAGE_START_EVENT, null, null);
      var config =
          new GeneratorConfiguration(
              ConnectorMode.NORMAL,
              null,
              null,
              null,
              Set.of(type),
              Map.of(GenerationFeature.INBOUND_DEDUPLICATION, false));

      // when
      var template =
          generator.generate(MyConnectorExecutable.MinimallyAnnotated.class, config).getFirst();

      // then
      assertThrows(Exception.class, () -> assertDeduplicationProperties(template));
    }

    private void assertDeduplicationProperties(ElementTemplate template) {
      var manualModeFlagProperty = getPropertyById("deduplicationModeManualFlag", template);
      assertThat(manualModeFlagProperty).isNotNull();
      assertThat(manualModeFlagProperty.getType()).isEqualTo("Boolean");
      assertThat(manualModeFlagProperty.getBinding().type()).isEqualTo("zeebe:property");
      assertThat(((ZeebeProperty) manualModeFlagProperty.getBinding()).name())
          .isEqualTo("deduplicationModeManualFlag");
      assertThat(manualModeFlagProperty.getValue()).isEqualTo(Boolean.FALSE);

      var manualModeProperty = getPropertyById("deduplicationModeManual", template);
      assertThat(manualModeProperty).isNotNull();
      assertThat(manualModeProperty.getType()).isEqualTo("Hidden");
      assertThat(manualModeProperty.getBinding().type()).isEqualTo("zeebe:property");
      assertThat(((ZeebeProperty) manualModeProperty.getBinding()).name())
          .isEqualTo("deduplicationMode");
      assertThat(manualModeProperty.getValue()).isEqualTo("MANUAL");
      assertThat(manualModeProperty.getCondition())
          .isEqualTo(new IsActive("deduplicationId", true));

      var autoModeProperty = getPropertyById("deduplicationModeAuto", template);
      assertThat(autoModeProperty).isNotNull();
      assertThat(autoModeProperty.getType()).isEqualTo("Hidden");
      assertThat(autoModeProperty.getBinding().type()).isEqualTo("zeebe:property");
      assertThat(((ZeebeProperty) autoModeProperty.getBinding()).name())
          .isEqualTo("deduplicationMode");
      assertThat(autoModeProperty.getValue()).isEqualTo("AUTO");
      assertThat(autoModeProperty.getCondition()).isEqualTo(new IsActive("deduplicationId", false));

      var deduplicationKeyProperty = getPropertyById("deduplicationId", template);
      assertThat(deduplicationKeyProperty).isNotNull();
      assertThat(deduplicationKeyProperty.getType()).isEqualTo("String");
      assertThat(deduplicationKeyProperty.getBinding().type()).isEqualTo("zeebe:property");
      assertThat(((ZeebeProperty) deduplicationKeyProperty.getBinding()).name())
          .isEqualTo("deduplicationId");
      assertThat(deduplicationKeyProperty.getCondition())
          .isEqualTo(new Equals("deduplicationModeManualFlag", true));
    }
  }

  @Test
  void supportsExtensionProperties() {
    var template =
        generator
            .generate(MyConnectorExecutable.MinimallyAnnotatedWithExtensionProperties.class)
            .getFirst();

    assertThat(template.properties())
        .filteredOn(
            p ->
                p.getBinding().type().equals("zeebe:property")
                    && ((ZeebeProperty) p.getBinding()).name().startsWith("myExtensionProperty"))
        .hasSize(2)
        .satisfiesExactlyInAnyOrder(
            p -> {
              assertThat(p).isInstanceOf(HiddenProperty.class);
              assertThat(p.getBinding()).isEqualTo(new ZeebeProperty("myExtensionProperty1"));
              assertThat(p.getValue()).isEqualTo("value1");
              assertThat(p.getCondition()).isNull();
            },
            p -> {
              assertThat(p).isInstanceOf(HiddenProperty.class);
              assertThat(p.getBinding()).isEqualTo(new ZeebeProperty("myExtensionProperty2"));
              assertThat(p.getValue()).isEqualTo("value2");
              assertThat(p.getCondition())
                  .isEqualTo(new PropertyCondition.Equals("prop1", "value1"));
            });
  }
}
