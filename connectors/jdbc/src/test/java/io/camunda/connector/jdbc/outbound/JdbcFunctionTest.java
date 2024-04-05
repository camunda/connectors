/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.outbound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;

// This test only tests different input validation and compliance, and secrets replacement
@ExtendWith(MockitoExtension.class)
public class JdbcFunctionTest extends OutboundBaseTest {

  private JdbcFunction objectUnderTest;

  @BeforeEach
  public void before() {
    objectUnderTest = new JdbcFunction();
  }

  @ParameterizedTest
  @MethodSource("successTestCases")
  void shouldSucceedSuccessCases(final String incomingJson) throws Exception {
    // given
    //    OutboundConnectorContext ctx =
    //        OutboundConnectorContextBuilder.create()
    //            .variables(incomingJson)
    //            .secret(SECRET_USER_NAME_KEY, SECRET_USER_NAME)
    //            .secret(SECRET_PASSWORD_KEY, SECRET_PASSWORD)
    //            .secret(SECRET_BOOTSTRAP_KEY, SECRET_BOOTSTRAP_SERVER)
    //            .secret(SECRET_TOPIC_KEY, SECRET_TOPIC_NAME)
    //            .build();

    // when
    //    objectUnderTest.execute(ctx);
    //    var request = ctx.bindVariables(JdbcRequest.class);

    // then
  }
}
