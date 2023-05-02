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
package io.camunda.connector.runtime.inbound.importer;

import io.camunda.connector.impl.Constants;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.dto.ProcessDefinition;
import io.camunda.operate.exception.OperateException;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ReceiveTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects the imported process definitions and extracts Inbound Connector definitions as {@link
 * ProcessCorrelationPoint}.
 */
public class ProcessDefinitionInspector {

  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionInspector.class);

  private static final List<Class<? extends BaseElement>> INBOUND_ELIGIBLE_TYPES =
      new ArrayList<>();

  static {
    INBOUND_ELIGIBLE_TYPES.add(StartEvent.class);
    INBOUND_ELIGIBLE_TYPES.add(IntermediateCatchEvent.class);
    INBOUND_ELIGIBLE_TYPES.add(ReceiveTask.class);
  }

  private final CamundaOperateClient operate;

  public ProcessDefinitionInspector(CamundaOperateClient operate) {
    this.operate = operate;
  }

  public List<InboundConnectorProperties> findInboundConnectors(ProcessDefinition processDefinition)
      throws OperateException {

    LOG.debug("Check " + processDefinition + " for connectors.");

    BpmnModelInstance modelInstance = operate.getProcessDefinitionModel(processDefinition.getKey());

    Map<String, List<InboundConnectorProperties>> connectorDefinitions =
        modelInstance.getDefinitions().getChildElementsByType(Process.class).stream()
            .flatMap(process -> inspectBpmnProcess(process, processDefinition).stream())
            .collect(Collectors.groupingBy(InboundConnectorProperties::getCorrelationPointId));

    return connectorDefinitions.entrySet().stream()
        .map(
            entry -> {
              if (entry.getValue().size() > 1) {
                LOG.info(
                    "Found multiple connector definitions with the same deduplication ID: "
                        + entry.getKey()
                        + ". It will be ignored");
              }
              return entry.getValue().get(0);
            })
        .collect(Collectors.toList());
  }

  private List<InboundConnectorProperties> inspectBpmnProcess(
      Process process, ProcessDefinition definition) {

    List<BaseElement> inboundEligibleElements =
        INBOUND_ELIGIBLE_TYPES.stream()
            .flatMap(type -> process.getChildElementsByType(type).stream())
            .collect(Collectors.toList());

    List<InboundConnectorProperties> discoveredConnectors = new ArrayList<>();

    for (BaseElement element : inboundEligibleElements) {
      ZeebeProperties zeebeProperties = element.getSingleExtensionElement(ZeebeProperties.class);
      if (zeebeProperties == null) {
        continue;
      }
      Optional<ProcessCorrelationPoint> maybeTarget = handleElement(element, definition);
      if (maybeTarget.isEmpty()) {
        continue;
      }
      ProcessCorrelationPoint target = maybeTarget.get();

      InboundConnectorProperties properties =
          new InboundConnectorProperties(
              target,
              zeebeProperties.getProperties().stream()
                  // Avoid issue with OpenJDK when collecting null values
                  // -->
                  // https://stackoverflow.com/questions/24630963/nullpointerexception-in-collectors-tomap-with-null-entry-values
                  // .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue)));
                  .collect(
                      HashMap::new,
                      (m, zeebeProperty) ->
                          m.put(zeebeProperty.getName(), zeebeProperty.getValue()),
                      HashMap::putAll),
              definition.getBpmnProcessId(),
              definition.getVersion().intValue(),
              definition.getKey());

      discoveredConnectors.add(properties);
    }
    return discoveredConnectors;
  }

  private Optional<ProcessCorrelationPoint> handleElement(
      BaseElement element, ProcessDefinition definition) {

    if (element instanceof StartEvent) {
      return handleStartEvent(definition);
    } else if (element instanceof IntermediateCatchEvent) {
      return handleIntermediateCatchEvent((IntermediateCatchEvent) element);
    } else if (element instanceof ReceiveTask) {
      return handleReceiveTask((ReceiveTask) element);
    }
    LOG.warn("Unsupported Inbound element type: " + element.getClass());
    return Optional.empty();
  }

  private Optional<ProcessCorrelationPoint> handleIntermediateCatchEvent(
      IntermediateCatchEvent catchEvent) {

    MessageEventDefinition msgDef =
        (MessageEventDefinition)
            catchEvent.getEventDefinitions().stream()
                .filter(def -> def instanceof MessageEventDefinition)
                .findAny()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Sanity check failed: IntermediateCatchEvent "
                                + catchEvent
                                + " must contain at least one event definition"));
    String name = msgDef.getMessage().getName();

    // fail-fast strategy, validating if correlation key expression is set
    validateRequiredProperty(catchEvent, Constants.CORRELATION_KEY_EXPRESSION_KEYWORD);

    return Optional.of(new MessageCorrelationPoint(name));
  }

  private Optional<ProcessCorrelationPoint> handleStartEvent(ProcessDefinition definition) {

    return Optional.of(
        new StartEventCorrelationPoint(
            definition.getKey(),
            definition.getBpmnProcessId(),
            definition.getVersion().intValue()));
  }

  private Optional<ProcessCorrelationPoint> handleReceiveTask(ReceiveTask receiveTask) {
    Message message = receiveTask.getMessage();

    // fail-fast strategy, validating if correlation key expression is set
    validateRequiredProperty(receiveTask, Constants.CORRELATION_KEY_EXPRESSION_KEYWORD);
    return Optional.of(new MessageCorrelationPoint(message.getName()));
  }

  private void validateRequiredProperty(BaseElement element, String name) {
    if (extractProperty(element, name) == null) {
      throw new IllegalStateException("Missing required property " + name);
    }
  }

  private String extractProperty(BaseElement element, String name) {
    ZeebeProperties zeebeProperties = element.getSingleExtensionElement(ZeebeProperties.class);
    if (zeebeProperties == null) {
      LOG.warn("Missing `zeebe:property` mappings on element " + element);
      return null;
    }
    return zeebeProperties.getProperties().stream()
        .filter(property -> property.getName().equals(name))
        .findAny()
        .map(ZeebeProperty::getValue)
        .orElse(null);
  }
}
