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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.ReflectionUtil;
import io.camunda.connector.runtime.core.ReflectionUtil.MethodWithAnnotation;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorOperationFunction implements OutboundConnectorFunction {

  private static final Logger log =
      LoggerFactory.getLogger(OutboundConnectorOperationFunction.class);
  private final Object connector;
  private final List<MethodWithAnnotation<Operation>> methods;
  private ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.getCopy();
  private ValidationProvider validationProvider;

  public OutboundConnectorOperationFunction(
      Object connector, ValidationProvider validationProvider) {
    this.connector = connector;
    this.validationProvider = validationProvider;
    this.methods = ReflectionUtil.getMethodsAnnotatedWith(connector.getClass(), Operation.class);
  }

  @Override
  public Object execute(OutboundConnectorContext context) throws Exception {

    String operationId = context.getJobContext().getCustomHeaders().get("operation");
    Optional<MethodWithAnnotation<Operation>> methodCandidate =
        methods.stream().filter(m -> m.annotation().id().equals(operationId)).findFirst();
    if (methodCandidate.isPresent()) {
      MethodWithAnnotation<Operation> method = methodCandidate.get();
      Method m = method.method();
      Object[] args = new Object[method.parameters().size()];
      JsonNode jobVariables = null;
      for (int i = 0; i < method.parameters().size(); i++) {
        Parameter parameter = method.parameters().get(i);
        var varAnnotation = parameter.getAnnotation(Variable.class);
        if (varAnnotation != null) {
          var variableName = varAnnotation.value();
          if (jobVariables == null) {
            jobVariables = readJsonAsTree(context.getJobContext().getVariables());
          }
          Object value;
          if (variableName != null) {
            if (variableName.isEmpty()) {
              value = readVariable(jobVariables, "", parameter.getType());
            } else {
              value =
                  readVariable(
                      jobVariables, "/" + variableName.replace(".", "/"), parameter.getType());
            }
          } else {
            throw new RuntimeException(
                "Missing required variable name for operation: "
                    + operationId
                    + " and parameter: "
                    + parameter.getName());
          }

          if (value != null) {
            validationProvider.validate(value);
          }
          if (varAnnotation.required() && value == null) {
            throw new ConnectorInputException(
                "Required variable '" + variableName + "' is missing or null");
          }
          args[i] = value;
        } else if (parameter.getType().equals(OutboundConnectorContext.class)) {
          args[i] = context;
        } else {
          throw new RuntimeException("Unsupported parameter type: " + parameter.getType());
        }
      }

      try {
        return m.invoke(connector, args);
      } catch (Exception e) {
        log.debug("Failed to invoke operation: {}", operationId, e);
        throw new RuntimeException("Failed to invoke operation: " + operationId, e);
      }
    } else {
      throw new RuntimeException("Operation not found: " + operationId);
    }
  }

  private JsonNode readJsonAsTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  private Object readVariable(JsonNode jobVariables, String jsonPtr, Class<?> clazz) {
    JsonNode node = jobVariables.at(jsonPtr);
    try (JsonParser parser = node.traverse(objectMapper)) {
      return parser.readValueAs(clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public List<MethodWithAnnotation<Operation>> getMethods() {
    return methods;
  }
}
