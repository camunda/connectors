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

package io.camunda.connector.runtime.jobworker.api.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class ConnectorJobHandlerTest {

  @Nested
  class Secrets {

    @Test
    public void shouldReplaceSecretsViaSpiLoadedProvider() {
      // given
      var jobHandler =
          new ConnectorJobHandler(
              (context) ->
                  context
                      .getSecretStore()
                      .replaceSecret("secrets." + TestSecretProvider.SECRET_NAME));

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result")).isEqualTo(TestSecretProvider.SECRET_VALUE);
    }

    @Test
    public void shouldOverrideSecretProvider() {
      // given
      var jobHandler =
          new TestConnectorJobHandler(
              (context) ->
                  context
                      .getSecretStore()
                      .replaceSecret("secrets." + TestSecretProvider.SECRET_NAME));

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariable("result")).isEqualTo("baz");
    }
  }

  @Nested
  class Output {

    @Test
    public void shouldNotSetWithoutResultVariable() {
      // given
      var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

      // when
      var result = JobBuilder.create().execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEmpty();
    }

    @Test
    public void shouldSetToResultVariable() {
      // given
      var jobHandler = new ConnectorJobHandler((context) -> Map.of("hello", "world"));

      // when
      var result = JobBuilder.create().withResultVariableHeader("result").execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEqualTo(Map.of("result", Map.of("hello", "world")));
    }

    @Test
    public void shouldSetToResultExpression() {
      // given
      // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

      // when
      var result =
          JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

      // then
      assertThat(result.getVariables())
          .isEqualTo(Map.of("processedOutput", Map.of("statusCode", "200 OK")));
    }

    @Test
    public void shouldSetToResultExpressionWhenPojoIsReturned() {
      // given
      // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
      final String responseValue = "response";
      var jobHandler =
          new ConnectorJobHandler((context) -> new TestConnectorResponsePojo(responseValue));

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.value }";

      // when
      var result =
          JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler);

      // then
      assertThat(result.getVariables()).isEqualTo(Map.of("processedOutput", responseValue));
    }

    @Test
    public void shouldSetBothResultVariableAndExpression() {
      // given
      // Response from service -> {"callStatus":{"statusCode":"200 OK"}}
      var jobHandler =
          new ConnectorJobHandler(
              (context) -> Map.of("callStatus", Map.of("statusCode", "200 OK")));

      final String resultVariable = "result";

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

      // when
      var result =
          JobBuilder.create()
              .withHeaders(
                  Map.of(
                      ConnectorJobHandler.RESULT_VARIABLE_HEADER_NAME, resultVariable,
                      ConnectorJobHandler.RESULT_EXPRESSION_HEADER_NAME, resultExpression))
              .execute(jobHandler);

      // then
      assertThat(result.getVariables().size()).isEqualTo(2);
      assertThat(result.getVariable("processedOutput")).isEqualTo(Map.of("statusCode", "200 OK"));
      assertThat(result.getVariable(resultVariable))
          .isEqualTo(Map.of("callStatus", Map.of("statusCode", "200 OK")));
    }

    @Test
    public void shouldSetResultVariableNullWhenCallReturnedNull() {
      // given
      final ConnectorJobHandler jobHandler = new ConnectorJobHandler((ctx) -> null);
      final String resultVariableName = "result";

      // when
      final JobBuilder.JobResult result =
          JobBuilder.create().withResultVariableHeader(resultVariableName).execute(jobHandler);

      // then
      assertThat(result.getVariables()).containsKey(resultVariableName);
      assertThat(result.getVariable(resultVariableName)).isNull();
      assertThat(result.getVariables().size()).isEqualTo(1);
    }

    @Test
    public void shouldSetResultVariableEmptyWhenCallReturnedEmpty() {
      // given
      final ConnectorJobHandler jobHandler = new ConnectorJobHandler((ctx) -> new HashMap<>());
      final String resultVariableName = "result";

      // when
      final JobBuilder.JobResult result =
          JobBuilder.create().withResultVariableHeader(resultVariableName).execute(jobHandler);

      // then
      assertThat(result.getVariables()).containsKey(resultVariableName);
      assertThat(result.getVariable(resultVariableName)).isEqualTo(Collections.EMPTY_MAP);
      assertThat(result.getVariables().size()).isEqualTo(1);
    }

    @Test
    public void shouldProduceFailCommandWhenResultExpressionIsDefinedAndCallReturnedNull() {
      // given
      // Response from service -> null
      var jobHandler = new ConnectorJobHandler((context) -> null);

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

      // when & then
      JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler, false);
    }

    @Test
    public void shouldProduceFailCommandWhenResultExpressionIsDefinedAndCallReturnedEmpty() {
      // given
      // Response from service -> empty
      var jobHandler = new ConnectorJobHandler((context) -> new HashMap<>());

      // FEEL expression -> {"processedOutput":response.callStatus}
      final String resultExpression = "{\"processedOutput\": response.callStatus }";

      // when & then
      JobBuilder.create().withResultExpressionHeader(resultExpression).execute(jobHandler, false);
    }
  }

  private static class TestConnectorJobHandler extends ConnectorJobHandler {

    public TestConnectorJobHandler(OutboundConnectorFunction call) {
      super(call);
    }

    @Override
    public SecretProvider getSecretProvider() {
      return name -> TestSecretProvider.SECRET_NAME.equals(name) ? "baz" : null;
    }
  }

  private static class TestConnectorResponsePojo {
    private final String value;

    private TestConnectorResponsePojo(final String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }
}
