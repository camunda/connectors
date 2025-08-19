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
package io.camunda.connector.document.jackson.deserializer;

import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isDocumentReference;
import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isIntrinsicFunction;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.intrinsic.IntrinsicFunctionExecutor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class ObjectDeserializer extends AbstractDeserializer<Object> {

  private final DocumentDeserializer documentDeserializer;
  private final IntrinsicFunctionObjectResultDeserializer functionDeserializer;

  public ObjectDeserializer(
      DocumentFactory documentFactory,
      IntrinsicFunctionExecutor intrinsicFunctionExecutor,
      DocumentModuleSettings settings) {
    super(settings);
    this.documentDeserializer =
        new DocumentDeserializer(documentFactory, intrinsicFunctionExecutor, settings);
    this.functionDeserializer =
        new IntrinsicFunctionObjectResultDeserializer(intrinsicFunctionExecutor, settings);
  }

  @Override
  protected Object handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {
    if (isDocumentReference(node)) {
      // return Document object
      return documentDeserializer.handleJsonNode(node, context);
    }
    if (isIntrinsicFunction(node)) {
      // return the result of the function, type is irrelevant since the caller expects an Object
      // limit counter is decremented in the function deserializer
      return functionDeserializer.handleJsonNode(node, context);
    }
    // fallback deserialization
    return fallback(node, context);
  }

  /** Fallback deserialization when the object is neither a document reference nor an operation. */
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

    final var fallbackDeserializer = new UntypedObjectDeserializer(null, null);
    return fallbackDeserializer.deserialize(parser, ctx);
  }
}
