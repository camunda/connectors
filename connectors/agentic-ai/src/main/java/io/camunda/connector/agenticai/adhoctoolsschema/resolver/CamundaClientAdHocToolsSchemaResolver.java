/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_DESCRIPTION;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_OBJECT;
import static io.camunda.connector.agenticai.JsonSchemaConstants.TYPE_STRING;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor.FeelInputParam;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
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
import java.util.LinkedHashMap;
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

  private final CamundaClient camundaClient;
  private final FeelInputParamExtractor feelInputParamExtractor;

  public CamundaClientAdHocToolsSchemaResolver(
      CamundaClient camundaClient, FeelInputParamExtractor feelInputParamExtractor) {
    this.camundaClient = camundaClient;
    this.feelInputParamExtractor = feelInputParamExtractor;
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
    final var inputSchema = extractInputSchema(element);

    return new AdHocToolDefinition(element.getId(), documentation, inputSchema);
  }

  private Optional<String> getDocumentation(FlowNode element) {
    return element.getDocumentations().stream()
        .filter(d -> "text/plain".equals(d.getTextFormat()))
        .findFirst()
        .map(ModelElementInstance::getTextContent)
        .filter(StringUtils::isNotBlank);
  }

  private Map<String, Object> extractInputSchema(FlowNode element) {
    Map<String, Object> properties = new LinkedHashMap<>();
    List<String> required = new ArrayList<>();

    final var inputParams = extractFeelInputParams(element);
    inputParams.forEach(
        inputParam -> {
          if (properties.containsKey(inputParam.name())) {
            throw new IllegalArgumentException(
                "Duplicate input parameter name: %s".formatted(inputParam.name()));
          }

          final var propertySchema =
              Optional.ofNullable(inputParam.schema())
                  .map(LinkedHashMap::new)
                  .orElseGet(LinkedHashMap::new);

          // apply type from inputParam if it is set
          if (!StringUtils.isBlank(inputParam.type())) {
            propertySchema.put(PROPERTY_TYPE, inputParam.type());
          }

          // default to string if no type is set (not on inputParam, not in schema directly)
          if (!propertySchema.containsKey(PROPERTY_TYPE)) {
            propertySchema.put(PROPERTY_TYPE, TYPE_STRING);
          }

          if (!StringUtils.isBlank(inputParam.description())) {
            propertySchema.put(PROPERTY_DESCRIPTION, inputParam.description());
          }

          properties.put(inputParam.name(), propertySchema);
          required.add(inputParam.name());
        });

    Map<String, Object> inputSchema = new LinkedHashMap<>();
    inputSchema.put(PROPERTY_TYPE, TYPE_OBJECT);
    inputSchema.put(PROPERTY_PROPERTIES, properties);
    inputSchema.put(PROPERTY_REQUIRED, required);
    return Collections.unmodifiableMap(inputSchema);
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
