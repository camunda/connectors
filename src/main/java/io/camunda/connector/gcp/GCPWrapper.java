package io.camunda.connector.gcp;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import io.camunda.connector.common.*;
import io.camunda.connector.http.Authentication;
import io.camunda.connector.http.BasicAuthentication;
import io.camunda.connector.http.BearerAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class GCPWrapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(GCPWrapper.class);

  private static final Gson GSON =
      new GsonBuilder()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(Authentication.class)
                  .registerSubtype(BasicAuthentication.class, "basic")
                  .registerSubtype(BearerAuthentication.class, "bearer"))
          .create();

  private final ConnectorFunction call;

  public GCPWrapper(ConnectorFunction call) {
    this.call = call;
  }

  public void service(final HttpRequest httpRequest, final HttpResponse httpResponse)
      throws Exception {

    final ConnectorBridgeResponse response = new ConnectorBridgeResponse();

    Optional<String> clusterId = httpRequest.getFirstHeader("X-Camunda-Cluster-ID");

    if (clusterId.isEmpty()) {
      httpResponse.setStatusCode(400);

      return;
    }

    LOGGER.info("Received request from cluster {}", clusterId.get());

    try {
      Object result = call.service(new GCPInput(httpRequest, httpResponse));

      response.setResult(result);
      httpResponse.setStatusCode(200);
    } catch (Exception error) {

      httpResponse.setStatusCode(500);
      response.setError(error.getMessage());
    }

    httpResponse.setContentType("application/json");
    GSON.toJson(response, httpResponse.getWriter());
  }

  class GCPInput implements ConnectorContext {

    HttpRequest request;
    HttpResponse response;

    public GCPInput(HttpRequest request, HttpResponse response) {
      this.request = request;
      this.response = response;
    }

    public <T extends Object> T getVariableAsType(Class<T> cls) {

      try {
        return GSON.fromJson(request.getReader(), cls);
      } catch (IOException exception) {
        throw new ConnectorInputException(exception);
      }
    }

    @Override
    public SecretStore getSecretStore() {
      return new SecretStoreImpl() {
        @Override
        public String getEnvSecret(String name) {
          return null;
        }

        @Override
        public String getRemoteSecret(String name) {
          return null;
        }

        @Override
        public String replaceSecret(String value) {
          return null;
        }
      };
    }
  }
}
