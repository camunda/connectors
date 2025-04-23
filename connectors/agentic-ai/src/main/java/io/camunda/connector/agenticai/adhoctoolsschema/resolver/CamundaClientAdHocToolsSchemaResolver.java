/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParam;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGenerator;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaClientAdHocToolsSchemaResolver implements AdHocToolsSchemaResolver {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaClientAdHocToolsSchemaResolver.class);

  private final CamundaClient camundaClient;
  private final FeelInputParamExtractor feelInputParamExtractor;
  private final AdHocToolSchemaGenerator schemaGenerator;

  public CamundaClientAdHocToolsSchemaResolver(
      CamundaClient camundaClient,
      FeelInputParamExtractor feelInputParamExtractor,
      AdHocToolSchemaGenerator schemaGenerator) {
    this.camundaClient = camundaClient;
    this.feelInputParamExtractor = feelInputParamExtractor;
    this.schemaGenerator = schemaGenerator;
  }

  @Override
  public AdHocToolsSchemaResponse resolveSchema(
      Long processDefinitionKey, String adHocSubprocessId) {
    LOGGER.info(
        "Resolving tool schema for ad-hoc subprocess {} in process definition with key {}",
        adHocSubprocessId,
        processDefinitionKey);

    final String processDefinitionXml =
        camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(processDefinitionXml.getBytes(StandardCharsets.UTF_8)));

    final var processElement = modelInstance.getModelElementById(adHocSubprocessId);
    if (!(processElement instanceof final AdHocSubProcess adHocSubProcess)) {
      throw new RuntimeException("Ad-hoc subprocess %s not found".formatted(adHocSubprocessId));
    }

    final var toolDefinitions =
        adHocSubProcess.getChildElementsByType(FlowNode.class).stream()
            .filter(element -> element.getIncoming().isEmpty())
            .map(this::mapActivityToToolDefinition)
            .toList();

    return new AdHocToolsSchemaResponse(toolDefinitions);
  }

  private AdHocToolDefinition mapActivityToToolDefinition(FlowNode element) {
    final var documentation = getDocumentation(element).orElseGet(element::getName);
    final var inputSchema = schemaGenerator.generateToolSchema(extractFeelInputParams(element));

    return new AdHocToolDefinition(element.getId(), documentation, inputSchema);
  }

  private Optional<String> getDocumentation(FlowNode element) {
    return element.getDocumentations().stream()
        .filter(d -> "text/plain".equals(d.getTextFormat()))
        .findFirst()
        .map(ModelElementInstance::getTextContent)
        .filter(StringUtils::isNotBlank);
  }

  private List<FeelInputParam> extractFeelInputParams(FlowNode element) {
    final var ioMapping = element.getSingleExtensionElement(ZeebeIoMapping.class);
    if (ioMapping == null) {
      return Collections.emptyList();
    }

    List<FeelInputParam> result = new ArrayList<>();
    result.addAll(extractFeelInputParams(ioMapping.getInputs()));
    result.addAll(extractFeelInputParams(ioMapping.getOutputs()));

    return result;
  }

  private List<FeelInputParam> extractFeelInputParams(Collection<? extends ZeebeMapping> mappings) {
    List<String> expressions =
        mappings.stream()
            .map(ZeebeMapping::getSource)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .filter(source -> source.startsWith("="))
            .map(source -> source.substring(1))
            .toList();

    return expressions.stream()
        .map(feelInputParamExtractor::extractInputParams)
        .flatMap(List::stream)
        .toList();
  }
}
