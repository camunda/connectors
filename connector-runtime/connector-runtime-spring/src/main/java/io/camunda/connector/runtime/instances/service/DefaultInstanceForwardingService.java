package io.camunda.connector.runtime.instances.service;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.runtime.core.http.DefaultInstancesUrlBuilder;
import io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient;
import io.camunda.connector.runtime.instances.reducer.ReducerRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInstanceForwardingService implements InstanceForwardingService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultInstanceForwardingService.class);

  private final String hostname;

  private final ReducerRegistry reducerRegistry = new ReducerRegistry();

  private final InstanceForwardingHttpClient instanceForwardingHttpClient;

  public DefaultInstanceForwardingService(int appPort, String headlessServiceUrl, String hostname) {
    this(
        new InstanceForwardingHttpClient(
            new DefaultInstancesUrlBuilder(appPort, headlessServiceUrl)),
        hostname);
  }

  public DefaultInstanceForwardingService(
      InstanceForwardingHttpClient instanceForwardingHttpClient, String hostname) {
    this.instanceForwardingHttpClient = instanceForwardingHttpClient;
    this.hostname = hostname;
  }

  @Override
  public <T> List<T> forward(HttpServletRequest request, TypeReference<T> responseType) {
    String method = request.getMethod();
    String path = request.getRequestURI();
    if (request.getQueryString() != null) {
      path += "?" + request.getQueryString();
    }
    try (var reader = request.getReader()) {
      String body = reader.lines().collect(Collectors.joining(System.lineSeparator()));

      Map<String, String> headers =
          Collections.list(request.getHeaderNames()).stream()
              .collect(Collectors.toMap(headerName -> headerName, request::getHeader));

      if (hostname == null) {
        LOGGER.error(
            "HOSTNAME environment variable (or 'camunda.connector.hostname' property) is not set. Cannot use instances forwarding.");
        throw new RuntimeException(
            "HOSTNAME environment variable (or 'camunda.connector.hostname' property) is not set. Cannot use instances forwarding.");
      }

      headers.put(X_CAMUNDA_FORWARDED_FOR, hostname);

      return instanceForwardingHttpClient.execute(method, path, body, headers, responseType);
    } catch (Exception e) {
      LOGGER.error("Error forwarding request to instances: {}", e.getMessage(), e);
      throw new RuntimeException("Error forwarding request to instances: " + e.getMessage(), e);
    }
  }

  @Override
  public <T> T reduce(List<T> responses, TypeReference<T> responseType) {
    if (responses == null || responses.isEmpty()) {
      return null;
    }
    var reducer = reducerRegistry.getReducer(responseType);

    if (reducer == null) {
      LOGGER.error("No reducer found for response type {}.", responseType.getType());
      throw new RuntimeException("No reducer found for response type: " + responseType.getType());
    }

    return responses.stream().reduce(null, reducer::reduce);
  }
}
