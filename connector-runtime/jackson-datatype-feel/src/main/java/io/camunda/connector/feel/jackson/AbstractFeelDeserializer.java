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
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.util.function.Supplier;

public abstract class AbstractFeelDeserializer<T> extends StdDeserializer<T>
    implements ContextualDeserializer {

  protected final FeelExpressionEvaluator evaluator;
  protected final boolean relaxed;

  /**
   * A blank object mapper object for use in inheriting classes.
   *
   * <p>NOTE: This object mapper does not preserve the original deserialization context nor is it
   * aware of any registered modules beyond what's present in the default ObjectMapper. For example,
   * jackson-datatype-document will not be registered. It should not be used to deserialize the
   * final result. For final results, use the {@link DeserializationContext} object passed to {@link
   * #doDeserialize(JsonNode, JsonNode, DeserializationContext)} instead.
   */
  protected static final ObjectMapper BLANK_OBJECT_MAPPER =
      ConnectorsObjectMapperSupplier.getCopy();

  /**
   * Creates a new deserializer with the given FEEL expression evaluator.
   *
   * @param evaluator the FEEL expression evaluator to use
   * @param relaxed if true, the deserializer will be triggered for any string value, even if not a
   *     FEEL expression. if false, the deserializer will only be triggered for string values that
   *     start with '=' (indicating a FEEL expression).
   */
  protected AbstractFeelDeserializer(FeelExpressionEvaluator evaluator, boolean relaxed) {
    super(String.class);
    this.evaluator = evaluator;
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

  /**
   * Evaluates a FEEL expression and converts the result to the target type using the
   * deserialization context.
   *
   * @param ctx the deserialization context (used for type conversion to preserve registered
   *     modules)
   * @param expression the FEEL expression to evaluate
   * @param targetType the target type to convert the result to
   * @param variables the variables to use in evaluation
   * @return the evaluation result converted to the target type
   */
  @SuppressWarnings("unchecked")
  protected <R> R evaluateFeelExpression(
      final DeserializationContext ctx,
      final String expression,
      final JavaType targetType,
      final Object... variables) {
    // Evaluate the expression - get raw result
    Object result = evaluator.evaluate(expression, variables);

    // Convert result using the deserialization context to preserve registered modules
    try {
      if (result == null) {
        return null;
      }
      JsonNode jsonNode = BLANK_OBJECT_MAPPER.valueToTree(result);
      if (targetType.getRawClass() == String.class && jsonNode.isObject()) {
        return (R) BLANK_OBJECT_MAPPER.writeValueAsString(jsonNode);
      }
      return ctx.readTreeAsValue(jsonNode, targetType);
    } catch (IOException e) {
      throw new FeelEngineWrapperException(
          "Failed to convert FEEL evaluation result to the target type", expression, variables, e);
    }
  }

  protected abstract T doDeserialize(
      JsonNode node, JsonNode feelContext, DeserializationContext deserializationContext)
      throws IOException;
}
