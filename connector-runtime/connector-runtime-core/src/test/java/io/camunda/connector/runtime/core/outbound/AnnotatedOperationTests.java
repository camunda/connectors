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
package io.camunda.connector.runtime.core.outbound;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.operation.ConnectorOperations;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.*;
import org.junit.jupiter.api.Test;

public class AnnotatedOperationTests {

  ValidationProvider validationProvider = new DefaultValidationProvider();
  ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  AnnotatedOperationConnector connector = new AnnotatedOperationConnector();
  ConnectorOperations connectorOperations =
      ConnectorOperations.from(connector, objectMapper, validationProvider);
  OutboundConnectorOperationFunction invoker =
      new OutboundConnectorOperationFunction(connectorOperations);

  String json =
      """
                    {
                        "myStringParam": "World",
                        "myObjectParam": {"name": "Test", "value": 42},
                        "deeply": {"nested": {"object": {"name": "Nested", "value": 100}}}
                    }
                    """;

  @Test
  public void shouldInvokeAnnotatedOperation() throws Exception {
    var result = invoker.execute(createMockContext(json, "myOperation"));
    assert "Hello, World!".equals(result);
  }

  @Test
  public void shouldInvokeAnnotatedOperation2() throws Exception {
    var result = invoker.execute(createMockContext(json, "myOperation2"));
    assertNotNull(result);
  }

  @Test
  public void shouldThrowExceptionForMissingRequiredVar() {
    assertThrows(
        ConnectorInputException.class,
        () -> {
          invoker.execute(createMockContext("{}", "myOperation"));
        });
  }

  @Test
  public void shouldFailValidation() {
    assertThrows(
        ConnectorInputException.class,
        () -> {
          invoker.execute(createMockContext("{}", "myOperation3"));
        });
  }

  JobHandlerContext createMockContext(String json, String operation) {
    var activatedJob = mock(ActivatedJob.class);
    when(activatedJob.getCustomHeaders()).thenReturn(Map.of("operation", operation));
    when(activatedJob.getVariables()).thenReturn(json);
    return new JobHandlerContext(
        activatedJob,
        name -> "",
        validationProvider,
        null,
        ConnectorsObjectMapperSupplier.getCopy());
  }
}
