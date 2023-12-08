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
package io.camunda.connector.feel.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.io.IOException;
import java.util.function.Supplier;

public abstract class AbstractFeelDeserializer<T> extends StdDeserializer<T>
    implements ContextualDeserializer {

  protected FeelEngineWrapper feelEngineWrapper;
  protected boolean relaxed;

  protected AbstractFeelDeserializer(FeelEngineWrapper feelEngineWrapper, boolean relaxed) {
    super(String.class);
    this.feelEngineWrapper = feelEngineWrapper;
    this.relaxed = relaxed;
  }

  @Override
  public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    JsonNode node = parser.getCodec().readTree(parser);
    if (node == null || node.isNull()) {
      return null;
    }
    ObjectCodec codec = parser.getCodec();
    ObjectMapper mapper;
    if (!(codec instanceof ObjectMapper)) {
      DeserializationConfig config = context.getConfig();
      mapper = new ObjectMapper();
      mapper.setConfig(config);
    } else {
      mapper = (ObjectMapper) codec;
    }

    if (isFeelExpression(node.textValue()) || relaxed) {
      var feelContextSupplier =
          context.getAttribute(FeelContextAwareObjectReader.FEEL_CONTEXT_ATTRIBUTE);

      if (feelContextSupplier == null) {
        return doDeserialize(node, mapper, mapper.createObjectNode());
      }
      if (feelContextSupplier instanceof Supplier<?> supplier) {
        return doDeserialize(node, mapper, mapper.valueToTree(supplier.get()));
      }
      throw new IOException(
          "Attribute "
              + FeelContextAwareObjectReader.FEEL_CONTEXT_ATTRIBUTE
              + " must be a Supplier, but was: "
              + feelContextSupplier.getClass());
    }
    throw new IOException(
        "Invalid input: expected a FEEL expression (starting with '=') or a JSON object/array/etc. "
            + "Property name: "
            + parser.getParsingContext().getCurrentName());
  }

  protected boolean isFeelExpression(String value) {
    return value != null && value.startsWith("=");
  }

  protected abstract T doDeserialize(JsonNode node, ObjectMapper mapper, JsonNode feelContext)
      throws IOException;
}
