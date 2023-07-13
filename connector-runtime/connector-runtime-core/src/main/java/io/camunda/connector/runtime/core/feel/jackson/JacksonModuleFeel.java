package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.util.function.Function;
import java.util.function.Supplier;

public class JacksonModuleFeel extends SimpleModule {

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  @Override
  public String getModuleName() {
    return "JacksonModuleFeel";
  }

  @Override
  public Version version() {
    // TODO: extract module into a separate artifact and get version dynamically from pom.xml
    return new Version(0, 1, 0, null, "io.camunda", "jackson-module-feel");
  }

  @Override
  public void setupModule(SetupContext context) {
    addDeserializer(
        Function.class, new FeelFunctionDeserializer<>(Object.class, feelEngineWrapper));
    addDeserializer(
        Supplier.class, new FeelSupplierDeserializer<>(Object.class, feelEngineWrapper));
    super.setupModule(context);
  }
}
