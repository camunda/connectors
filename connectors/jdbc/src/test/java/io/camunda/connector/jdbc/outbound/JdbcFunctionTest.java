/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.jdbc.outbound;

import io.camunda.connector.jdbc.model.JdbcRequest;
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
