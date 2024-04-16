/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.outbound;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

// This test only tests different input validation and compliance, and secrets replacement
@ExtendWith(MockitoExtension.class)
public class JdbcFunctionTest extends OutboundBaseTest {

  @Test
  void shouldSucceedSuccessCases() throws Exception {
    // given
    //    var input = new JdbcRequest(SD);
    //    var function = new JdbcFunction();
    //    var context =
    //        OutboundConnectorContextBuilder.create()
    //            .variables(objectMapper.writeValueAsString(input))
    //            .build();
    //    // when
    //    var result = function.execute(context);
    //
    //    // then
    //    assertThat(result)
    //        .isInstanceOf(MyConnectorResult.class)
    //        .extracting("myProperty")
    //        .isEqualTo("Message received: Hello World!");

    // then
  }
}
