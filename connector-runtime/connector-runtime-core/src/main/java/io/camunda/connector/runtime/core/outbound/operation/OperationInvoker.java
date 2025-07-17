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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.outbound.operation.ParameterDescriptor.OutboundConnectorContextDescriptor;
import io.camunda.connector.runtime.core.outbound.operation.ParameterDescriptor.VariableDescriptor;
import io.camunda.connector.runtime.core.outbound.operation.ParameterResolver.Context;
import io.camunda.connector.runtime.core.outbound.operation.ParameterResolver.ContextAware.Variable;
import io.camunda.connector.runtime.core.outbound.operation.ParameterResolver.ResolverContext;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperationInvoker {
  private static final Logger log = LoggerFactory.getLogger(OperationInvoker.class);
  private final ObjectMapper objectMapper;
  private final ValidationProvider validationProvider;
  private final OperationDescriptor descriptor;
  private final List<ParameterResolver<?>> parameterResolvers;

  public OperationInvoker(
      ObjectMapper objectMapper,
      ValidationProvider validationProvider,
      OperationDescriptor descriptor) {
    this.objectMapper = objectMapper;
    this.validationProvider = validationProvider;
    this.descriptor = descriptor;
    this.parameterResolvers =
        descriptor.params().stream()
            .map(
                param -> {
                  switch (param) {
                    case OutboundConnectorContextDescriptor ignored -> {
                      return ParameterResolver.CONTEXT_RESOLVER;
                    }
                    case VariableDescriptor<?> variableDescriptor -> {
                      return new Variable<>(objectMapper, variableDescriptor);
                    }
                  }
                })
            .toList();
  }

  public Object invoke(Object connectorInstance, OutboundConnectorContext context) {
    Object[] args = new Object[parameterResolvers.size()];
    JsonNode jobVariables = null;
    ResolverContext resolverContext = null;
    for (int i = 0; i < args.length; i++) {
      ParameterResolver<?> resolver = parameterResolvers.get(i);
      switch (resolver) {
        case Context ignored -> args[i] = context;
        case Variable<?> variableResolver -> {
          if (jobVariables == null) {
            jobVariables = readJsonAsTree(context.getJobContext().getVariables());
          }
          if (resolverContext == null) {
            resolverContext = new ResolverContext(context, jobVariables);
          }
          Object value = variableResolver.resolve(resolverContext);
          if (value != null) {
            validationProvider.validate(value);
          }
          args[i] = value;
        }
      }
    }
    try {
      return descriptor.method().invoke(connectorInstance, args);
    } catch (Exception e) {
      log.debug("Failed to invoke operation: {}", descriptor.id(), e);
      throw new RuntimeException(e);
    }
  }

  private JsonNode readJsonAsTree(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

  public OperationDescriptor getDescriptor() {
    return descriptor;
  }
}
