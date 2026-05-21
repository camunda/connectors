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

/**
 * Base Jackson deserializer for connector FEEL-backed values.
 *
 * <p>Subclasses decide whether values are evaluated immediately during property binding or turned
 * into deferred runtime callbacks such as {@link java.util.function.Function} and {@link Supplier}.
 *
 * @param <T> the deserialized target type
 */
public abstract class AbstractFeelDeserializer<T> extends StdDeserializer<T>
    implements ContextualDeserializer {

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

  /** Evaluator configured for this deserializer instance. */
  protected final FeelExpressionEvaluator evaluator;

  /**
   * Controls both accepted input shape and evaluator override behavior.
   *
   * <p>Strict mode ({@code false}) is used for deferred runtime callbacks such as {@link
   * java.util.function.Function} and {@link Supplier}. These callbacks are deserialized from
   * explicit FEEL expressions and evaluated later against in-memory runtime data, which may include
   * objects such as Documents. They must keep the evaluator configured by their Jackson module,
   * usually local FEEL, because a remote evaluator cannot serialize or interpret those runtime
   * objects reliably.
   *
   * <p>Relaxed mode ({@code true}) is used for regular connector properties, for example fields
   * annotated with {@code @FEEL}. In this mode, the deserializer also accepts plain strings and
   * JSON-like values, and a per-reader evaluator override may be applied.
   */
  protected final boolean relaxed;

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

  /**
   * Checks whether the given value is an explicit FEEL expression.
   *
   * @param value the textual value to inspect
   * @return {@code true} if the value starts with {@code =}
   */
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
   * @param <R> the converted FEEL expression result type
   * @return the evaluation result converted to the target type
   */
  @SuppressWarnings("unchecked")
  protected <R> R evaluateFeelExpression(
      final DeserializationContext ctx,
      final String expression,
      final JavaType targetType,
      final Object... variables) {
    // Evaluate the expression - get raw result, allowing a per-call evaluator override via
    // FEEL_EVALUATOR_ATTRIBUTE on the DeserializationContext.
    FeelExpressionEvaluator effectiveEvaluator = resolveEvaluator(ctx);
    Object result = effectiveEvaluator.evaluate(expression, variables);

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

  /**
   * Performs subclass-specific deserialization after the FEEL context has been resolved.
   *
   * @param node the source JSON node
   * @param feelContext the FEEL context available during evaluation
   * @param deserializationContext the Jackson deserialization context
   * @return the deserialized value
   * @throws IOException when deserialization fails
   */
  protected abstract T doDeserialize(
      JsonNode node, JsonNode feelContext, DeserializationContext deserializationContext)
      throws IOException;

  private FeelExpressionEvaluator resolveEvaluator(DeserializationContext ctx) {
    // Strict deserializers are used for deferred runtime callbacks like Function/Supplier.
    // They must keep their module-configured evaluator, usually local FEEL, because their
    // inputs can contain in-memory runtime objects that a remote evaluator cannot serialize
    // or interpret correctly.
    if (!relaxed) {
      return evaluator;
    }
    var override = ctx.getAttribute(FeelContextAwareObjectReader.FEEL_EVALUATOR_ATTRIBUTE);
    if (override instanceof FeelExpressionEvaluator feelEvaluator) {
      return feelEvaluator;
    }
    return evaluator;
  }
}
