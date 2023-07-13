package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.util.function.Function;

public class FeelFunctionDeserializer<IN, OUT> extends AbstractFeelDeserializer<Function<IN, OUT>> {

  private final Class<OUT> outputType;

  public FeelFunctionDeserializer(Class<OUT> outputType, FeelEngineWrapper feelEngineWrapper) {
    super(feelEngineWrapper);
    this.outputType = outputType;
  }

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  @Override
  protected Function<IN, OUT> doDeserialize(String expression) {
    return (input) -> {
      Object result = feelEngineWrapper.evaluate(expression, input);
      return feelEngineWrapper.getObjectMapper().convertValue(result, outputType);
    };
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    if (property.getType().containedTypeCount() == 2) {
      var outputType = property.getType().containedType(1).getRawClass();
      return new FeelFunctionDeserializer<>(outputType, feelEngineWrapper);
    }
    return new FeelFunctionDeserializer<>(Object.class, feelEngineWrapper);
  }
}
