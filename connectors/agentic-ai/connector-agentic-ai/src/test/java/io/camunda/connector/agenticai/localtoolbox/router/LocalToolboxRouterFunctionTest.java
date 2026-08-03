/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.localtoolbox.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallProcessVariable;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.localtoolbox.router.LocalToolboxRouterFunction.LocalToolboxRouterRequest;
import io.camunda.connector.agenticai.localtoolbox.router.LocalToolboxRouterFunction.LocalToolboxRouterRequest.ToolCallRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LocalToolboxRouterFunctionTest {

  private final LocalToolboxRouterFunction function = new LocalToolboxRouterFunction();

  @Nested
  class FirstInvocation {

    @Test
    void activatesTheRequestedToolElement() {
      var toolCall = new ToolCallRequest("mySharedTool", Map.of("x", "y"));

      var result = function.execute(context(new LocalToolboxRouterRequest(toolCall, List.of())));

      assertThat(result.completionConditionFulfilled()).isFalse();
      assertThat(result.cancelRemainingInstances()).isFalse();
      assertThat(result.elementActivations())
          .singleElement()
          .satisfies(
              activation -> {
                assertThat(activation.elementId()).isEqualTo("mySharedTool");
                var variables = activation.variables();
                assertThat(variables).containsEntry("toolCallResult", "");
                assertThat(variables.get("toolCall"))
                    .isEqualTo(
                        new ToolCallProcessVariable("call-1", "mySharedTool", Map.of("x", "y")));
              });
    }

    @Test
    void throwsException_whenToolCallVariableIsMissing() {
      assertThatThrownBy(
              () -> function.execute(context(new LocalToolboxRouterRequest(null, List.of()))))
          .isInstanceOfSatisfying(
              ConnectorException.class,
              e ->
                  assertThat(e.getErrorCode()).isEqualTo("LOCAL_TOOLBOX_INVALID_TOOL_DEFINITIONS"));
    }
  }

  @Nested
  class SecondInvocation {

    @Test
    void completesWithTheToolResult_onceResultIsPresent() {
      var toolCall = new ToolCallRequest("mySharedTool", Map.of("x", "y"));
      var toolCallResults =
          List.of(
              ToolCallResult.builder()
                  .id("call-1")
                  .name("mySharedTool")
                  .content("the tool result")
                  .build());

      var result =
          function.execute(context(new LocalToolboxRouterRequest(toolCall, toolCallResults)));

      assertThat(result.completionConditionFulfilled()).isTrue();
      assertThat(result.elementActivations()).isEmpty();
      assertThat(result.variables()).containsEntry("toolCallResult", "the tool result");
    }

    @Test
    void ignoresUnrelatedToolCallResults() {
      var toolCall = new ToolCallRequest("mySharedTool", Map.of());
      var toolCallResults =
          List.of(ToolCallResult.builder().id("other-call").name("other").content("x").build());

      var result =
          function.execute(context(new LocalToolboxRouterRequest(toolCall, toolCallResults)));

      assertThat(result.completionConditionFulfilled()).isFalse();
      assertThat(result.elementActivations())
          .singleElement()
          .satisfies(activation -> assertThat(activation.elementId()).isEqualTo("mySharedTool"));
    }
  }

  private OutboundConnectorContext context(LocalToolboxRouterRequest request) {
    var context = mock(OutboundConnectorContext.class);
    when(context.bindVariables(LocalToolboxRouterRequest.class)).thenReturn(request);
    return context;
  }
}
