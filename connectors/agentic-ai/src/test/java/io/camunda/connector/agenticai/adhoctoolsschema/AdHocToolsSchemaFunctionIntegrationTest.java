/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.adhoctoolsschema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElementParameter;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaRequest;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaRequest.AdHocToolsSchemaRequestData;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.CamundaClientProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.FeelExpressionParameterExtractionException;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.feel.FeelExpressionParameterExtractor;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerationException;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolSchemaGenerator;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolverImpl;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.model.tool.GatewayToolDefinition;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdHocToolsSchemaFunctionIntegrationTest {

  private static final Long PROCESS_DEFINITION_KEY = 123456L;
  private static final String AD_HOC_SUB_PROCESS_ID = "Agent_Tools";

  private static final Map<String, Object> EXPECTED_EMPTY_SCHEMA =
      Map.of("type", "object", "comment", "emptySchema", "dummy", true);
  private static final Map<String, Object> EXPECTED_TOOL_A_SCHEMA =
      Map.of("type", "object", "comment", "toolASchema", "dummy", true);

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private FeelExpressionParameterExtractor parameterExtractor;
  @Mock private AdHocToolSchemaGenerator schemaGenerator;

  private AdHocToolsSchemaFunction function;
  private AdHocToolsSchemaFunction functionWithGatewayToolDefinitionResolvers;

  private String bpmnXml;

  @BeforeEach
  void setUp() throws IOException {
    final var toolElementsResolver =
        new CamundaClientProcessDefinitionAdHocToolElementsResolver(
            camundaClient, parameterExtractor);

    function =
        new AdHocToolsSchemaFunction(
            toolElementsResolver, new AdHocToolsSchemaResolverImpl(List.of(), schemaGenerator));

    functionWithGatewayToolDefinitionResolvers =
        new AdHocToolsSchemaFunction(
            toolElementsResolver,
            new AdHocToolsSchemaResolverImpl(
                List.of(
                    new SimpleToolGatewayToolDefinitionResolver(),
                    new ComplexToolGatewayToolDefinitionResolver()),
                schemaGenerator));

    bpmnXml =
        Files.readString(
            Path.of(getClass().getClassLoader().getResource("ad-hoc-tools.bpmn").getFile()));
  }

  @Test
  void loadsAdHocToolSchema() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    final var toolAParameters =
        List.of(
            new AdHocToolElementParameter("inputParameter", "An input parameter"),
            new AdHocToolElementParameter("outputParameter", "An output parameter"));

    doReturn(List.of(toolAParameters.get(0)))
        .when(parameterExtractor)
        .extractParameters("fromAi(toolCall.inputParameter, \"An input parameter\")");
    doReturn(List.of(toolAParameters.get(1)))
        .when(parameterExtractor)
        .extractParameters("fromAi(toolCall.outputParameter, \"An output parameter\")");

    when(schemaGenerator.generateToolSchema(any())).thenReturn(EXPECTED_EMPTY_SCHEMA);
    doReturn(EXPECTED_TOOL_A_SCHEMA)
        .when(schemaGenerator)
        .generateToolSchema(argThat(element -> element.elementId().equals("Tool_A")));

    final var result =
        function.execute(outboundConnectorContext(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID));

    assertThat(result).isNotNull();
    assertThat(result.toolDefinitions())
        .isNotEmpty()
        .containsExactlyInAnyOrder(
            ToolDefinition.builder()
                .name("Simple_Tool")
                .description("A simple tool")
                .inputSchema(EXPECTED_EMPTY_SCHEMA)
                .build(),
            ToolDefinition.builder()
                .name("Tool_A")
                .description("The A tool")
                .inputSchema(EXPECTED_TOOL_A_SCHEMA)
                .build(),
            ToolDefinition.builder()
                .name("An_Event")
                .description("An event!")
                .inputSchema(EXPECTED_EMPTY_SCHEMA)
                .build(),
            ToolDefinition.builder()
                .name("A_Complex_Tool")
                .description("A very complex tool")
                .inputSchema(EXPECTED_EMPTY_SCHEMA)
                .build());

    assertThat(result.toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain(
            "Tool_B", "Event_Follow_Up_Task", "Complex_Tool_Error", "Handle_Complex_Tool_Error");
    assertThat(result.gatewayToolDefinitions()).isEmpty();
  }

  @Test
  void loadsAdHocToolSchemaWithGatewayToolDefinitions() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    final var toolAParameters =
        List.of(
            new AdHocToolElementParameter("inputParameter", "An input parameter"),
            new AdHocToolElementParameter("outputParameter", "An output parameter"));

    doReturn(List.of(toolAParameters.get(0)))
        .when(parameterExtractor)
        .extractParameters("fromAi(toolCall.inputParameter, \"An input parameter\")");
    doReturn(List.of(toolAParameters.get(1)))
        .when(parameterExtractor)
        .extractParameters("fromAi(toolCall.outputParameter, \"An output parameter\")");

    when(schemaGenerator.generateToolSchema(any())).thenReturn(EXPECTED_EMPTY_SCHEMA);
    doReturn(EXPECTED_TOOL_A_SCHEMA)
        .when(schemaGenerator)
        .generateToolSchema(argThat(element -> element.elementId().equals("Tool_A")));

    final var result =
        functionWithGatewayToolDefinitionResolvers.execute(
            outboundConnectorContext(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID));

    assertThat(result).isNotNull();
    assertThat(result.toolDefinitions())
        .isNotEmpty()
        .containsExactlyInAnyOrder(
            ToolDefinition.builder()
                .name("Tool_A")
                .description("The A tool")
                .inputSchema(EXPECTED_TOOL_A_SCHEMA)
                .build(),
            ToolDefinition.builder()
                .name("An_Event")
                .description("An event!")
                .inputSchema(EXPECTED_EMPTY_SCHEMA)
                .build());
    assertThat(result.toolDefinitions())
        .extracting(ToolDefinition::name)
        .doesNotContain(
            "Simple_Tool",
            "A_Complex_Tool",
            "Tool_B",
            "Event_Follow_Up_Task",
            "Complex_Tool_Error",
            "Handle_Complex_Tool_Error");

    assertThat(result.gatewayToolDefinitions())
        .isNotEmpty()
        .containsExactlyInAnyOrder(
            GatewayToolDefinition.builder()
                .type("simpleTool")
                .name("Simple_Tool")
                .description("A simple tool")
                .properties(Map.of("simple", true))
                .build(),
            GatewayToolDefinition.builder()
                .type("complexTool")
                .name("A_Complex_Tool")
                .description("A very complex tool")
                .properties(Map.of("toolType", "complex"))
                .build());
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -10})
  void throwsExceptionWhenProcessDefinitionKeyIsInvalid(Long processDefinitionKey) {
    assertThatThrownBy(
            () ->
                function.execute(
                    outboundConnectorContext(processDefinitionKey, AD_HOC_SUB_PROCESS_ID)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Process definition key must not be null or negative");

    verifyNoInteractions(camundaClient, parameterExtractor, schemaGenerator);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void throwsExceptionWhenAdHocSubProcessIdIsInvalid(String adHocSubProcessId) {
    assertThatThrownBy(
            () ->
                function.execute(
                    outboundConnectorContext(PROCESS_DEFINITION_KEY, adHocSubProcessId)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("adHocSubProcessId cannot be null or empty");

    verifyNoInteractions(camundaClient, parameterExtractor, schemaGenerator);
  }

  @Test
  void throwsExceptionWhenBpmnXmlIsInvalid() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn("DUMMY");

    assertThatThrownBy(
            () ->
                function.execute(
                    outboundConnectorContext(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID)))
        .isInstanceOf(ModelParseException.class);
  }

  @Test
  void throwsExceptionWhenAdHocSubProcessWithRequestedIdDoesNotExist() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    assertThatThrownBy(
            () -> function.execute(outboundConnectorContext(PROCESS_DEFINITION_KEY, "DUMMY")))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("AD_HOC_SUB_PROCESS_NOT_FOUND");
              assertThat(e.getMessage())
                  .isEqualTo(
                      "Unable to resolve tool elements. Ad-hoc sub-process with ID 'DUMMY' was not found.");
            });
  }

  @Test
  void throwsExceptionWhenParameterExtractionFails() {
    when(camundaClient.newProcessDefinitionGetXmlRequest(PROCESS_DEFINITION_KEY).send().join())
        .thenReturn(bpmnXml);

    doThrow(new FeelExpressionParameterExtractionException("I can't handle the fromAi function."))
        .when(parameterExtractor)
        .extractParameters("fromAi(toolCall.inputParameter, \"An input parameter\")");

    assertThatThrownBy(
            () ->
                function.execute(
                    outboundConnectorContext(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID)))
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

    when(parameterExtractor.extractParameters(any())).thenReturn(Collections.emptyList());
    doThrow(new FeelExpressionParameterExtractionException("I can't handle the fromAi function."))
        .when(parameterExtractor)
        .extractParameters("fromAi(toolCall.outputParameter, \"An output parameter\")");

    assertThatThrownBy(
            () ->
                function.execute(
                    outboundConnectorContext(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID)))
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
        .thenThrow(new AdHocToolSchemaGenerationException("I can't generate this schema"));

    assertThatThrownBy(
            () ->
                function.execute(
                    outboundConnectorContext(PROCESS_DEFINITION_KEY, AD_HOC_SUB_PROCESS_ID)))
        .isInstanceOfSatisfying(
            ConnectorException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo("AD_HOC_TOOL_SCHEMA_INVALID");
              assertThat(e.getMessage()).isEqualTo("I can't generate this schema");
            });
  }

  private OutboundConnectorContext outboundConnectorContext(
      Long processDefinitionKey, String containerElementId) {
    final var outboundConnectorContext =
        mock(OutboundConnectorContext.class, Answers.RETURNS_DEEP_STUBS);

    when(outboundConnectorContext.getJobContext().getProcessDefinitionKey())
        .thenReturn(processDefinitionKey);
    when(outboundConnectorContext.bindVariables(AdHocToolsSchemaRequest.class))
        .thenReturn(
            new AdHocToolsSchemaRequest(new AdHocToolsSchemaRequestData(containerElementId)));

    return outboundConnectorContext;
  }

  private static class SimpleToolGatewayToolDefinitionResolver
      implements GatewayToolDefinitionResolver {

    @Override
    public List<GatewayToolDefinition> resolveGatewayToolDefinitions(
        List<AdHocToolElement> elements) {
      return elements.stream()
          .filter(element -> element.elementId().equals("Simple_Tool"))
          .map(
              element ->
                  GatewayToolDefinition.builder()
                      .type("simpleTool")
                      .name(element.elementId())
                      .description(element.documentationWithNameFallback())
                      .properties(Map.of("simple", true))
                      .build())
          .toList();
    }
  }

  private static class ComplexToolGatewayToolDefinitionResolver
      implements GatewayToolDefinitionResolver {

    @Override
    public List<GatewayToolDefinition> resolveGatewayToolDefinitions(
        List<AdHocToolElement> elements) {
      return elements.stream()
          .filter(element -> element.elementId().equals("A_Complex_Tool"))
          .map(
              element ->
                  GatewayToolDefinition.builder()
                      .type("complexTool")
                      .name(element.elementId())
                      .description(element.documentationWithNameFallback())
                      .properties(Map.of("toolType", "complex"))
                      .build())
          .toList();
    }
  }
}
