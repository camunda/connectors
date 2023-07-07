package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.io.IOException;
import java.util.function.Function;

public class FeelFunctionDeserializer extends JsonDeserializer<Function<?, ?>>
    implements ContextualDeserializer {

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  @Override
  public Function<?, ?> deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {

    JsonNode node = jsonParser.getCodec().readTree(jsonParser);

    if (node != null && node.isTextual()) {
      String value = node.textValue();
      if (isFeelExpression(value)) {
        return createFeelExpressionLambda(value, deserializationContext.getContextualType());
      }
    }
    throw new IOException(
        "Invalid deserialization for Function type: expected a FEEL expression, but got '" +
            (node != null ? node.toString() : "null")
            + "' instead.");
  }

  private boolean isFeelExpression(String value) {
    return value.startsWith("=");
  }

  private <T> Function<?, T> createFeelExpressionLambda(String expression, JavaType getTargetType) throws IOException {
    try {
      // fail-fast if the expression is not valid

    } catch (Exception e) {
      throw new IOException("Invalid FEEL expression: " + expression, e);
    }

    return (input) -> feelEngineWrapper.evaluate(expression, input);
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property)
      throws JsonMappingException {
    return this;
  }
}
