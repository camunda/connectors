/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.example;

public class MyFunctionTest {

  /*ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldReturnReceivedMessageWhenExecute() throws Exception {
    // given
    var input = new MyConnectorRequest(
            "Hello World!",
            new Authentication("testUser", "testToken")
    );
    var function = new MyConnectorFunction();
    var context = OutboundConnectorContextBuilder.create()
      .variables(objectMapper.writeValueAsString(input))
      .build();
    // when
    var result = function.execute(context);
    // then
    assertThat(result)
      .isInstanceOf(MyConnectorResult.class)
      .extracting("myProperty")
      .isEqualTo("Message received: Hello World!");
  }

  @Test
  void shouldThrowWithErrorCodeWhenMessageStartsWithFail() throws Exception {
    // given
    var input = new MyConnectorRequest(
            "Fail: unauthorized",
            new Authentication("testUser", "testToken")
    );
    var function = new MyConnectorFunction();
    var context = OutboundConnectorContextBuilder.create()
        .variables(objectMapper.writeValueAsString(input))
        .build();
    // when
    var result = catchThrowable(() -> function.execute(context));
    // then
    assertThat(result)
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("started with 'fail'")
        .extracting("errorCode").isEqualTo("FAIL");
  }*/
}
