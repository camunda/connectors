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

package io.camunda.connector.jdbc.model;

import static io.camunda.connector.jdbc.outbound.OutboundBaseTest.getContextBuilderWithSecrets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.jdbc.BaseTest;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class JdbcRequestTest extends BaseTest {
  private OutboundConnectorContext context;

  @ParameterizedTest
  @MethodSource("failRequestValidationTestCases")
  void shouldThrowExceptionWhenMissingOrBadRequestField(String input) {
    // Given request without one required field
    context =
        getContextBuilderWithSecrets()
            .validation(new DefaultValidationProvider())
            .variables(input)
            .build();
    // When context.validate(JdbcRequest);
    // Then expect exception that one required field not set
    ConnectorInputException thrown =
        assertThrows(
            ConnectorInputException.class,
            () -> context.bindVariables(JdbcRequest.class),
            "IllegalArgumentException was expected");
    assertThat(thrown.getMessage()).contains("Found constraints violated while validating input");
  }
}
