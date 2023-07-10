package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.io.IOException;
import java.util.function.Function;

public class TypeAwareFeelFunctionDeserializer<IN, OUT>
    extends StdDeserializer<Function<IN, OUT>> {

  private final Class<IN> inputType;
  private final Class<OUT> outputType;
  private final ObjectMapper functionResultMapper;

  public TypeAwareFeelFunctionDeserializer(Class<IN> inputType, Class<OUT> outputType,
      ObjectMapper functionResultMapper) {
    super(String.class);
    this.inputType = inputType;
    this.outputType = outputType;
    this.functionResultMapper = functionResultMapper;
  }

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  @Override
  public Function<IN, OUT> deserialize(
      JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {

    JsonNode node = jsonParser.getCodec().readTree(jsonParser);

    if (node != null && node.isTextual()) {
      String value = node.textValue();
      if (isFeelExpression(value)) {
        return createFeelExpressionLambda(value);
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

  private Function<IN, OUT> createFeelExpressionLambda(String expression) {
    return (input) -> {
      Object result = feelEngineWrapper.evaluate(expression, input);
      return functionResultMapper.convertValue(result, outputType);
    };
  }
}
