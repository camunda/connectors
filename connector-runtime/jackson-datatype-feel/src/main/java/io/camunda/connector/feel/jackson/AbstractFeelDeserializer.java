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
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.client.CamundaClient;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.io.IOException;
import java.util.function.Supplier;

public abstract class AbstractFeelDeserializer<T> extends StdDeserializer<T>
    implements ContextualDeserializer {

  protected FeelEngineWrapper feelEngineWrapper;
  protected final boolean relaxed;
  protected final Supplier<CamundaClient> camundaClientSupplier;

  /**
   * A blank object mapper object for use in inheriting classes.
   *
   * <p>NOTE: This object mapper does not preserve the original deserialization context nor is it
   * aware of any registered modules. It should not be used to deserialize the final result. For
   * final results, use the {@link DeserializationContext} object passed to {@link
   * #doDeserialize(JsonNode, JsonNode, DeserializationContext)} instead.
   */
  protected static final ObjectMapper BLANK_OBJECT_MAPPER = new ObjectMapper();

  /**
   * @param feelEngineWrapper the FEEL engine wrapper for local evaluation
   * @param relaxed if true, the deserializer will be triggered for any string value, even if not a
   *     FEEL expression. if false, the deserializer will only be triggered for string values that
   *     start with '=' (indicating a FEEL expression).
   * @param camundaClientSupplier supplier for CamundaClient for remote evaluation, or null to use
   *     local FeelEngineWrapper
   */
  protected AbstractFeelDeserializer(
      FeelEngineWrapper feelEngineWrapper,
      boolean relaxed,
      Supplier<CamundaClient> camundaClientSupplier) {
    super(String.class);
    this.feelEngineWrapper = feelEngineWrapper;
    this.relaxed = relaxed;
    this.camundaClientSupplier = camundaClientSupplier;
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

  protected Object evaluateFeelExpression(
      final DeserializationContext ctx,
      final String expression,
      final JavaType clazz,
      final JsonNode... variables) {

    if (camundaClientSupplier == null) {
      return feelEngineWrapper.evaluate(ctx, expression, clazz, (Object[]) variables);
    }

    var request = camundaClientSupplier.get().newEvaluateExpressionCommand().expression(expression);
    var mergedVariables = feelEngineWrapper.mergeMapVariables(variables);

    if (mergedVariables != null && !mergedVariables.isEmpty()) {
      request.variables(mergedVariables);
    }
    var response = request.send().join();
    // TODO: should we handle warnings? should they be logged or propagated in some way?
    try {
      // response type might be a problem even if it's compatible (e.g. int/long)
      // we use the blank object mapper to convert the result to a JsonNode and then use the
      // deserialization context to convert it to the expected type.
      // This way we can leverage Jackson's type coercion and custom deserializers if needed.
      // if custom modules are registered (like the JavaTimeModule for java.time types), they will
      // be used here.
      return ctx.readTreeAsValue(BLANK_OBJECT_MAPPER.valueToTree(response.getResult()), clazz);
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to deserialize FEEL expression result: " + e.getMessage(), e);
    }
  }

  protected abstract T doDeserialize(
      JsonNode node, JsonNode feelContext, DeserializationContext deserializationContext)
      throws IOException;
}
