package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.function.Function;

public class JacksonModuleFeel extends SimpleModule {

  @Override
  public String getModuleName() {
    return "JacksonModuleFeel";
  }

  @Override
  public Version version() {
    return new Version(1, 0, 0, null, null, null);
  }

  @Override
  public void setupModule(SetupContext context) {
    addDeserializer(Function.class, new ContextualFeelFunctionDeserializer(new ObjectMapper()));
    super.setupModule(context);
  }
}
