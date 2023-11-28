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

public class OutboundElementTypeSupportTest extends BaseTest {

  @Test
  void serviceTask() {
    OutboundElementTemplate template =
        OutboundElementTemplateBuilder.create()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(BpmnType.TASK)
            .elementType(BpmnType.SERVICE_TASK)
            .build();

    assertThat(template.appliesTo()).containsExactly(BpmnType.TASK.getName());
    assertThat(template.elementType().value()).isEqualTo(BpmnType.SERVICE_TASK.getName());
    assertThat(template.elementType().eventDefinition()).isNull();
  }

  @Test
  void intermediateThrowEvent() {
    OutboundElementTemplate template =
        OutboundElementTemplateBuilder.create()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(BpmnType.INTERMEDIATE_THROW_EVENT)
            .elementType(BpmnType.INTERMEDIATE_THROW_EVENT)
            .build();

    assertThat(template.appliesTo()).containsExactly(BpmnType.INTERMEDIATE_THROW_EVENT.getName());
    assertThat(template.elementType().value())
        .isEqualTo(BpmnType.INTERMEDIATE_THROW_EVENT.getName());
    assertThat(template.elementType().eventDefinition()).isEqualTo("bpmn:MessageEventDefinition");
  }

  @Test
  void scriptTask() {
    OutboundElementTemplate template =
        OutboundElementTemplateBuilder.create()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(BpmnType.TASK)
            .elementType(BpmnType.SCRIPT_TASK)
            .build();

    assertThat(template.appliesTo()).containsExactly(BpmnType.TASK.getName());
    assertThat(template.elementType().value()).isEqualTo(BpmnType.SCRIPT_TASK.getName());
    assertThat(template.elementType().eventDefinition()).isNull();
  }

  @Test
  void messageEndEvent() {
    OutboundElementTemplate template =
        OutboundElementTemplateBuilder.create()
            .id("id")
            .name("name")
            .type("type", false)
            .appliesTo(BpmnType.END_EVENT)
            .elementType(BpmnType.MESSAGE_END_EVENT)
            .build();

    assertThat(template.appliesTo()).containsExactly(BpmnType.END_EVENT.getName());
    assertThat(template.elementType().value()).isEqualTo(BpmnType.MESSAGE_END_EVENT.getName());
    assertThat(template.elementType().eventDefinition()).isEqualTo("bpmn:MessageEventDefinition");
  }
}
