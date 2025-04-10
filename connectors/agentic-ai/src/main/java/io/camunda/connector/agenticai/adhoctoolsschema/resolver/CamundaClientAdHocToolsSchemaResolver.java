/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import static io.camunda.connector.agenticai.util.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition.JsonSchema;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaClientAdHocToolsSchemaResolver implements AdHocToolsSchemaResolver {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaClientAdHocToolsSchemaResolver.class);

  private static final String INPUT_SCHEMA_PROPERTY_NAME = "camunda:adHocActivityInputSchema";

  private final CamundaClient camundaClient;
  private final ObjectMapper objectMapper;
  private final FeelEngineWrapper feelEngineWrapper;

  public CamundaClientAdHocToolsSchemaResolver(
      CamundaClient camundaClient, ObjectMapper objectMapper, FeelEngineWrapper feelEngineWrapper) {
    this.camundaClient = camundaClient;
    this.objectMapper = objectMapper;
    this.feelEngineWrapper = feelEngineWrapper;
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
    final var documentation = getDocumentation(element);
    final var inputSchema = getInputSchema(element);

    return inputSchema
        .map(
            inputSchemaValue ->
                this.toolDefinitionFromInputSchema(element, documentation, inputSchemaValue))
        .orElseGet(
            () -> new AdHocToolDefinition(element.getId(), documentation, JsonSchema.empty()));
  }

  private AdHocToolDefinition toolDefinitionFromInputSchema(
      final FlowNode element, final String documentation, final String inputSchema) {

    var inputSchemaJson = inputSchema;
    if (inputSchemaJson.startsWith("=")) {
      inputSchemaJson = feelEngineWrapper.evaluateToJson(inputSchemaJson);
    }

    try {
      JsonSchema parsedJsonSchema = objectMapper.readValue(inputSchemaJson, JsonSchema.class);
      return new AdHocToolDefinition(element.getId(), documentation, parsedJsonSchema);
    } catch (Exception e) {
      if (e.getCause() instanceof JsonParseException jpe) {
        throw new ConnectorException(
            "Failed to parse input JSON schema for node %s: %s"
                .formatted(element.getId(), humanReadableJsonProcessingExceptionMessage(jpe)),
            jpe);
      }

      throw new ConnectorException(
          "Failed to parse input JSON schema for node %s".formatted(element.getId()), e);
    }
  }

  private String getDocumentation(FlowNode element) {
    return element.getDocumentations().stream()
        .filter(d -> "text/plain".equals(d.getTextFormat()))
        .findFirst()
        .map(ModelElementInstance::getTextContent)
        .orElse("");
  }

  private Optional<String> getInputSchema(FlowNode element) {
    return Optional.ofNullable(element.getSingleExtensionElement(ZeebeProperties.class))
        .flatMap(
            extension ->
                extension.getProperties().stream()
                    .filter(p -> INPUT_SCHEMA_PROPERTY_NAME.equals(p.getName()))
                    .map(ZeebeProperty::getValue)
                    .findFirst())
        .filter(StringUtils::isNotBlank);
  }
}
