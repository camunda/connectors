package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.util.Map;
import java.util.function.Supplier;

public class FeelSupplierDeserializer<OUT> extends AbstractFeelDeserializer<Supplier<OUT>> {

  Class<OUT> outputType;

  protected FeelSupplierDeserializer(Class<OUT> outputType, FeelEngineWrapper feelEngineWrapper) {
    super(feelEngineWrapper);
    this.outputType = outputType;
  }

  @Override
  Supplier<OUT> doDeserialize(String expression) {
    return () -> {
      Object result = feelEngineWrapper.evaluate(expression, Map.of());
      return feelEngineWrapper.getObjectMapper().convertValue(result, outputType);
    };
  }

  @Override
  public FeelSupplierDeserializer<?> createContextual(DeserializationContext ctxt,
      BeanProperty property) {

    if (property.getType().containedTypeCount() == 1) {
      var outputType = property.getType().containedType(0).getRawClass();
      return new FeelSupplierDeserializer<>(outputType, feelEngineWrapper);
    }
    return new FeelSupplierDeserializer<>(Object.class, feelEngineWrapper);
  }
}
