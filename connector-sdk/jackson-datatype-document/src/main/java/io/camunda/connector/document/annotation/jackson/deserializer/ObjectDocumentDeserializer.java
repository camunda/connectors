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
package io.camunda.connector.document.annotation.jackson.deserializer;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel;
import io.camunda.document.DocumentFactory;
import io.camunda.document.operation.DocumentOperationExecutor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class ObjectDocumentDeserializer extends DocumentDeserializerBase<Object> {

  private final UntypedObjectDeserializer fallbackDeserializer =
      new UntypedObjectDeserializer(null, null);

  public ObjectDocumentDeserializer(
      DocumentOperationExecutor operationExecutor, DocumentFactory documentFactory, boolean lazy) {
    super(operationExecutor, documentFactory);
    this.lazy = lazy;
  }

  private final boolean lazy;

  @Override
  public Object deserializeDocumentReference(
      DocumentReferenceModel reference, DeserializationContext ctx) throws IOException {

    if (reference.operation().isPresent()) {
      var operationResultSupplier = deserializeOperation(reference, reference.operation().get());
      if (lazy) {
        return operationResultSupplier;
      }
      // TODO: check output type
      return operationResultSupplier.get();
    }
    // if no operation, return the document
    return createDocument(reference);
  }

  @Override
  public Object fallback(JsonNode node, DeserializationContext ctx) throws IOException {
    if (node.isObject()) {
      var fields = node.fields();
      var map = new LinkedHashMap<String, Object>();
      while (fields.hasNext()) {
        var field = fields.next();
        var parser = field.getValue().traverse();
        parser.setCodec(ctx.getParser().getCodec());
        // invoke the deserializer for the field
        map.put(field.getKey(), ctx.readValue(parser, Object.class));
      }
      return map;
    }

    if (node.isArray()) {
      var list = new ArrayList<>();
      for (int i = 0; i < node.size(); i++) {
        var parser = node.get(i).traverse();
        parser.setCodec(ctx.getParser().getCodec());
        // invoke the deserializer for the element
        list.add(ctx.readValue(parser, Object.class));
      }
      return list;
    }

    var parser = node.traverse(ctx.getParser().getCodec());
    parser.nextToken();
    return fallbackDeserializer.deserialize(parser, ctx);
  }
}
