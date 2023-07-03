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
package io.camunda.connector.test.outbound;

import static io.camunda.connector.example.outbound.ExampleTestData.Object;
import static io.camunda.connector.example.outbound.ExampleTestData.ObjectAndListWithSecrets;
import static io.camunda.connector.example.outbound.ExampleTestData.ObjectWithSecret;
import static io.camunda.connector.example.outbound.ExampleTestData.SingleKeyObjectWithSecret;
import static io.camunda.connector.example.outbound.ExampleTestData.SingleKeyObjectWithSecretInParentheses;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.example.outbound.ExampleNumberOutboundInput;
import io.camunda.connector.example.outbound.ExampleOutboundFunction;
import io.camunda.connector.example.outbound.ExampleOutboundInput;
import org.junit.jupiter.api.Test;

public class OutboundConnectorFunctionTest {

  ExampleOutboundFunction fn = new ExampleOutboundFunction();

  @Test
  public void shouldExecuteConnector() throws Exception {
    var context = OutboundConnectorContextBuilder.create().variables(Object).build();
    var result = (ExampleOutboundInput) fn.execute(context);
    assertThat(result.getFoo()).isEqualTo("FOO");
  }

  @Test
  public void shouldReplaceSecret() throws Exception {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(ObjectWithSecret)
            .secret("value", "SECRET_FOO")
            .build();
    var result = (ExampleOutboundInput) fn.execute(context);
    assertThat(result.getFoo()).isEqualTo("SECRET_FOO");
  }

  @Test
  public void shouldReplaceSecretInList() throws Exception {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(ObjectAndListWithSecrets)
            .secret("value", "SECRET_FOO")
            .build();
    var result = (ExampleOutboundInput) fn.execute(context);
    assertThat(result.getFoos().get(1)).isEqualTo("SECRET_FOO");
  }

  @Test
  public void shouldReplaceSecretWithNumber() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(SingleKeyObjectWithSecret)
            .secret("value", "1")
            .build();
    var example = context.bindVariables(ExampleNumberOutboundInput.class);
    assertThat(example.foo()).isEqualTo(1);
  }

  @Test
  public void shouldReplaceSecretWithParentheses() {
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(SingleKeyObjectWithSecretInParentheses)
            .secret("value", "mySecret")
            .build();
    var example = context.bindVariables(ExampleOutboundInput.class);
    assertThat(example.getFoo()).isEqualTo("mySecret");
  }
}
