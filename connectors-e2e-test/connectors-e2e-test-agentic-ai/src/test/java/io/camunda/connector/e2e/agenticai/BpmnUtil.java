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
package io.camunda.connector.e2e.agenticai;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public class BpmnUtil {
  private BpmnUtil() {}

  public static List<ServiceTask> serviceTasksByType(
      BpmnModelInstance bpmnModel, Predicate<String> typePredicate) {
    return bpmnModel.getModelElementsByType(ServiceTask.class).stream()
        .filter(
            st -> {
              var taskDefinition = st.getSingleExtensionElement(ZeebeTaskDefinition.class);
              var type = taskDefinition.getType();
              return type != null && typePredicate.test(type);
            })
        .toList();
  }

  public static void updateInputMappings(
      BpmnModelInstance bpmnModel, String elementId, Map<String, String> inputMappings) {
    final var element = bpmnModel.getModelElementById(elementId);
    assertThat(element)
        .describedAs("Element with ID %s is expected to exist", elementId)
        .isNotNull()
        .describedAs(
            "Element with ID %s is expected to be a child instance of BaseElement", elementId)
        .isInstanceOf(BaseElement.class);

    updateInputMappings(
        bpmnModel, (BaseElement) bpmnModel.getModelElementById(elementId), inputMappings);
  }

  public static void updateInputMappings(
      BpmnModelInstance bpmnModel, BaseElement element, Map<String, String> inputMappings) {
    ZeebeIoMapping ioMapping =
        Optional.ofNullable(element.getSingleExtensionElement(ZeebeIoMapping.class))
            .orElseGet(
                () -> element.getExtensionElements().addExtensionElement(ZeebeIoMapping.class));

    inputMappings.forEach(
        (target, source) -> {
          getOrCreateInputMapping(bpmnModel, ioMapping, target).setSource(source);
        });
  }

  public static ZeebeInput getOrCreateInputMapping(
      BpmnModelInstance bpmnModel, ZeebeIoMapping ioMapping, String target) {
    return ioMapping.getChildElementsByType(ZeebeInput.class).stream()
        .filter(input -> target.equals(input.getTarget()))
        .findFirst()
        .orElseGet(
            () -> {
              final var im = bpmnModel.newInstance(ZeebeInput.class);
              im.setTarget(target);
              ioMapping.addChildElement(im);
              return im;
            });
  }

  /**
   * TODO(camunda/camunda#59715): temporary workaround, remove once the v2 AI Agent element
   * templates set {@code zeebe:agentDefinition} themselves. The engine rejects {@code
   * AgentInstance} creation without that marker, but neither the v2 element templates nor
   * element-templates-cli's bundled {@code zeebe-bpmn-moddle} support the element yet - the CLI
   * strips it if added to the source BPMN. Needs element-templates-cli/library updates plus updated
   * element templates; until then, add it here directly on the already-applied model.
   */
  public static BpmnModelInstance withAgentDefinitionMarker(
      BpmnModelInstance model, String elementId, String agentType) {
    final var element = model.getModelElementById(elementId);
    assertThat(element)
        .describedAs("Element with ID %s is expected to exist", elementId)
        .isNotNull()
        .describedAs(
            "Element with ID %s is expected to be a child instance of BaseElement", elementId)
        .isInstanceOf(BaseElement.class);

    final var agentDefinition =
        ((BaseElement) element)
            .getExtensionElements()
            .addExtensionElement("http://camunda.org/schema/zeebe/1.0", "agentDefinition");
    agentDefinition.setAttributeValue("agentType", agentType);
    return model;
  }
}
