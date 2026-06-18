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
package io.camunda.connector.runtime.outbound.secret;

import io.camunda.client.CamundaClient;
import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.runtime.core.secret.SecretResolverMode;
import io.camunda.connector.runtime.core.secret.SecretUtil;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.FlowElement;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.ScriptTask;
import io.camunda.zeebe.model.bpmn.instance.SendTask;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;

public class ProcessDefinitionSecretKeyCache implements SecretKeyCache {
  private static final Logger LOG = LoggerFactory.getLogger(ProcessDefinitionSecretKeyCache.class);
  private static final List<Class<? extends BaseElement>> OUTBOUND_ELIGIBLE_TYPES =
      new ArrayList<>();

  static {
    OUTBOUND_ELIGIBLE_TYPES.add(ServiceTask.class);
    OUTBOUND_ELIGIBLE_TYPES.add(SendTask.class);
    OUTBOUND_ELIGIBLE_TYPES.add(ScriptTask.class);
    OUTBOUND_ELIGIBLE_TYPES.add(BusinessRuleTask.class);
  }

  private final CamundaClient camundaClient;
  private final Cache cache;

  public ProcessDefinitionSecretKeyCache(CamundaClient camundaClient, Cache cache) {
    this.camundaClient = camundaClient;
    this.cache = cache;
  }

  @Override
  public List<String> getSecretKeys(SecretKeyContext secretKeyContext) {
    return cache
        .get(
            secretKeyContext.processDefinitionKey(),
            () -> fetchSecretKeysByElementIds(secretKeyContext.processDefinitionKey()))
        .getOrDefault(secretKeyContext.elementId(), Collections.emptyList());
  }

  private Map<String, List<String>> fetchSecretKeysByElementIds(long processDefinitionKey) {
    String bpmnXml =
        camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).execute();

    BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(new ByteArrayInputStream(bpmnXml.getBytes()));
    var processes =
        modelInstance.getDefinitions().getChildElementsByType(Process.class).stream().toList();

    return processes.stream()
        .flatMap(process -> inspectBpmnProcess(process, processDefinitionKey).entrySet().stream())
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private Map<String, List<String>> inspectBpmnProcess(Process process, long processDefinitionKey) {
    Collection<BaseElement> outboundEligibleElements =
        retrieveOutboundEligibleElementsFromProcess(process);
    if (outboundEligibleElements.isEmpty()) {
      LOG.debug(
          "No connector elements found in process definition with key {}", processDefinitionKey);
      return Collections.emptyMap();
    }

    Map<String, List<String>> discoveredOutboundConnectors = new HashMap<>();
    for (BaseElement element : outboundEligibleElements) {
      var inputs = findElementInput(element);
      var definedSecrets = extractSecrets(inputs);
      discoveredOutboundConnectors.put(element.getId(), definedSecrets);
    }
    return discoveredOutboundConnectors;
  }

  private List<String> extractSecrets(List<ZeebeInput> inputs) {
    return inputs.stream()
        .flatMap(
            input ->
                SecretUtil.retrieveSecretKeysInInput(input.getSource(), SecretResolverMode.ALL)
                    .stream())
        .map(String::trim)
        .distinct()
        .toList();
  }

  private List<ZeebeInput> findElementInput(BaseElement element) {
    ZeebeIoMapping singleExtensionElement = element.getSingleExtensionElement(ZeebeIoMapping.class);
    if (singleExtensionElement == null) {
      return Collections.emptyList();
    }
    return singleExtensionElement.getInputs().stream().toList();
  }

  private Collection<BaseElement> retrieveOutboundEligibleElementsFromProcess(
      final Process process) {
    // process is root element in graph
    Collection<FlowElement> buffer = new HashSet<>();
    Collection<FlowElement> allElements = collectFlowElements(process.getFlowElements(), buffer);
    Collection<BaseElement> outboundEligibleElements = new HashSet<>();
    for (FlowElement element : allElements) {
      OUTBOUND_ELIGIBLE_TYPES.forEach(
          iet -> {
            if (iet.isInstance(element) && isElementTemplate(element)) {
              outboundEligibleElements.add(element);
            }
          });
    }
    return outboundEligibleElements;
  }

  // pre-existing, move to util from ProcessDefinitionInspector
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

  // pre-existing, move to util from ProcessDefinitionInspector
  private Collection<FlowElement> retrieveEligibleElementsFromSubprocess(
      final SubProcess subprocess) {
    // Subprocesses can contain other subprocesses
    Collection<FlowElement> buffer = new HashSet<>();
    Collection<FlowElement> processFlowElements = subprocess.getFlowElements();
    return collectFlowElements(processFlowElements, buffer);
  }

  private boolean isElementTemplate(FlowElement element) {
    ElementTemplateDetails elementTemplateDetails = getElementTemplateDetails(element);
    return elementTemplateDetails.id() != null;
  }

  // pre-existing, move to util from ProcessDefinitionInspector
  private static ElementTemplateDetails getElementTemplateDetails(BaseElement element) {
    final String NAMESPACE = "http://camunda.org/schema/zeebe/1.0";

    final String TEMPLATE_ID = "modelerTemplate";
    final String TEMPLATE_VERSION = "modelerTemplateVersion";
    final String TEMPLATE_ICON = "modelerTemplateIcon";

    return new ElementTemplateDetails(
        element.getAttributeValueNs(NAMESPACE, TEMPLATE_ID),
        element.getAttributeValueNs(NAMESPACE, TEMPLATE_VERSION),
        element.getAttributeValueNs(NAMESPACE, TEMPLATE_ICON));
  }
}
