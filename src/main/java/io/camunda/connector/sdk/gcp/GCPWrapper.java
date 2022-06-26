package io.camunda.connector.sdk.gcp;

import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.gson.Gson;
import io.camunda.connector.sdk.common.ConnectorContext;
import io.camunda.connector.sdk.common.ConnectorFunction;
import io.camunda.connector.sdk.common.ConnectorInputException;
import io.camunda.connector.sdk.common.SecretStore;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GCPWrapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(GCPWrapper.class);

  private static final String REQUEST_HEADER_CLUSTER_ID = "X-Camunda-Cluster-ID";

  private final ConnectorFunction connectorFunction;

  public GCPWrapper(ConnectorFunction connectorFunction) {
    this.connectorFunction = connectorFunction;
  }

  public void service(final HttpRequest httpRequest, final HttpResponse httpResponse)
      throws Exception {

    final ConnectorBridgeResponse response = new ConnectorBridgeResponse();

    Optional<String> clusterId = getClusterId(httpRequest);

    if (clusterId.isEmpty()) {
      httpResponse.setStatusCode(400);
      httpResponse.setContentType("text/plain");
      httpResponse
          .getWriter()
          .append("No cluster id found at request header ")
          .append(REQUEST_HEADER_CLUSTER_ID);
      return;
    }

    LOGGER.info("Received request from cluster {}", clusterId.get());

    Gson gson = connectorFunction.getGson();

    try {
      Object result = connectorFunction.execute(new GCPInput(httpRequest, gson));

      response.setResult(result);
      httpResponse.setStatusCode(200);
    } catch (Exception error) {

      httpResponse.setStatusCode(500);
      response.setError(error.getMessage());
    }

    httpResponse.setContentType("application/json");
    gson.toJson(response, httpResponse.getWriter());
  }

  protected Optional<String> getClusterId(final HttpRequest httpRequest) {
    Optional<String> clusterId = httpRequest.getFirstHeader(REQUEST_HEADER_CLUSTER_ID);
    return clusterId;
  }

  private class GCPInput implements ConnectorContext {

    private HttpRequest request;
    private Gson gson;

    public GCPInput(HttpRequest request, Gson gson) {
      this.request = request;
      this.gson = gson;
    }

    @Override
    public <T extends Object> T getVariablesAsType(Class<T> cls) {

      try {
        return gson.fromJson(request.getReader(), cls);
      } catch (IOException exception) {
        throw new ConnectorInputException(exception);
      }
    }

    @Override
    public SecretStore getSecretStore() {
      return new GCPSecretStore(gson, getClusterId(request).get());
    }
  }
}
