package io.camunda.connector.runtime.cloud;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.camunda.connector.sdk.ConnectorContext;
import io.camunda.connector.sdk.ConnectorFunction;
import io.camunda.connector.sdk.ConnectorInputException;
import io.camunda.connector.sdk.SecretStore;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudConnectorFunction implements HttpFunction {

  private static final Gson GSON = new GsonBuilder().create();

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudConnectorFunction.class);

  private static final String REQUEST_HEADER_CLUSTER_ID = "X-Camunda-Cluster-ID";

  private final ConnectorFunction connectorFunction;

  public CloudConnectorFunction(ConnectorFunction connectorFunction) {
    this.connectorFunction = connectorFunction;
  }

  public CloudConnectorFunction() {
    this.connectorFunction = new ConnectorFunctionLoader().load();

    LOGGER.info("Connector Function: {}", this.connectorFunction.getClass().getName());
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

    try {
      var result = connectorFunction.execute(new CloudConnectorContext(httpRequest, GSON));

      LOGGER.debug("Completed request from cluster {}", clusterId.get());

      response.setResult(result);
      httpResponse.setStatusCode(200);

    } catch (Exception error) {

      LOGGER.debug("Failed to process request from cluster {}", clusterId.get(), error);

      httpResponse.setStatusCode(500);
      response.setError(error.getMessage());
    }

    httpResponse.setContentType("application/json");
    GSON.toJson(response, httpResponse.getWriter());
  }

  protected Optional<String> getClusterId(final HttpRequest httpRequest) {
    Optional<String> clusterId = httpRequest.getFirstHeader(REQUEST_HEADER_CLUSTER_ID);
    return clusterId;
  }

  class CloudConnectorContext implements ConnectorContext {

    private HttpRequest request;
    private Gson gson;

    public CloudConnectorContext(HttpRequest request, Gson gson) {
      this.request = request;
      this.gson = gson;
    }

    @Override
    public String getVariables() {
      try {
        return CharStreams.toString(request.getReader());
      } catch (IOException exception) {
        throw new ConnectorInputException(exception);
      }
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
      return new CloudSecretStore(gson, getClusterId(request).get());
    }
  }

  class ConnectorFunctionLoader {

    public ConnectorFunction load() {

      // work around for context class loader not being set
      // https://github.com/GoogleCloudPlatform/functions-framework-java/issues/110

      final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(CloudConnectorFunction.class.getClassLoader());

      try {
        return Optional.<ConnectorFunction>empty()
            .or(() -> loadFromEnv())
            .or(() -> loadViaServiceLoader())
            .orElseThrow(() -> loadFailure(ConnectorFunction.class.getName() + " not configured"));

      } finally {
        Thread.currentThread().setContextClassLoader(contextClassLoader);
      }
    }

    private Optional<ConnectorFunction> loadViaServiceLoader() {

      ServiceLoader<ConnectorFunction> providers = ServiceLoader.load(ConnectorFunction.class);

      Optional<ConnectorFunction> first = providers.findFirst();

      var count = providers.stream().count();

      if (count == 0) {
        throw loadFailure("No " + ConnectorFunction.class.getName() + " defined");
      }

      if (count != 1) {
        throw loadFailure(
            "Multiple implementations of "
                + ConnectorFunction.class.getName()
                + " on the classpath");
      }

      return first;
    }

    private Optional<ConnectorFunction> loadFromEnv() {

      final var connectorFunction = Optional.ofNullable(System.getenv("CONNECTOR_FUNCTION"));

      return connectorFunction
          .map(
              (clsName) -> {
                try {
                  return (Class<ConnectorFunction>) Class.forName(clsName);
                } catch (ClassNotFoundException | ClassCastException exception) {
                  throw loadFailure(connectorFunction.get(), exception);
                }
              })
          .map(
              (cls) -> {
                try {
                  return cls.getDeclaredConstructor().newInstance();
                } catch (NoSuchMethodException
                    | InstantiationException
                    | IllegalAccessException
                    | InvocationTargetException exception) {
                  throw loadFailure(connectorFunction.get(), exception);
                }
              });
    }

    private RuntimeException loadFailure(String cls, Throwable cause) {
      return new IllegalStateException("Failed to load " + cls, cause);
    }

    private RuntimeException loadFailure(String message) {
      return new IllegalStateException(message);
    }
  }
}
