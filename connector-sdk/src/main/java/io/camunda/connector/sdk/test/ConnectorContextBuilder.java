package io.camunda.connector.sdk.test;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.camunda.connector.sdk.ConnectorContext;
import io.camunda.connector.sdk.ConnectorInputException;
import io.camunda.connector.sdk.SecretProvider;
import io.camunda.connector.sdk.SecretStore;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class ConnectorContextBuilder {

  private static final Gson GSON = new GsonBuilder().create();

  protected Map<String, String> secrets = new HashMap<>();
  protected SecretProvider secretProvider = (name) -> secrets.get(name);
  protected Reader variablesInputReader;
  protected String variablesInputJson;

  public static ConnectorContextBuilder create() {
    return new ConnectorContextBuilder();
  }

  public ConnectorContextBuilder variables(String variablesAsJson) {
    this.variablesInputJson = variablesAsJson;
    return this;
  }

  public ConnectorContextBuilder variables(Reader variablesReader) {
    this.variablesInputReader = variablesReader;
    return this;
  }

  public ConnectorContextBuilder secret(String name, String value) {
    secrets.put(name, value);
    return this;
  }

  public ConnectorContextBuilder secrets(SecretProvider secretProvider) {
    this.secretProvider = secretProvider;
    return this;
  }

  public ConnectorContext build() {
    return new ConnectorContext(){

      @Override
      public String getVariables() {
        try {
          if (variablesInputReader != null) {
            return CharStreams.toString(variablesInputReader);
          }
          return variablesInputJson;
        } catch (IOException exception) {
          throw new ConnectorInputException(exception);
        }
      }

      @Override
      public <T extends Object> T getVariablesAsType(Class<T> cls) {
        if (variablesInputReader != null) {
          return GSON.fromJson(variablesInputReader, cls);
        }
        return GSON.fromJson(variablesInputJson, cls);
      }

      @Override
      public SecretStore getSecretStore() {
        return new SecretStore(secretProvider);
      }
    };
  }
}
