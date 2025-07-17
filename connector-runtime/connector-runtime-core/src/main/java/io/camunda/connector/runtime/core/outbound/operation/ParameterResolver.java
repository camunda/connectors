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

import com.fasterxml.jackson.core.JsonPointer;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.runtime.core.outbound.operation.ParameterDescriptor.VariableDescriptor;

public sealed interface ParameterResolver<T> {

  interface VariablesJson {
    <T> T readValueAs(JsonPointer pointer, Class<T> type);
  }

  record ResolverContext(OutboundConnectorContext context, VariablesJson variablesJson) {}

  sealed interface ContextAware<T> extends ParameterResolver<T> {
    T resolve(ResolverContext context);

    final class Variable<T> implements ContextAware<T> {
      private final VariableDescriptor<T> descriptor;
      private final JsonPointer jsonPointer;

      public Variable(VariableDescriptor<T> descriptor) {
        this.descriptor = descriptor;
        if (descriptor.name().isEmpty()) {
          jsonPointer = JsonPointer.empty();
        } else {
          jsonPointer = JsonPointer.compile("/" + descriptor.name().replace(".", "/"));
        }
      }

      @Override
      public T resolve(ResolverContext context) {
        var value = context.variablesJson.readValueAs(jsonPointer, descriptor.type());
        if (descriptor.required() && value == null) {
          throw new ConnectorInputException(
              "Required variable '" + descriptor.name() + "' is missing or null");
        }
        return value;
      }
    }
  }

  final class Context implements ParameterResolver<OutboundConnectorContext> {}

  Context CONTEXT_RESOLVER = new Context();
}
