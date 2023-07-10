package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import java.io.IOException;
import java.util.function.Function;

public class ContextualFeelFunctionDeserializer extends JsonDeserializer<Function<?, ?>> implements
    ContextualDeserializer {
  private final ObjectMapper functionResultMapper;

  public ContextualFeelFunctionDeserializer(ObjectMapper functionResultMapper) {
    this.functionResultMapper = functionResultMapper;
  }

  @Override
  public Function<?, ?> deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    throw new IOException("Sanity check: raw deserialization not implemented");
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    var inputType = property.getType().containedType(0).getRawClass();
    var outputType = property.getType().containedType(1).getRawClass();
    return new TypeAwareFeelFunctionDeserializer<>(inputType, outputType, functionResultMapper);
  }
}
