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
package io.camunda.connector.document.annotation.jackson.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.connector.document.annotation.jackson.DocumentOperationResult;
import java.io.IOException;

public class DocumentOperationResultSerializer extends JsonSerializer<DocumentOperationResult<?>> {

  @Override
  public void serialize(
      DocumentOperationResult<?> value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    var result = value.get();
    if (result == null) {
      gen.writeNull();
      return;
    }
    if (result instanceof byte[]) {
      gen.writeBinary((byte[]) result);
      return;
    }
    if (result instanceof String) {
      gen.writeString((String) result);
      return;
    }
    if (result instanceof Boolean) {
      gen.writeBoolean((Boolean) result);
      return;
    }
    if (result instanceof Number) {
      gen.writeNumber(result.toString());
      return;
    }
    gen.writeObject(result);
  }
}
