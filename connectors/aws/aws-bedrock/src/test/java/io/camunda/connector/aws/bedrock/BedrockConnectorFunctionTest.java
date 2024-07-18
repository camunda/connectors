/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.aws.bedrock.core.BedrockExecutor;
import io.camunda.connector.aws.bedrock.model.InvokeModelWrappedResponse;
import io.camunda.connector.test.outbound.OutboundConnectorContextBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class BedrockConnectorFunctionTest extends BaseTest {

  @ParameterizedTest
  @MethodSource("loadInvokeModelVariables")
  void executeInvokeModelReturnsCorrectResult(String variables) {

    var bedrockConnectorFunction = new BedrockConnectorFunction();
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var bedrockExecutor = Mockito.mock(BedrockExecutor.class);

    try (MockedStatic<BedrockExecutor> bedrockExecutorMockedStatic =
        Mockito.mockStatic(BedrockExecutor.class)) {
      bedrockExecutorMockedStatic
          .when(() -> BedrockExecutor.create(any()))
          .thenReturn(bedrockExecutor);
      when(bedrockExecutor.execute()).thenReturn(new InvokeModelWrappedResponse("Hello"));
      var response = bedrockConnectorFunction.execute(context);
      Assertions.assertNotNull(response);
      Assertions.assertInstanceOf(InvokeModelWrappedResponse.class, response);
    }
  }

  @ParameterizedTest
  @MethodSource("loadConverseVariables")
  void executeConverseReturnsCorrectResult(String variables) {

    var bedrockConnectorFunction = new BedrockConnectorFunction();
    var context = OutboundConnectorContextBuilder.create().variables(variables).build();

    var bedrockExecutor = Mockito.mock(BedrockExecutor.class);

    try (MockedStatic<BedrockExecutor> bedrockExecutorMockedStatic =
        Mockito.mockStatic(BedrockExecutor.class)) {
      bedrockExecutorMockedStatic
          .when(() -> BedrockExecutor.create(any()))
          .thenReturn(bedrockExecutor);
      when(bedrockExecutor.execute()).thenReturn(new InvokeModelWrappedResponse("Hello"));
      var response = bedrockConnectorFunction.execute(context);
      Assertions.assertNotNull(response);
      Assertions.assertInstanceOf(InvokeModelWrappedResponse.class, response);
    }
  }
}
