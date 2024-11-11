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
package io.camunda.connector.runtime.inbound.state;

import static io.camunda.connector.runtime.core.Keywords.CORRELATION_KEY_EXPRESSION_KEYWORD;
import static io.camunda.connector.runtime.core.Keywords.INBOUND_TYPE_KEYWORD;
import static io.camunda.connector.runtime.core.Keywords.MESSAGE_ID_EXPRESSION;
import static io.camunda.connector.runtime.core.Keywords.MESSAGE_TTL;

import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.runtime.core.error.InvalidInboundConnectorDefinitionException;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.BoundaryEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.correlation.MessageStartEventCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.correlation.ProcessCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.inbound.operate.OperateClient;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionIdentifier;
import io.camunda.connector.runtime.inbound.state.ProcessImportResult.ProcessDefinitionVersion;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.CatchEvent;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ReceiveTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inspects the imported process elements and extracts Inbound Connector elements as {@link
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
    INBOUND_ELIGIBLE_TYPES.add(BoundaryEvent.class);
  }

  private final OperateClient operateClient;

  public ProcessDefinitionInspector(OperateClient operateClient) {
    this.operateClient = operateClient;
  }

  public List<InboundConnectorElement> findInboundConnectors(
      ProcessDefinitionIdentifier identifier, ProcessDefinitionVersion version) {

    LOG.debug("Checking {} (version {}) for connectors.", identifier, version.version());
    BpmnModelInstance modelInstance = operateClient.getProcessModel(version.processDefinitionKey());

    var processes =
        modelInstance.getDefinitions().getChildElementsByType(Process.class).stream()
            .filter(p -> p.getId().equals(identifier.bpmnProcessId()))
            .findFirst();

    return processes.stream()
        .flatMap(process -> inspectBpmnProcess(process, identifier, version).stream())
        .toList();
  }

  private List<InboundConnectorElement> inspectBpmnProcess(
      Process process, ProcessDefinitionIdentifier identifier, ProcessDefinitionVersion version) {
    Collection<BaseElement> inboundEligibleElements = retrieveEligibleElementsFromProcess(process);

    List<InboundConnectorElement> discoveredInboundConnectors = new ArrayList<>();
    for (BaseElement element : inboundEligibleElements) {
      Optional<ProcessCorrelationPoint> optionalTarget =
          getCorrelationPointForElement(element, process, identifier, version);
      if (optionalTarget.isEmpty()) {
        continue;
      }
      ProcessCorrelationPoint target = optionalTarget.get();

      var rawProperties = getRawProperties(element);
      if (rawProperties == null || !rawProperties.containsKey(INBOUND_TYPE_KEYWORD)) {
        LOG.debug("Not a connector: {}", element.getId());
        continue;
      }

      var processElement =
          new ProcessElement(
              process.getId(),
              version.version(),
              version.processDefinitionKey(),
              element.getId(),
              identifier.tenantId());
      InboundConnectorElement def =
          new InboundConnectorElement(rawProperties, target, processElement);

      discoveredInboundConnectors.add(def);
    }
    return discoveredInboundConnectors;
  }

  private Collection<BaseElement> retrieveEligibleElementsFromProcess(final Process process) {
    // process is root element in graph
    Collection<FlowElement> buffer = new HashSet<>();
    Collection<FlowElement> allElements = collectFlowElements(process.getFlowElements(), buffer);
    Collection<BaseElement> inboundEligibleElements = new HashSet<>();
    for (FlowElement element : allElements) {
      INBOUND_ELIGIBLE_TYPES.forEach(
          iet -> {
            if (iet.isInstance(element)
                && getRawProperties(element).containsKey(INBOUND_TYPE_KEYWORD)) {
              inboundEligibleElements.add(element);
            }
          });
    }
    return inboundEligibleElements;
  }

  private Collection<FlowElement> retrieveEligibleElementsFromSubprocess(
      final SubProcess subprocess) {
    // Subprocesses can contain other subprocesses
    Collection<FlowElement> buffer = new HashSet<>();
    Collection<FlowElement> processFlowElements = subprocess.getFlowElements();
    return collectFlowElements(processFlowElements, buffer);
  }

  private Collection<FlowElement> collectFlowElements(
      final Collection<FlowElement> processFlowElements, final Collection<FlowElement> buffer) {
    for (FlowElement element : processFlowElements) {
      // if we detect a subprocess, we have to expand it
      // its building blocks to identify where are connectors
      if (element instanceof SubProcess subprocess) {
        buffer.addAll(retrieveEligibleElementsFromSubprocess(subprocess));
        continue;
      }
      buffer.add(element);
    }
    return buffer;
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForElement(
      BaseElement element,
      Process process,
      ProcessDefinitionIdentifier identifier,
      ProcessDefinitionVersion version) {
    try {
      if (element instanceof StartEvent se) {
        return getCorrelationPointForStartEvent(se, process, version);
      } else if (element instanceof IntermediateCatchEvent ice) {
        return getCorrelationPointForIntermediateCatchEvent(ice);
      } else if (element instanceof BoundaryEvent be) {
        return getCorrelationPointForIntermediateBoundaryEvent(be);
      } else if (element instanceof ReceiveTask rt) {
        return getCorrelationPointForReceiveTask(rt);
      }
      LOG.warn(
          "Unsupported Inbound element type: {}, in process definition: {} (Key: {}, Version: {})",
          element.getClass().getSimpleName(),
          identifier.bpmnProcessId(),
          version.processDefinitionKey(),
          version.version());
    } catch (InvalidInboundConnectorDefinitionException e) {
      LOG.warn(
          "Error getting correlation point for {} in process definition: {} (Key: {}, Version: {}): {}",
          element.getClass().getSimpleName(),
          identifier.bpmnProcessId(),
          version.processDefinitionKey(),
          version.version(),
          e.getMessage(),
          e);
    }
    return Optional.empty();
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForIntermediateCatchEvent(
      IntermediateCatchEvent intermediateCatchEvent) {
    return getCorrelationPointCatchEvent(intermediateCatchEvent);
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForIntermediateBoundaryEvent(
      BoundaryEvent boundaryEvent) {
    return getCorrelationPointCatchEvent(boundaryEvent);
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointCatchEvent(CatchEvent catchEvent) {
    MessageEventDefinition msgDef =
        (MessageEventDefinition)
            catchEvent.getEventDefinitions().stream()
                .filter(def -> def instanceof MessageEventDefinition)
                .findAny()
                .orElseThrow(
                    () ->
                        new InvalidInboundConnectorDefinitionException(
                            "Sanity check failed: "
                                + catchEvent.getClass().getSimpleName()
                                + " must contain at least one event definition"));
    String name = msgDef.getMessage().getName();

    String correlationKeyExpression =
        extractRequiredProperty(catchEvent, CORRELATION_KEY_EXPRESSION_KEYWORD);

    String messageIdExpression = extractProperty(catchEvent, MESSAGE_ID_EXPRESSION).orElse(null);

    Duration messageTtl =
        extractProperty(catchEvent, MESSAGE_TTL).map(Duration::parse).orElse(null);

    ProcessCorrelationPoint correlationPoint;
    if (BoundaryEvent.class.isAssignableFrom(catchEvent.getClass())) {
      var boundaryEvent = (BoundaryEvent) catchEvent;
      var attachedTo = boundaryEvent.getAttachedTo();
      var activity =
          new BoundaryEventCorrelationPoint.Activity(attachedTo.getId(), attachedTo.getName());
      correlationPoint =
          new BoundaryEventCorrelationPoint(
              name, correlationKeyExpression, messageIdExpression, messageTtl, activity);
    } else {
      correlationPoint =
          new StandaloneMessageCorrelationPoint(
              name, correlationKeyExpression, messageIdExpression, messageTtl);
    }

    return Optional.of(correlationPoint);
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForStartEvent(
      StartEvent startEvent, Process process, ProcessDefinitionVersion version) {

    MessageEventDefinition msgDef =
        (MessageEventDefinition)
            startEvent.getEventDefinitions().stream()
                .filter(def -> def instanceof MessageEventDefinition)
                .findAny()
                .orElse(null);

    if (msgDef != null) {
      String messageIdExpression = extractProperty(startEvent, MESSAGE_ID_EXPRESSION).orElse(null);

      Duration messageTtl =
          extractProperty(startEvent, MESSAGE_TTL).map(Duration::parse).orElse(null);

      String correlationKeyExpression =
          extractProperty(startEvent, CORRELATION_KEY_EXPRESSION_KEYWORD).orElse(null);
      return Optional.of(
          new MessageStartEventCorrelationPoint(
              msgDef.getMessage().getName(),
              messageIdExpression,
              messageTtl,
              correlationKeyExpression,
              process.getId(),
              version.version(),
              version.processDefinitionKey()));
    }

    return Optional.of(
        new StartEventCorrelationPoint(
            process.getId(), version.version(), version.processDefinitionKey()));
  }

  private Optional<ProcessCorrelationPoint> getCorrelationPointForReceiveTask(
      ReceiveTask receiveTask) {
    Message message = receiveTask.getMessage();
    String correlationKeyExpression =
        extractRequiredProperty(receiveTask, CORRELATION_KEY_EXPRESSION_KEYWORD);
    String messageIdExpression = extractProperty(receiveTask, MESSAGE_ID_EXPRESSION).orElse(null);
    Duration messageTtl =
        extractProperty(receiveTask, MESSAGE_TTL).map(Duration::parse).orElse(null);
    return Optional.of(
        new StandaloneMessageCorrelationPoint(
            message.getName(), correlationKeyExpression, messageIdExpression, messageTtl));
  }

  private Map<String, String> getRawProperties(BaseElement element) {
    ZeebeProperties zeebeProperties = element.getSingleExtensionElement(ZeebeProperties.class);
    if (zeebeProperties == null) {
      return Collections.emptyMap();
    }
    return zeebeProperties.getProperties().stream()
        .filter(property -> property.getValue() != null)
        .collect(
            Collectors.toMap(
                ZeebeProperty::getName,
                ZeebeProperty::getValue,
                (oldValue, newValue) -> {
                  LOG.warn(
                      "A duplicate has been found, old value {} and new value {} for element {}",
                      oldValue,
                      newValue,
                      element.getId());
                  // In case a duplicate is found we take the first value found
                  return oldValue;
                }));
  }

  private String extractRequiredProperty(BaseElement element, String name) {
    return extractProperty(element, name)
        .orElseThrow(
            () ->
                new InvalidInboundConnectorDefinitionException(
                    "Missing required property " + name));
  }

  private Optional<String> extractProperty(BaseElement element, String name) {
    ZeebeProperties zeebeProperties = element.getSingleExtensionElement(ZeebeProperties.class);
    return Optional.ofNullable(zeebeProperties)
        .map(ZeebeProperties::getProperties)
        .flatMap(
            props ->
                props.stream()
                    .filter(property -> property.getName().equals(name))
                    .findAny()
                    .map(ZeebeProperty::getValue));
  }
}
