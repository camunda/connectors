package io.camunda.connector.sdk.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public interface ConnectorFunction {

  Gson DEFAULT_GSON = new GsonBuilder().create();

  Object service(ConnectorContext input);

  default Gson getGson() {
    return DEFAULT_GSON;
  }
}
