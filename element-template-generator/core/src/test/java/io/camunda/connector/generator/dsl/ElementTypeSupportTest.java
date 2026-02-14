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
package io.camunda.connector.generator.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.BaseTest;
import org.junit.jupiter.api.Test;

public class ElementTypeSupportTest extends BaseTest {

  @Test
  void serviceTask() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.TASK)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .SERVICE_TASK)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.TASK.getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.SERVICE_TASK
                .getName());
    assertThat(template.elementType().eventDefinition()).isNull();
  }

  @Test
  void intermediateThrowEvent() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .INTERMEDIATE_THROW_EVENT)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .INTERMEDIATE_THROW_EVENT)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                .INTERMEDIATE_THROW_EVENT
                .getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                .INTERMEDIATE_THROW_EVENT
                .getName());
    assertThat(template.elementType().eventDefinition()).isEqualTo("bpmn:MessageEventDefinition");
  }

  @Test
  void scriptTask() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.TASK)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.SCRIPT_TASK)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.TASK.getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.SCRIPT_TASK
                .getName());
    assertThat(template.elementType().eventDefinition()).isNull();
  }

  @Test
  void sendTask() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.TASK)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.SEND_TASK)
            .build();
    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.TASK.getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.SEND_TASK
                .getName());
    assertThat(template.elementType().eventDefinition()).isNull();
  }

  @Test
  void messageEndEvent() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.END_EVENT)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .MESSAGE_END_EVENT)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.END_EVENT
                .getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                .MESSAGE_END_EVENT
                .getName());
    assertThat(template.elementType().eventDefinition()).isEqualTo("bpmn:MessageEventDefinition");
  }

  @Test
  void plainStartEvent() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.START_EVENT)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.START_EVENT)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.START_EVENT
                .getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.START_EVENT
                .getName());
    assertThat(template.elementType().eventDefinition()).isNull();
  }

  @Test
  void intermediateCatchEvent() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .INTERMEDIATE_CATCH_EVENT)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .INTERMEDIATE_CATCH_EVENT)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                .INTERMEDIATE_CATCH_EVENT
                .getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                .INTERMEDIATE_CATCH_EVENT
                .getName());
    assertThat(template.elementType().eventDefinition()).isEqualTo("bpmn:MessageEventDefinition");
  }

  @Test
  void receiveTask() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .RECEIVE_TASK)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .RECEIVE_TASK)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.RECEIVE_TASK
                .getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.RECEIVE_TASK
                .getName());
    assertThat(template.elementType().eventDefinition()).isNull();
  }

  @Test
  void messageStartEvent() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.START_EVENT)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .MESSAGE_START_EVENT)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.START_EVENT
                .getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                .MESSAGE_START_EVENT
                .getName());
    assertThat(template.elementType().eventDefinition()).isEqualTo("bpmn:MessageEventDefinition");
  }

  @Test
  void boundaryEvent() {
    ElementTemplate template =
        ElementTemplateBuilder.createOutbound()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .BOUNDARY_EVENT)
            .elementType(
                io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType
                    .BOUNDARY_EVENT)
            .build();

    assertThat(template.appliesTo())
        .containsExactly(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.BOUNDARY_EVENT
                .getName());
    assertThat(template.elementType().value())
        .isEqualTo(
            io.camunda.connector.generator.java.annotation.ElementTemplate.BpmnType.BOUNDARY_EVENT
                .getName());
    assertThat(template.elementType().eventDefinition()).isEqualTo("bpmn:MessageEventDefinition");
  }
}
