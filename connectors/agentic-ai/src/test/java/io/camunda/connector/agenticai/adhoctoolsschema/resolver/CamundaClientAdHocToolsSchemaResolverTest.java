/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParam;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractionException;
import io.camunda.connector.agenticai.adhoctoolsschema.feel.FeelInputParamExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.schema.SchemaGenerationException;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.model.xml.ModelParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CamundaClientAdHocToolsSchemaResolverTest {

  private static final Long PROCESS_DEFINITION_KEY = 123456L;
  private static final String AD_HOC_SUB_PROCESS_ID = "Agent_Tools";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private FeelInputParamExtractor feelInputParamExtractor;
  @Mock private AdHocToolSchemaGenerator schemaGenerator;
  @InjectMocks private CamundaClientAdHocToolsSchemaResolver resolver;

  private String bpmnXml;

  @BeforeEach
  void setUp() throws IOException {
    bpmnXml =
        Files.readString(
            Path.of(getClass().getClassLoader().getResource("ad-hoc-tools.bpmn").getFile()));
  }

  @Test
  void loadsAdHocToolSchema() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    final Map<String, Object> expectedToolASchema =
        Map.of("type", "object", "comment", "toolASchema", "dummy", true);
    final Map<String, Object> expectedEmptySchema =
        Map.of("type", "object", "comment", "emptySchema", "dummy", true);

    final var toolAInputParams =
        List.of(
            new FeelInputParam("inputParameter", "An input parameter"),
            new FeelInputParam("outputParameter", "An output parameter"));

    doReturn(List.of(toolAInputParams.get(0)))
        .when(feelInputParamExtractor)
        .extractInputParams("fromAi(toolCall.inputParameter, \"An input parameter\")");
    doReturn(List.of(toolAInputParams.get(1)))
        .when(feelInputParamExtractor)
        .extractInputParams("fromAi(toolCall.outputParameter, \"An output parameter\")");

    when(schemaGenerator.generateToolSchema(any())).thenReturn(expectedEmptySchema);
    doReturn(expectedToolASchema).when(schemaGenerator).generateToolSchema(toolAInputParams);

    final var result = resolver.resolveSchema(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID);

    assertThat(result).isNotNull();
    assertThat(result.toolDefinitions())
        .isNotEmpty()
        .satisfiesExactlyInAnyOrder(
            td -> {
              assertThat(td.name()).isEqualTo("A_Complex_Tool");
              assertThat(td.description()).isEqualTo("A very complex tool");
              assertThat(td.inputSchema()).isEqualTo(expectedEmptySchema);
            },
            td -> {
              assertThat(td.name()).isEqualTo("Tool_A");
              assertThat(td.description()).isEqualTo("The A tool");
              assertThat(td.inputSchema()).isEqualTo(expectedToolASchema);
            },
            td -> {
              assertThat(td.name()).isEqualTo("Simple_Tool");
              assertThat(td.description()).isEqualTo("A simple tool");
              assertThat(td.inputSchema()).isEqualTo(expectedEmptySchema);
            },
            td -> {
              assertThat(td.name()).isEqualTo("An_Event");
              assertThat(td.description()).isEqualTo("An event!");
              assertThat(td.inputSchema()).isEqualTo(expectedEmptySchema);
            });

    assertThat(result.toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain(
            "Tool_B", "Event_Follow_Up_Task", "Complex_Tool_Error", "Handle_Complex_Tool_Error");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(longs = {0, -10})
  void throwsExceptionWhenProcessDefinitionKeyIsInvalid(Long processDefinitionKey) {
    assertThatThrownBy(() -> resolver.resolveSchema(processDefinitionKey, "AHSP"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Process definition key must not be null or negative");

    verifyNoInteractions(camundaClient, feelInputParamExtractor, schemaGenerator);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void throwsExceptionWhenAdHocSubProcessIdIsInvalid(String adHocSubProcessId) {
    assertThatThrownBy(() -> resolver.resolveSchema(123456L, adHocSubProcessId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("adHocSubProcessId cannot be null or empty");

    verifyNoInteractions(camundaClient, feelInputParamExtractor, schemaGenerator);
  }

  @Test
  void throwsExceptionWhenBpmnXmlIsInvalid() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn("DUMMY");

    assertThatThrownBy(() -> resolver.resolveSchema(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID))
        .isInstanceOf(ModelParseException.class);
  }

  @Test
  void throwsExceptionWhenAdHocSubProcessWithRequestedIdDoesNotExist() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    assertThatThrownBy(() -> resolver.resolveSchema(PROCESS_DEFINITION_KEY, "DUMMY"))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("AD_HOC_SUB_PROCESS_NOT_FOUND");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Unable to resolve tools schema. Ad-hoc sub-process with ID 'DUMMY' was not found.");
            });
  }

  @Test
  void throwsExceptionWhenInputParamExtractionFails() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    doThrow(new FeelInputParamExtractionException("I can't handle the fromAi function."))
        .when(feelInputParamExtractor)
        .extractInputParams("fromAi(toolCall.inputParameter, \"An input parameter\")");

    assertThatThrownBy(() -> resolver.resolveSchema(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("AD_HOC_TOOL_DEFINITION_INVALID");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Invalid ad-hoc tool definition for element 'Tool_A' on input mapping 'inputParameter': I can't handle the fromAi function.");
            });
  }

  @Test
  void throwsExceptionWhenOutputParamExtractionFails() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    when(feelInputParamExtractor.extractInputParams(any())).thenReturn(Collections.emptyList());
    doThrow(new FeelInputParamExtractionException("I can't handle the fromAi function."))
        .when(feelInputParamExtractor)
        .extractInputParams("fromAi(toolCall.outputParameter, \"An output parameter\")");

    assertThatThrownBy(() -> resolver.resolveSchema(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("AD_HOC_TOOL_DEFINITION_INVALID");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Invalid ad-hoc tool definition for element 'Tool_A' on output mapping 'outputParameter': I can't handle the fromAi function.");
            });
  }

  @Test
  void throwsExceptionWhenSchemaGenerationFails() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);
    when(schemaGenerator.generateToolSchema(any()))
        .thenThrow(new SchemaGenerationException("I can't generate this schema"));

    assertThatThrownBy(() -> resolver.resolveSchema(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("AD_HOC_TOOL_SCHEMA_INVALID");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Failed to generate ad-hoc tool schema for element 'A_Complex_Tool': I can't generate this schema");
            });
  }
}
