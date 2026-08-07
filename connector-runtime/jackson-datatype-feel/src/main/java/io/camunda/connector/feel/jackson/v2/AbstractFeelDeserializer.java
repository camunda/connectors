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
package io.camunda.connector.feel.jackson.v2;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import java.io.IOException;
import java.util.function.Supplier;

/**
 * Jackson 2 counterpart of {@link io.camunda.connector.feel.jackson.AbstractFeelDeserializer}, used
 * only by the {@code camundaJsonMapper} Spring bean: that bean is wrapped in camunda-client-java's
 * {@code CamundaObjectMapper}, whose constructor only accepts a Jackson 2 {@code ObjectMapper} (no
 * Jackson 3 overload exists yet). Every other ObjectMapper in the codebase uses the Jackson 3
 * variant instead. {@code DeserializationContext} attribute keys are plain strings, so both
 * variants share the constants declared on {@link FeelContextAwareObjectReader} even though that
 * class itself is Jackson 3-typed.
 *
 * @param <T> the deserialized target type
 */
public abstract class AbstractFeelDeserializer<T> extends StdDeserializer<T>
    implements ContextualDeserializer {

  /**
   * A blank object mapper for use in inheriting classes. Deliberately a plain Jackson 2 mapper
   * rather than a shared supplier — {@code ConnectorsObjectMapperSupplier} is Jackson 3-only.
   */
  protected static final ObjectMapper BLANK_OBJECT_MAPPER =
      JsonMapper.builder()
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
          .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
          .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
          .enable(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS)
          .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
          .build();

  /** Evaluator configured for this deserializer instance. */
  protected final FeelExpressionEvaluator evaluator;

  /**
   * Controls both accepted input shape and evaluator override behavior. See the Jackson 3 variant's
   * javadoc for the full explanation.
   */
  protected final boolean relaxed;

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

  @SuppressWarnings("unchecked")
  protected <R> R evaluateFeelExpression(
      final DeserializationContext ctx,
      final String expression,
      final JavaType targetType,
      final Object... variables) {
    FeelExpressionEvaluator effectiveEvaluator = resolveEvaluator(ctx);
    Object result = effectiveEvaluator.evaluate(expression, variables);

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

  private FeelExpressionEvaluator resolveEvaluator(DeserializationContext ctx) {
    if (!relaxed) {
      return evaluator;
    }
    var override = ctx.getAttribute(FeelContextAwareObjectReader.FEEL_EVALUATOR_ATTRIBUTE);
    if (override == null) {
      return evaluator;
    }
    if (override instanceof FeelExpressionEvaluator feelEvaluator) {
      return feelEvaluator;
    }
    throw new IllegalArgumentException(
        "Attribute "
            + FeelContextAwareObjectReader.FEEL_EVALUATOR_ATTRIBUTE
            + " must be a FeelExpressionEvaluator, but was: "
            + override.getClass());
  }
}
