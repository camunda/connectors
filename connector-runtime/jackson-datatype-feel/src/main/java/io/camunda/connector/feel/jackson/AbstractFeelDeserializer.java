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
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.io.IOException;
import java.util.function.Supplier;

public abstract class AbstractFeelDeserializer<T> extends StdDeserializer<T>
    implements ContextualDeserializer {

  protected FeelEngineWrapper feelEngineWrapper;
  protected boolean relaxed;

  /**
   * A blank object mapper object for use in inheriting classes.
   *
   * <p>NOTE: This object mapper does not preserve the original deserialization context nor is it
   * aware of any registered modules. It should not be used to deserialize the final result. For
   * final results, use the {@link DeserializationContext} object passed to {@link
   * #doDeserialize(JsonNode, JsonNode, DeserializationContext)} instead.
   *
   * <p>{@link SerializationFeature#FAIL_ON_EMPTY_BEANS} is disabled and {@link
   * JacksonModuleDocumentSerializer} is registered so that FEEL contexts containing a {@code
   * CamundaDocument} can be serialized into a tree without throwing — and so the document
   * reference is preserved instead of being silently emptied. See issue #6946.
   */
  protected static final ObjectMapper BLANK_OBJECT_MAPPER =
      new ObjectMapper()
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .registerModule(new JacksonModuleDocumentSerializer());

  protected AbstractFeelDeserializer(FeelEngineWrapper feelEngineWrapper, boolean relaxed) {
    super(String.class);
    this.feelEngineWrapper = feelEngineWrapper;
    this.relaxed = relaxed;
  }

  @Override
  public T deserialize(JsonParser parser, DeserializationContext context) throws IOException {
    JsonNode node = parser.readValueAsTree();
    if (node == null || node.isNull()) {
      return null;
    }

    if (isFeelExpression(node.textValue()) || relaxed) {
      var feelContextSupplier =
          context.getAttribute(FeelContextAwareObjectReader.FEEL_CONTEXT_ATTRIBUTE);

      if (feelContextSupplier == null) {
        return doDeserialize(node, BLANK_OBJECT_MAPPER.createObjectNode(), context);
      }
      if (feelContextSupplier instanceof Supplier<?> supplier) {
        return doDeserialize(node, BLANK_OBJECT_MAPPER.valueToTree(supplier.get()), context);
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

  protected abstract T doDeserialize(
      JsonNode node, JsonNode feelContext, DeserializationContext deserializationContext)
      throws IOException;
}
