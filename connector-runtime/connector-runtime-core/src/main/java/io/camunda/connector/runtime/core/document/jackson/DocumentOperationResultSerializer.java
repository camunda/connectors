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
package io.camunda.connector.runtime.core.document.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.connector.api.document.DocumentOperationResult;
import java.io.IOException;

public class DocumentOperationResultSerializer extends JsonSerializer<DocumentOperationResult<?>> {

  @Override
  public void serialize(
      DocumentOperationResult<?> value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    var result = value.get();
    switch (result) {
      case null -> gen.writeNull();
      case String s -> gen.writeString(s);
      case byte[] bytes -> gen.writeBinary(bytes);
      case Boolean b -> gen.writeBoolean(b);
      case Number number -> gen.writeNumber(number.toString());
      default -> gen.writeObject(result);
    }
  }
}
