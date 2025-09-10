/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.processdefinition;

import static io.camunda.connector.agenticai.util.BpmnUtils.getElementDocumentation;
import static io.camunda.connector.agenticai.util.BpmnUtils.getExtensionProperties;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.worker.BackoffSupplier;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.AdHocToolElementParameterExtractor;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaClientProcessDefinitionAdHocToolElementsResolver
    implements ProcessDefinitionAdHocToolElementsResolver {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaClientProcessDefinitionAdHocToolElementsResolver.class);

  private static final String ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR =
      "AD_HOC_SUB_PROCESS_XML_FETCH_ERROR";
  private static final String ERROR_CODE_AD_HOC_SUB_PROCESS_NOT_FOUND =
      "AD_HOC_SUB_PROCESS_NOT_FOUND";
  private static final String ERROR_CODE_AD_HOC_TOOL_DEFINITION_INVALID =
      "AD_HOC_TOOL_DEFINITION_INVALID";

  private static final int GET_DEFINITION_XML_MAX_RETRIES = 3;
  private static final BackoffSupplier GET_DEFINITION_XML_BACKOFF_SUPPLIER =
      BackoffSupplier.newBackoffBuilder().minDelay(500).build();

  private final CamundaClient camundaClient;
  private final AdHocToolElementParameterExtractor parameterExtractor;

  public CamundaClientProcessDefinitionAdHocToolElementsResolver(
      CamundaClient camundaClient, AdHocToolElementParameterExtractor parameterExtractor) {
    this.camundaClient = camundaClient;
    this.parameterExtractor = parameterExtractor;
  }

  @Override
  public List<AdHocToolElement> resolveToolElements(
      Long processDefinitionKey, String adHocSubProcessId) {
    if (processDefinitionKey == null || processDefinitionKey <= 0) {
      throw new IllegalArgumentException("Process definition key must not be null or negative");
    }

    if (adHocSubProcessId == null || adHocSubProcessId.isBlank()) {
      throw new IllegalArgumentException("adHocSubProcessId cannot be null or empty");
    }

    LOGGER.info(
        "Resolving tool elements for ad-hoc sub-process {} in process definition with key {}",
        adHocSubProcessId,
        processDefinitionKey);

    final String processDefinitionXml =
        getProcessDefinitionXmlWithRetries(processDefinitionKey, 1, Duration.ZERO);

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(processDefinitionXml.getBytes(StandardCharsets.UTF_8)));

    final var processElement = modelInstance.getModelElementById(adHocSubProcessId);
    if (!(processElement instanceof final AdHocSubProcess adHocSubProcess)) {
      throw new ConnectorException(
          ERROR_CODE_AD_HOC_SUB_PROCESS_NOT_FOUND,
          "Unable to resolve tool elements. Ad-hoc sub-process with ID '%s' was not found."
              .formatted(adHocSubProcessId));
    }

    return adHocSubProcess.getChildElementsByType(FlowNode.class).stream()
        .filter(this::isToolElement)
        .map(this::asSubProcessElement)
        .toList();
  }

  private String getProcessDefinitionXmlWithRetries(
      Long processDefinitionKey, int currentAttempt, Duration currentRetryDelay) {
    if (!currentRetryDelay.isZero()) {
      LOGGER.warn(
          "Retrying to fetch process definition XML for process definition key {}. Attempt {}/{}. Waiting for {} ms before retrying.",
          processDefinitionKey,
          currentAttempt,
          GET_DEFINITION_XML_MAX_RETRIES,
          currentRetryDelay.toMillis());

      try {
        Thread.sleep(currentRetryDelay);
      } catch (InterruptedException ex) {
        LOGGER.warn(
            "Interrupted while waiting for retry delay to fetch process definition with key {}",
            processDefinitionKey,
            ex);

        Thread.currentThread().interrupt();

        throw new ConnectorException(
            ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR,
            "Unable to resolve tool elements. Interrupted while waiting for retry delay to fetch process definition XML with key '%s'."
                .formatted(processDefinitionKey));
      }
    }

    try {
      return camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();
    } catch (ClientHttpException e) {
      if (currentAttempt < GET_DEFINITION_XML_MAX_RETRIES) {
        final var newRetryDelay =
            Duration.ofMillis(
                GET_DEFINITION_XML_BACKOFF_SUPPLIER.supplyRetryDelay(currentRetryDelay.toMillis()));

        return getProcessDefinitionXmlWithRetries(
            processDefinitionKey, ++currentAttempt, newRetryDelay);
      } else {
        LOGGER.error(
            "Failed to retrieve process definition XML for process definition key {} after {} attempts",
            processDefinitionKey,
            currentAttempt,
            e);

        throw new ConnectorException(
            ERROR_CODE_AD_HOC_SUB_PROCESS_XML_FETCH_ERROR,
            "Unable to resolve tool elements. Failed to retrieve process definition XML with key %s: %s"
                .formatted(processDefinitionKey, e.getMessage()),
            e);
      }
    }
  }

  private boolean isToolElement(FlowNode element) {
    return element.getIncoming().isEmpty() && !(element instanceof BoundaryEvent);
  }

  private AdHocToolElement asSubProcessElement(FlowNode element) {
    return AdHocToolElement.builder()
        .elementId(element.getId())
        .elementName(element.getName())
        .documentation(getElementDocumentation(element).orElse(null))
        .properties(getExtensionProperties(element))
        .parameters(extractParameters(element))
        .build();
  }

  private List<AdHocToolElementParameter> extractParameters(FlowNode element) {
    final var ioMapping = element.getSingleExtensionElement(ZeebeIoMapping.class);
    if (ioMapping == null) {
      return Collections.emptyList();
    }

    List<AdHocToolElementParameter> result = new ArrayList<>();
    result.addAll(extractParameters(element, ioMapping.getInputs()));
    result.addAll(extractParameters(element, ioMapping.getOutputs()));

    return result;
  }

  private List<AdHocToolElementParameter> extractParameters(
      FlowNode element, Collection<? extends ZeebeMapping> mappings) {
    return mappings.stream()
        .map(mapping -> extractParameters(element, mapping))
        .flatMap(List::stream)
        .toList();
  }

  private List<AdHocToolElementParameter> extractParameters(
      FlowNode element, ZeebeMapping mapping) {
    if (mapping.getSource() == null) {
      return Collections.emptyList();
    }

    final String source = mapping.getSource().trim();
    if (source.startsWith("=")) {
      try {
        return parameterExtractor.extractParameters(source.substring(1));
      } catch (Exception e) {
        final var mappingType =
            switch (mapping) {
              case ZeebeInput ignored -> "input";
              case ZeebeOutput ignored -> "output";
              default ->
                  throw new IllegalArgumentException(
                      "Unexpected mapping type: " + mapping.getClass());
            };

        throw new ConnectorException(
            ERROR_CODE_AD_HOC_TOOL_DEFINITION_INVALID,
            "Invalid ad-hoc tool definition for element '%s' on %s mapping '%s': %s"
                .formatted(element.getId(), mappingType, mapping.getTarget(), e.getMessage()));
      }
    }

    return Collections.emptyList();
  }
}
