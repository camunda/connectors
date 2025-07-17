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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.core.outbound.operation.ParameterDescriptor.VariableDescriptor;
import java.io.IOException;

public sealed interface ParameterResolver<T> {

  record ResolverContext(OutboundConnectorContext context, JsonNode variables) {}

  sealed interface ContextAware<T> extends ParameterResolver<T> {
    T resolve(ResolverContext context);

    final class Variable<T> implements ContextAware<T> {
      private final ObjectMapper objectMapper;
      private final VariableDescriptor<T> descriptor;
      private final String jsonPointer;

      public Variable(ObjectMapper objectMapper, VariableDescriptor<T> descriptor) {
        this.objectMapper = objectMapper;
        this.descriptor = descriptor;
        if (descriptor.name().isEmpty()) {
          jsonPointer = "";
        } else {
          jsonPointer = "/" + descriptor.name().replace(".", "/");
        }
      }

      @Override
      public T resolve(ResolverContext context) {
        JsonNode node = context.variables().at(jsonPointer);
        try (JsonParser parser = node.traverse(objectMapper)) {
          var value = parser.readValueAs(descriptor.type());
          if (descriptor.required() && value == null) {
            throw new ConnectorInputException(
                "Required variable '" + descriptor.name() + "' is missing or null");
          }
          return value;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  final class Context implements ParameterResolver<OutboundConnectorContext> {}

  Context CONTEXT_RESOLVER = new Context();
}
