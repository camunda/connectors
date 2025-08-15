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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.ConnectorConfigurationUtil;
import io.camunda.connector.runtime.core.NoOpSecretProvider;
import io.camunda.connector.runtime.core.TestObjectMapperSupplier;
import io.camunda.connector.runtime.core.outbound.operation.ConnectorOperations;
import io.camunda.connector.runtime.core.outbound.operation.OutboundConnectorOperationFunction;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class AnnotatedOperationTests {

  ValidationProvider validationProvider = new DefaultValidationProvider();
  ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;
  AnnotatedOperationConnector connector = new AnnotatedOperationConnector();
  ConnectorOperations connectorOperations =
      ConnectorOperations.from(connector, objectMapper, validationProvider);
  OutboundConnectorOperationFunction invoker =
      new OutboundConnectorOperationFunction(connectorOperations);

  String json =
      """
        {
          "myStringParam": "World",
          "myObjectParam": {"name": "Test", "value": 42}
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
        () -> invoker.execute(createMockContext("{}", "myOperation3")));
  }

  @Test
  public void testHeaderVariableResolution() {
    var result =
        invoker.execute(createMockContext(json, "myOperation4", Map.of("myHeader", "myValue")));
    assert "myValue".equals(result);
  }

  @Test
  public void testHeaderVariableResolutionWithComplexType() {
    var result =
        invoker.execute(
            createMockContext("{\"x\": 10}", "myOperation5", Map.of("myFeelFunction", "=x+2")));
    assertEquals(12L, result);
  }

  @Test
  public void shouldThrowExceptionForMissingRequiredHeader() {
    assertThrows(
        ConnectorInputException.class,
        () -> {
          invoker.execute(createMockContext("{}", "myOperation4"));
        });
  }

  @Test
  public void testJobActivationVariable() {
    var variables =
        Arrays.stream(
                ConnectorConfigurationUtil.getInputVariables(
                    AnnotatedOperationConnector.class,
                    AnnotatedOperationConnector.class.getAnnotation(OutboundConnector.class)))
            .toList();
    Assertions.assertThatCollection(variables)
        .containsExactlyInAnyOrder(
            "myStringParam", "myObjectParam", "nullObjectParam", "name", "value", "validatingName");
  }

  JobHandlerContext createMockContext(String json, String operation) {
    return this.createMockContext(json, operation, null);
  }

  JobHandlerContext createMockContext(String json, String operation, Map<String, String> headers) {
    var activatedJob = mock(ActivatedJob.class);
    var operationHeader = Map.of("operation", operation);
    Map<String, String> customHeaders;
    if (headers == null) {
      customHeaders = operationHeader;
    } else {
      customHeaders = new HashMap<>(headers);
      customHeaders.putAll(operationHeader);
    }
    when(activatedJob.getCustomHeaders()).thenReturn(customHeaders);
    when(activatedJob.getVariables()).thenReturn(json);
    return new JobHandlerContext(
        activatedJob, new NoOpSecretProvider(), validationProvider, null, objectMapper);
  }
}
