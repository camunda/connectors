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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import io.camunda.connector.example.outbound.ExampleOutboundFunction;
import io.camunda.connector.example.outbound.ExampleOutboundInput;
import org.junit.jupiter.api.Test;

public class OutboundConnectorFunctionTest {

  @Test
  public void shouldExecuteConnector() throws Exception {
    // given
    var fn = new ExampleOutboundFunction();

    // when
    var context =
        OutboundConnectorContextBuilder.create().variables(new ExampleOutboundInput("FOO")).build();
    var result = fn.execute(context);

    // then
    assertThat(result).isEqualTo("FOO");
  }

  @Test
  public void shouldReplaceSecret() throws Exception {
    // given
    var fn = new ExampleOutboundFunction();

    // when
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(new ExampleOutboundInput("secrets.FOO"))
            .secret("FOO", "SECRET_FOO")
            .build();

    var result = fn.execute(context);

    // then
    assertThat(result).isEqualTo("SECRET_FOO");
  }

  @Test
  public void shouldValidateInput() {
    // given
    var fn = new ExampleOutboundFunction();
    var context =
        OutboundConnectorContextBuilder.create()
            .variables(new ExampleOutboundInput("foo"))
            .validation(
                input -> {
                  throw new IllegalStateException("This will never validate: Test - foo");
                })
            .build();

    // when
    var exception = catchException(() -> fn.execute(context));

    // then
    assertThat(exception.getMessage()).contains("Test - foo");
  }

  @Test
  public void shouldFailOnMissingValidationProvider() {
    // given
    var fn = new ExampleOutboundFunction();

    // when
    var context =
        OutboundConnectorContextBuilder.create().variables(new ExampleOutboundInput("foo")).build();
    var exception = catchException(() -> fn.execute(context));

    // then
    assertThat(exception)
        .hasMessage(
            "Please bind an implementation to io.camunda.connector.api.validation.ValidationProvider via SPI");
  }
}
