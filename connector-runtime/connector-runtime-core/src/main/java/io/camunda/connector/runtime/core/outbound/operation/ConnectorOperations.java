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
package io.camunda.connector.runtime.core.outbound.operation;

import static io.camunda.connector.api.reflection.ReflectionUtil.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.Header;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.reflection.ReflectionUtil.MethodWithAnnotation;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.operation.ParameterDescriptor.Context;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ConnectorOperations(Object connector, Map<String, OperationInvoker> operations) {

  public static ConnectorOperations from(
      Object connector, ObjectMapper objectMapper, ValidationProvider validationProvider) {
    Map<String, OperationInvoker> operations =
        getMethodsAnnotatedWith(connector.getClass(), Operation.class).stream()
            .map(MethodWithAnnotation::method)
            .map(method -> new OperationInvoker(objectMapper, validationProvider, map(method)))
            .collect(Collectors.toMap(invoker -> invoker.getDescriptor().id(), invoker -> invoker));
    if (operations.isEmpty()) {
      throw new IllegalArgumentException(
          "Connector class "
              + connector.getClass().getName()
              + " does not have any methods annotated with @Operation");
    }
    return new ConnectorOperations(connector, operations);
  }

  private static OperationDescriptor map(Method method) {
    Operation operationAnnotation = method.getAnnotation(Operation.class);
    String operationId = operationAnnotation.id();
    if (operationId == null || operationId.isEmpty()) {
      throw new IllegalArgumentException(
          "Operation method "
              + method.getName()
              + " must have a non-empty id specified in the @Operation annotation");
    }
    List<ParameterDescriptor> parameters =
        Arrays.stream(method.getParameters()).map(ConnectorOperations::map).toList();
    return new OperationDescriptor(operationId, method, parameters);
  }

  private static ParameterDescriptor map(Parameter parameter) {
    if (parameter.isAnnotationPresent(Variable.class)) {
      Variable variableAnnotation = parameter.getAnnotation(Variable.class);
      return new ParameterDescriptor.Variable<>(
          getVariableName(variableAnnotation), parameter.getType(), variableAnnotation.required());
    } else if (parameter.getType().equals(OutboundConnectorContext.class)) {
      return new Context();
    } else if (parameter.isAnnotationPresent(Header.class)) {
      Header headerAnnotation = parameter.getAnnotation(Header.class);
      return new ParameterDescriptor.Header<>(
          getHeaderName(headerAnnotation), parameter.getType(), headerAnnotation.required());
    } else {
      throw new IllegalArgumentException("Unsupported parameter type: " + parameter.getType());
    }
  }
}
