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
import io.camunda.connector.generator.dsl.BpmnType;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding.MessageProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.java.example.inbound.MyConnectorExecutable;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class InboundClassBasedTemplateGeneratorTest extends BaseTest {

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  @Nested
  class Basic {

    @Test
    void connectorTypeProperty() {
      var templates = generator.generate(MyConnectorExecutable.class);
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
      var templates = generator.generate(MyConnectorExecutable.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Result variable", template);
        assertThat(property.getType()).isEqualTo("String");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
        assertThat(property.getFeel()).isNull();
      }
    }

    @Test
    void resultExpressionProperty() {
      var templates = generator.generate(MyConnectorExecutable.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Result expression", template);
        assertThat(property.getType()).isEqualTo("Text");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
        assertThat(property.getFeel()).isEqualTo(FeelMode.required);
      }
    }

    @Test
    void activationConditionProperty() {
      var templates = generator.generate(MyConnectorExecutable.class);
      for (var template : templates) {
        var property = getPropertyByLabel("Activation condition", template);
        assertThat(property.getType()).isEqualTo("String");
        assertThat(property.getBinding().type()).isEqualTo("zeebe:property");
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
      var templates = generator.generate(MyConnectorExecutable.class);

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
      var config = new GeneratorConfiguration(ConnectorMode.NORMAL, null, null, null, Set.of(type));

      // when
      var templates = generator.generate(MyConnectorExecutable.class, config);

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
      var config = new GeneratorConfiguration(ConnectorMode.NORMAL, null, null, null, Set.of(type));

      // when
      var templates = generator.generate(MyConnectorExecutable.class, config);

      // then
      assertThat(templates).hasSize(1);
      var template = templates.getFirst();
      assertThrows(Exception.class, () -> getPropertyById("messageNameUuid", template));
    }
  }
}
