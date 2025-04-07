/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.schema;

import static io.camunda.connector.agenticai.mapping.JacksonExceptionMessageExtractor.humanReadableJsonProcessingExceptionMessage;

import com.fasterxml.jackson.core.JsonParseException;
import io.camunda.client.CamundaClient;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.AdHocSubProcess;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.modelcontextprotocol.spec.McpSchema;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CamundaClientAdHocToolsSchemaResolver implements AdHocToolsSchemaResolver {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CamundaClientAdHocToolsSchemaResolver.class);

  private static final String INPUT_SCHEMA_PROPERTY_NAME = "camunda:adHocActivityInputSchema";
  private static final String EMPTY_INPUT_SCHEMA =
      """
        {
          "type": "object",
          "properties": {},
          "required": []
        }
        """;

  private final CamundaClient camundaClient;

  public CamundaClientAdHocToolsSchemaResolver(CamundaClient camundaClient) {
    this.camundaClient = camundaClient;
  }

  @Override
  public AdHocToolsSchema resolveSchema(Long processDefinitionKey, String adHocSubprocessId) {
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

    final var toolDefinitionMappings =
        adHocSubProcess.getChildElementsByType(FlowNode.class).stream()
            .filter(element -> element.getIncoming().isEmpty())
            .map(this::mapActivityToToolDefinitions)
            .toList();

    final var tools = toolDefinitionMappings.stream().flatMap(t -> t.tools().stream()).toList();

    return new AdHocToolsSchema(tools);
  }

  private ActivityToToolsMapping mapActivityToToolDefinitions(FlowNode element) {
    final var documentation =
        element.getDocumentations().stream()
            .filter(d -> "text/plain".equals(d.getTextFormat()))
            .findFirst()
            .map(ModelElementInstance::getTextContent)
            .orElse("");

    final var inputSchemaProperty =
        Optional.ofNullable(element.getSingleExtensionElement(ZeebeProperties.class))
            .flatMap(
                extension ->
                    extension.getProperties().stream()
                        .filter(p -> INPUT_SCHEMA_PROPERTY_NAME.equals(p.getName()))
                        .findFirst())
            .orElse(null);

    // from defined input JSON schema
    if (inputSchemaProperty != null) {
      try {
        return new ActivityToToolsMapping(
            new McpSchema.Tool(element.getId(), documentation, inputSchemaProperty.getValue()));
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

    // from documentation with empty schema
    return new ActivityToToolsMapping(
        new McpSchema.Tool(element.getId(), documentation, EMPTY_INPUT_SCHEMA));
  }

  // wrapper around a single activity possibly exposing multiple tools (e.g. MCP)
  private record ActivityToToolsMapping(List<McpSchema.Tool> tools) {
    public ActivityToToolsMapping(McpSchema.Tool tool) {
      this(List.of(tool));
    }
  }
}
