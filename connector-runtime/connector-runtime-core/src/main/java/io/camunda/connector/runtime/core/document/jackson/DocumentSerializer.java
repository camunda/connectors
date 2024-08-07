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
import io.camunda.connector.api.document.Document;
import io.camunda.connector.runtime.core.document.DocumentOperationExecutor;
import java.io.IOException;

public class DocumentSerializer extends JsonSerializer<Document> {

  private final DocumentOperationExecutor operationExecutor;

  public DocumentSerializer(DocumentOperationExecutor operationExecutor) {
    this.operationExecutor = operationExecutor;
  }

  @Override
  public void serialize(
      Document document, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {

    var reference = document.reference();
    if (reference.operation().isPresent()) {
      var result = operationExecutor.execute(reference.operation().get(), document);
      jsonGenerator.writeStartObject();
      switch (result) {
        case String str -> jsonGenerator.writeString(str);
        case Number number -> jsonGenerator.writeNumber(number.toString());
        case Boolean bool -> jsonGenerator.writeBoolean(bool);
        case byte[] bytes -> jsonGenerator.writeBinary(bytes);
        default -> jsonGenerator.writeObject(result);
      }
      jsonGenerator.writeObject(result);
    } else {
      jsonGenerator.writeObject(document);
    }
  }
}
