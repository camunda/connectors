package io.camunda.connector.http.components;

import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import io.camunda.connector.http.auth.Authentication;
import io.camunda.connector.http.auth.BasicAuthentication;
import io.camunda.connector.http.auth.BearerAuthentication;

public class GsonComponentSupplier {

  private GsonComponentSupplier() {}

  private static final GsonFactory GSON_FACTORY = new GsonFactory();

  private static final Gson GSON =
      new GsonBuilder()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(Authentication.class, "type")
                  .registerSubtype(BasicAuthentication.class, "basic")
                  .registerSubtype(BearerAuthentication.class, "bearer"))
          .create();

  public static Gson gsonInstance() {
    return GSON;
  }

  public static GsonFactory gsonFactoryInstance() {
    return GSON_FACTORY;
  }
}
