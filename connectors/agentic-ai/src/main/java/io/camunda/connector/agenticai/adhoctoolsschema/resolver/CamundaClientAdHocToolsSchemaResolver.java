/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse.AdHocToolDefinition.JsonSchema;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaClientAdHocToolsSchemaResolver implements AdHocToolsSchemaResolver {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaClientAdHocToolsSchemaResolver.class);

  private final CamundaClient camundaClient;
  private final ObjectMapper objectMapper;

  public CamundaClientAdHocToolsSchemaResolver(
      CamundaClient camundaClient, ObjectMapper objectMapper) {
    this.camundaClient = camundaClient;
    this.objectMapper = objectMapper;
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

    if (!documentation.isBlank() && documentation.trim().startsWith("{")) {
      try {
        var partialToolDefinition =
            objectMapper.readValue(documentation, AdHocToolDefinition.class);
        return new AdHocToolDefinition(
            element.getId(),
            Optional.ofNullable(partialToolDefinition.description()).orElse(element.getName()),
            Optional.ofNullable(partialToolDefinition.inputSchema()).orElseGet(JsonSchema::empty));
      } catch (Exception e) {
        LOGGER.error("Failed to parse tool definition from documentation", e);
      }
    }

    return new AdHocToolDefinition(element.getId(), documentation, JsonSchema.empty());
  }

  private String getDocumentation(FlowNode element) {
    return element.getDocumentations().stream()
        .filter(d -> "text/plain".equals(d.getTextFormat()))
        .findFirst()
        .map(ModelElementInstance::getTextContent)
        .orElse("");
  }
}
