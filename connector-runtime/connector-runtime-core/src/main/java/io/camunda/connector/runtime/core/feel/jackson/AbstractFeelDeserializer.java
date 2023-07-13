package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.io.IOException;

public abstract class AbstractFeelDeserializer<T> extends StdDeserializer<T>
    implements ContextualDeserializer {
  protected FeelEngineWrapper feelEngineWrapper;

  protected AbstractFeelDeserializer(FeelEngineWrapper feelEngineWrapper) {
    super(String.class);
    this.feelEngineWrapper = feelEngineWrapper;
  }

  @Override
  public T deserialize(JsonParser parser, DeserializationContext context)
      throws IOException {
    JsonNode node = parser.getCodec().readTree(parser);

    if (node != null && node.isTextual()) {
      String value = node.textValue();
      if (isFeelExpression(value)) {
        return doDeserialize(value);
      }
    }
    throw new IOException(
        "Invalid input: expected a FEEL expression, but got '" + node + "' instead.");
  }

  private boolean isFeelExpression(String value) {
    return value.startsWith("=");
  }

  abstract T doDeserialize(String expression);
}
