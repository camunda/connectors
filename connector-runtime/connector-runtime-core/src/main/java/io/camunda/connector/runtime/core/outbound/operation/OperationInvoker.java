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

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.JobHandlerContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

  /**
   * Resolves the {@link ObjectMapper} to use for this invocation: prefers the per-physical-tenant
   * mapper carried by a {@link JobHandlerContext} (the real runtime context, correctly wired per
   * tenant by {@code OutboundConnectorManager}) over the mapper captured in this instance at
   * connector-registration time, which is otherwise stale/wrong for any tenant other than the one
   * active when the operation-based connector's registration was built.
   */
  private ObjectMapper resolveObjectMapper(OutboundConnectorContext context) {
    return context instanceof JobHandlerContext jobHandlerContext
        ? jobHandlerContext.getObjectMapper()
        : objectMapper;
  }

  public Object invoke(Object connectorInstance, OutboundConnectorContext context) {
    ObjectMapper mapper = resolveObjectMapper(context);
    Object[] args = new Object[descriptor.params().size()];
    JsonNode jobVariables = null;
    for (int i = 0; i < args.length; i++) {
      ParameterDescriptor parameterDescriptor = descriptor.params().get(i);
      args[i] =
          switch (parameterDescriptor) {
            case ParameterDescriptor.Context ignored -> context;
            case ParameterDescriptor.Variable<?> variable -> {
              if (jobVariables == null) {
                jobVariables = readJsonAsTree(context.getJobContext().getVariables(), mapper);
              }
              yield resolveVariableValue(variable, jobVariables, mapper);
            }
            case ParameterDescriptor.Header<?> header ->
                resolveHeaderValue(header, context.getJobContext().getCustomHeaders(), mapper);
          };
    }
    return invokeMethod(connectorInstance, args);
  }

  private Object resolveVariableValue(
      ParameterDescriptor.Variable<?> variableDescriptor,
      JsonNode jobVariables,
      ObjectMapper mapper) {
    JsonPointer jsonPointer = variableDescriptor.getJsonPointer();
    Object value = readValueAs(jobVariables, jsonPointer, variableDescriptor.getType(), mapper);
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
      ParameterDescriptor.Header<?> headerDescriptor,
      Map<String, String> headers,
      ObjectMapper mapper) {
    String rawValue = headers.get(headerDescriptor.name());
    Object value;
    try {
      value = mapper.convertValue(rawValue, headerDescriptor.type());
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

  private Object readValueAs(
      JsonNode jsonNode, JsonPointer jsonPointer, Type type, ObjectMapper mapper) {
    JsonNode node = jsonNode.at(jsonPointer);

    JavaType javaType = mapper.getTypeFactory().constructType(type);

    try {
      return mapper.reader().forType(javaType).readValue(node);
    } catch (JacksonException ex) {
      throw new RuntimeException(ex);
    }
  }

  private JsonNode readJsonAsTree(String json, ObjectMapper mapper) {
    try {
      return mapper.readTree(json);
    } catch (JacksonException e) {
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
      return runtimeException;
    } else {
      return new RuntimeException(targetException);
    }
  }

  public OperationDescriptor getDescriptor() {
    return descriptor;
  }
}
