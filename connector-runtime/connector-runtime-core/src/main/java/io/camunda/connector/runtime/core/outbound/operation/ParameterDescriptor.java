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

public sealed interface ParameterDescriptor {

  record Context() implements ParameterDescriptor {}

  final class Variable<T> implements ParameterDescriptor {

    private final JsonPointer jsonPointer;
    private final String name;
    private final Class<T> type;
    private final boolean required;

    public Variable(String name, Class<T> type, boolean required) {
      this.name = name;
      this.type = type;
      this.required = required;
      if (name.isEmpty()) {
        jsonPointer = JsonPointer.empty();
      } else {
        jsonPointer = JsonPointer.compile("/" + name.replace(".", "/"));
      }
    }

    public JsonPointer getJsonPointer() {
      return jsonPointer;
    }

    public String getName() {
      return name;
    }

    public Class<T> getType() {
      return type;
    }

    public boolean isRequired() {
      return required;
    }
  }
}
