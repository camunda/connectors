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

import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.api.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.api.inbound.correlation.ProcessCorrelationPoint;
import io.camunda.connector.api.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.core.Keywords;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
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

  public List<InboundConnectorDefinitionImpl> findInboundConnectors(
      ProcessDefinition processDefinition) throws OperateException {

    LOG.debug("Check " + processDefinition + " for connectors.");
    BpmnModelInstance modelInstance = operate.getProcessDefinitionModel(processDefinition.getKey());

    var processes =
        modelInstance.getDefinitions().getChildElementsByType(Process.class).stream()
            .filter(p -> p.getId().equals(processDefinition.getBpmnProcessId()))
            .findFirst();

    var connectorDefinitions =
        processes.stream()
            .flatMap(process -> inspectBpmnProcess(process, processDefinition).stream())
            .collect(Collectors.groupingBy(InboundConnectorDefinition::correlationPoint));

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

  private List<InboundConnectorDefinitionImpl> inspectBpmnProcess(
      Process process, ProcessDefinition definition) {

    List<BaseElement> inboundEligibleElements =
        INBOUND_ELIGIBLE_TYPES.stream()
            .flatMap(type -> process.getChildElementsByType(type).stream())
            .filter(
                element -> {
                  Map<String, String> zeebeProperties = getRawProperties(element);
                  if (zeebeProperties != null
                      && zeebeProperties.get(Keywords.INBOUND_TYPE_KEYWORD) != null) {
                    return true;
                  }
                  return false;
                })
            .collect(Collectors.toList());

    List<InboundConnectorDefinitionImpl> discoveredInboundConnectors = new ArrayList<>();

    for (BaseElement element : inboundEligibleElements) {
      Optional<ProcessCorrelationPoint> optionalTarget =
          getCorrelationPointForElement(element, process, definition);
      if (optionalTarget.isEmpty()) {
        continue;
      }
      ProcessCorrelationPoint target = optionalTarget.get();

      var rawProperties = getRawProperties(element);
      if (rawProperties == null || !rawProperties.containsKey(Keywords.INBOUND_TYPE_KEYWORD)) {
        LOG.debug("Not a connector: " + element.getId());
        continue;
      }

      InboundConnectorDefinitionImpl def =
          new InboundConnectorDefinitionImpl(
              rawProperties,
              target,
              process.getId(),
              definition.getVersion().intValue(),
              definition.getKey(),
              element.getId());

      discoveredInboundConnectors.add(def);
    }
    return discoveredInboundConnectors;
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForElement(
      BaseElement element, Process process, ProcessDefinition definition) {

    if (element instanceof StartEvent) {
      return getCorrelationPointForStartEvent(process, definition);
    } else if (element instanceof IntermediateCatchEvent) {
      return getCorrelationPointForIntermediateCatchEvent((IntermediateCatchEvent) element);
    } else if (element instanceof ReceiveTask) {
      return getCorrelationPointForReceiveTask((ReceiveTask) element);
    }
    LOG.warn("Unsupported Inbound element type: " + element.getClass());
    return Optional.empty();
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForIntermediateCatchEvent(
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

    String correlationKeyExpression =
        extractRequiredProperty(catchEvent, Keywords.CORRELATION_KEY_EXPRESSION_KEYWORD);

    return Optional.of(new MessageCorrelationPoint(name, correlationKeyExpression));
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForStartEvent(
      Process process, ProcessDefinition definition) {

    return Optional.of(
        new StartEventCorrelationPoint(
            process.getId(), definition.getVersion().intValue(), definition.getKey()));
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForReceiveTask(
      ReceiveTask receiveTask) {
    Message message = receiveTask.getMessage();
    String correlationKeyExpression =
        extractRequiredProperty(receiveTask, Keywords.CORRELATION_KEY_EXPRESSION_KEYWORD);
    return Optional.of(new MessageCorrelationPoint(message.getName(), correlationKeyExpression));
  }

  private Map<String, String> getRawProperties(BaseElement element) {
    ZeebeProperties zeebeProperties = element.getSingleExtensionElement(ZeebeProperties.class);
    if (zeebeProperties == null) {
      return null;
    }
    return zeebeProperties.getProperties().stream()
        .filter(property -> property.getValue() != null)
        .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue));
  }

  private String extractRequiredProperty(BaseElement element, String name) {
    ZeebeProperties zeebeProperties = element.getSingleExtensionElement(ZeebeProperties.class);
    if (zeebeProperties == null) {
      throw new IllegalStateException("Missing required property " + name);
    }
    return zeebeProperties.getProperties().stream()
        .filter(property -> property.getName().equals(name))
        .findAny()
        .map(ZeebeProperty::getValue)
        .orElseThrow(() -> new IllegalStateException("Missing required property " + name));
  }
}
