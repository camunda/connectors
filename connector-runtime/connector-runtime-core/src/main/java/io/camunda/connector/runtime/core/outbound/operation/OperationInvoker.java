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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.validation.ValidationProvider;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperationInvoker {
  private static final Logger log = LoggerFactory.getLogger(OperationInvoker.class);
  private final ObjectMapper objectMapper;
  private final ValidationProvider validationProvider;
  private final OperationDescriptor descriptor;

  public OperationInvoker(
      ObjectMapper objectMapper,
      ValidationProvider validationProvider,
      OperationDescriptor descriptor) {
    this.objectMapper = objectMapper;
    this.validationProvider = validationProvider;
    this.descriptor = descriptor;
  }

  public Object invoke(Object connectorInstance, OutboundConnectorContext context) {
    Object[] args = new Object[descriptor.params().size()];
    JsonNode jobVariables = null;
    for (int i = 0; i < args.length; i++) {
      ParameterDescriptor parameterDescriptor = descriptor.params().get(i);
      args[i] =
          switch (parameterDescriptor) {
            case ParameterDescriptor.Context ignored -> context;
            case ParameterDescriptor.Variable<?> variable -> {
              if (jobVariables == null) {
                jobVariables = readJsonAsTree(context.getJobContext().getVariables());
              }
              yield resolveVariableValue(variable, jobVariables);
            }
            case ParameterDescriptor.Header<?> header ->
                resolveHeaderValue(header, context.getJobContext().getCustomHeaders());
          };
    }
    return invokeMethod(connectorInstance, args);
  }

  private Object resolveVariableValue(
      ParameterDescriptor.Variable<?> variableDescriptor, JsonNode jobVariables) {
    JsonPointer jsonPointer = variableDescriptor.getJsonPointer();
    Object value = readValueAs(jobVariables, jsonPointer, variableDescriptor.getType());
    if (variableDescriptor.isRequired() && value == null) {
      throw new ConnectorInputException(
          "Required variable '"
              + variableDescriptor.getName()
              + "' is missing in the job variables.");
    }
    if (value != null) {
      validationProvider.validate(value);
    }
    return value;
  }

  private Object resolveHeaderValue(
      ParameterDescriptor.Header<?> headerDescriptor, Map<String, String> headers) {
    String rawValue = headers.get(headerDescriptor.name());
    Object value;
    try {
      value = objectMapper.convertValue(rawValue, headerDescriptor.type());
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
    if (headerDescriptor.required() && value == null) {
      throw new ConnectorInputException(
          "Required header '" + headerDescriptor.name() + "' is missing in the job headers.");
    }
    if (value != null) {
      validationProvider.validate(value);
    }
    return value;
  }

  private Object readValueAs(JsonNode jsonNode, JsonPointer jsonPointer, Class<?> type) {
    JsonNode node = jsonNode.at(jsonPointer);
    try (JsonParser parser = node.traverse(objectMapper)) {
      return parser.readValueAs(type);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  private JsonNode readJsonAsTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object invokeMethod(Object connectorInstance, Object[] args) {
    try {
      return descriptor.method().invoke(connectorInstance, args);
    } catch (Exception e) {
      log.debug("Failed to invoke operation: {}", descriptor.id(), e);
      if (e instanceof InvocationTargetException invocationTargetException) {
        throw runtimeExceptionFrom(invocationTargetException);
      } else {
        throw new RuntimeException(e);
      }
    }
  }

  private static RuntimeException runtimeExceptionFrom(InvocationTargetException e) {
    Throwable targetException = e.getTargetException();
    if (targetException instanceof RuntimeException runtimeException) {
      throw runtimeException;
    } else {
      throw new RuntimeException(targetException);
    }
  }

  public OperationDescriptor getDescriptor() {
    return descriptor;
  }
}
