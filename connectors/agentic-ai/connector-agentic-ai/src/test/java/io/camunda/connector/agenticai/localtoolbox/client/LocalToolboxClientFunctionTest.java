/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolElement;
import io.camunda.connector.agenticai.adhoctoolsschema.model.AdHocToolsSchemaResponse;
import io.camunda.connector.agenticai.adhoctoolsschema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.localtoolbox.client.model.LocalToolboxClientRequest;
import io.camunda.connector.agenticai.localtoolbox.client.model.LocalToolboxClientRequest.LocalToolboxClientRequestData;
import io.camunda.connector.agenticai.localtoolbox.client.model.LocalToolboxClientRequest.LocalToolboxClientRequestData.LocalToolboxOperationConfiguration;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxCallToolResult;
import io.camunda.connector.agenticai.localtoolbox.client.model.result.LocalToolboxListToolsResult;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalToolboxClientFunctionTest {

  private static final String PROCESS_ID = "toolbox-process";
  private static final String CONTAINER_ELEMENT_ID = "Tools";
  private static final Long PROCESS_DEFINITION_KEY = 123456L;

  @Mock private LocalToolboxProcessDefinitionResolver processDefinitionResolver;
  @Mock private ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  @Mock private AdHocToolsSchemaResolver toolsSchemaResolver;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  private LocalToolboxClientFunction function;

  @BeforeEach
  void setUp() {
    function =
        new LocalToolboxClientFunction(
            processDefinitionResolver, toolElementsResolver, toolsSchemaResolver, camundaClient);
  }

  @Nested
  class ListTools {

    @Test
    void resolvesToolSchemaFromReferencedProcess() {
      var elements = List.of(mock(AdHocToolElement.class));
      var toolDefinitions =
          List.of(ToolDefinition.builder().name("tool1").inputSchema(Map.of()).build());

      when(processDefinitionResolver.resolveProcessDefinitionKey(PROCESS_ID, null))
          .thenReturn(PROCESS_DEFINITION_KEY);
      when(toolElementsResolver.resolveToolElements(PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
          .thenReturn(elements);
      when(toolsSchemaResolver.resolveAdHocToolsSchema(elements))
          .thenReturn(new AdHocToolsSchemaResponse(toolDefinitions, List.of()));

      var result = function.execute(context(request(null, "tools/list", Map.of())));

      assertThat(result).isInstanceOf(LocalToolboxListToolsResult.class);
      assertThat(((LocalToolboxListToolsResult) result).toolDefinitions())
          .isEqualTo(toolDefinitions);
    }

    @Test
    void resolvesPinnedVersion() {
      when(processDefinitionResolver.resolveProcessDefinitionKey(PROCESS_ID, 3))
          .thenReturn(PROCESS_DEFINITION_KEY);
      when(toolElementsResolver.resolveToolElements(PROCESS_DEFINITION_KEY, CONTAINER_ELEMENT_ID))
          .thenReturn(List.of());
      when(toolsSchemaResolver.resolveAdHocToolsSchema(List.of()))
          .thenReturn(new AdHocToolsSchemaResponse(List.of(), List.of()));

      function.execute(context(request(3, "tools/list", Map.of())));

      verify(processDefinitionResolver).resolveProcessDefinitionKey(PROCESS_ID, 3);
    }
  }

  @Nested
  class CallTool {

    @Test
    void createsToolboxInstance_usingLatestVersion() {
      var arguments = Map.<String, Object>of("x", "y");
      when(camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .variables(anyMap())
              .withResult()
              .send()
              .join()
              .getVariablesAsMap())
          .thenReturn(Map.of("toolCallResult", "the result"));

      var result =
          function.execute(
              context(
                  request(
                      null, "tools/call", Map.of("name", "mySharedTool", "arguments", arguments))));

      assertThat(result).isInstanceOf(LocalToolboxCallToolResult.class);
      var callToolResult = (LocalToolboxCallToolResult) result;
      assertThat(callToolResult.name()).isEqualTo("mySharedTool");
      assertThat(callToolResult.content()).isEqualTo("the result");
    }

    @Test
    void createsToolboxInstance_usingPinnedVersion() {
      when(camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .version(2)
              .variables(anyMap())
              .withResult()
              .send()
              .join()
              .getVariablesAsMap())
          .thenReturn(Map.of("toolCallResult", "pinned result"));

      var result =
          function.execute(
              context(
                  request(2, "tools/call", Map.of("name", "mySharedTool", "arguments", Map.of()))));

      assertThat(((LocalToolboxCallToolResult) result).content()).isEqualTo("pinned result");
    }

    @Test
    void passesToolCallAndMetaVariablesToTheCreatedInstance() {
      when(camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .variables(anyMap())
              .withResult()
              .send()
              .join()
              .getVariablesAsMap())
          .thenReturn(Map.of());

      function.execute(
          context(
              request(
                  null,
                  "tools/call",
                  Map.of("name", "mySharedTool", "arguments", Map.of("x", "y")),
                  Map.of("tenantId", "t-1"))));

      verify(camundaClient.newCreateInstanceCommand().bpmnProcessId(PROCESS_ID).latestVersion())
          .variables(
              eq(
                  Map.of(
                      "toolCall", Map.of("name", "mySharedTool", "arguments", Map.of("x", "y")),
                      "meta", Map.of("tenantId", "t-1"))));
    }
  }

  private LocalToolboxClientRequest request(
      Integer version, String method, Map<String, Object> params) {
    return request(version, method, params, null);
  }

  private LocalToolboxClientRequest request(
      Integer version, String method, Map<String, Object> params, Map<String, Object> meta) {
    return new LocalToolboxClientRequest(
        new LocalToolboxClientRequestData(
            PROCESS_ID,
            version,
            CONTAINER_ELEMENT_ID,
            meta,
            new LocalToolboxOperationConfiguration(method, params)));
  }

  private OutboundConnectorContext context(LocalToolboxClientRequest request) {
    var context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(LocalToolboxClientRequest.class)).thenReturn(request);
    return context;
  }
}
