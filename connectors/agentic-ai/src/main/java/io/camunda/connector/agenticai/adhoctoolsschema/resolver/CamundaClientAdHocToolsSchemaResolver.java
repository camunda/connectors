/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParam;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractionException;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.SchemaGenerationException;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaClientAdHocToolsSchemaResolver implements AdHocToolsSchemaResolver {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaClientAdHocToolsSchemaResolver.class);

  private static final String ERROR_CODE_AD_HOC_SUB_PROCESS_NOT_FOUND =
      "AD_HOC_SUB_PROCESS_NOT_FOUND";
  private static final String ERROR_CODE_AD_HOC_TOOL_DEFINITION_INVALID =
      "AD_HOC_TOOL_DEFINITION_INVALID";
  private static final String ERROR_CODE_AD_HOC_TOOL_SCHEMA_INVALID = "AD_HOC_TOOL_SCHEMA_INVALID";

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
      Long processDefinitionKey, String adHocSubProcessId) {
    if (processDefinitionKey == null || processDefinitionKey <= 0) {
      throw new IllegalArgumentException("Process definition key must not be null or negative");
    }

    if (adHocSubProcessId == null || adHocSubProcessId.isBlank()) {
      throw new IllegalArgumentException("adHocSubProcessId cannot be null or empty");
    }

    LOGGER.info(
        "Resolving tool schema for ad-hoc sub-process {} in process definition with key {}",
        adHocSubProcessId,
        processDefinitionKey);

    final String processDefinitionXml =
        camundaClient.newProcessDefinitionGetXmlRequest(processDefinitionKey).send().join();

    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            new ByteArrayInputStream(processDefinitionXml.getBytes(StandardCharsets.UTF_8)));

    final var processElement = modelInstance.getModelElementById(adHocSubProcessId);
    if (!(processElement instanceof final AdHocSubProcess adHocSubProcess)) {
      throw new ConnectorException(
          ERROR_CODE_AD_HOC_SUB_PROCESS_NOT_FOUND,
          "Unable to resolve tools schema. Ad-hoc sub-process with ID '%s' was not found."
              .formatted(adHocSubProcessId));
    }

    final var toolDefinitions =
        adHocSubProcess.getChildElementsByType(FlowNode.class).stream()
            .filter(this::isToolElement)
            .map(this::mapActivityToToolDefinition)
            .toList();

    return new AdHocToolsSchemaResponse(toolDefinitions);
  }

  private boolean isToolElement(FlowNode element) {
    return element.getIncoming().isEmpty() && !(element instanceof BoundaryEvent);
  }

  private ToolDefinition mapActivityToToolDefinition(FlowNode element) {
    final var documentation = getDocumentation(element).orElseGet(element::getName);
    final var inputSchema = generateInputSchema(element);

    return ToolDefinition.builder()
        .name(element.getId())
        .description(documentation)
        .inputSchema(inputSchema)
        .build();
  }

  private Optional<String> getDocumentation(FlowNode element) {
    return element.getDocumentations().stream()
        .filter(d -> "text/plain".equals(d.getTextFormat()))
        .findFirst()
        .map(ModelElementInstance::getTextContent)
        .filter(StringUtils::isNotBlank);
  }

  private Map<String, Object> generateInputSchema(FlowNode element) {
    final var inputParams = extractFeelInputParams(element);

    try {
      return schemaGenerator.generateToolSchema(inputParams);
    } catch (SchemaGenerationException e) {
      throw new ConnectorException(
          ERROR_CODE_AD_HOC_TOOL_SCHEMA_INVALID,
          "Failed to generate ad-hoc tool schema for element '%s': %s"
              .formatted(element.getId(), e.getMessage()));
    }
  }

  private List<FeelInputParam> extractFeelInputParams(FlowNode element) {
    final var ioMapping = element.getSingleExtensionElement(ZeebeIoMapping.class);
    if (ioMapping == null) {
      return Collections.emptyList();
    }

    List<FeelInputParam> result = new ArrayList<>();
    result.addAll(extractFeelInputParams(element, ioMapping.getInputs()));
    result.addAll(extractFeelInputParams(element, ioMapping.getOutputs()));

    return result;
  }

  private List<FeelInputParam> extractFeelInputParams(
      FlowNode element, Collection<? extends ZeebeMapping> mappings) {
    return mappings.stream()
        .map(mapping -> extractFeelInputParams(element, mapping))
        .flatMap(List::stream)
        .toList();
  }

  private List<FeelInputParam> extractFeelInputParams(FlowNode element, ZeebeMapping mapping) {
    if (mapping.getSource() == null) {
      return Collections.emptyList();
    }

    final String source = mapping.getSource().trim();
    if (source.startsWith("=")) {
      try {
        return feelInputParamExtractor.extractInputParams(source.substring(1));
      } catch (FeelInputParamExtractionException e) {
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
