package io.camunda.connector.http;

import com.google.api.client.http.*;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ToNumberPolicy;
import com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.camunda.connector.sdk.common.ConnectorContext;
import io.camunda.connector.sdk.common.ConnectorFunction;
import io.camunda.connector.sdk.common.ConnectorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpJsonFunction implements ConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpJsonFunction.class);
  private static final Gson GSON =
      new GsonBuilder()
          .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
          .registerTypeAdapterFactory(
              RuntimeTypeAdapterFactory.of(Authentication.class)
                  .registerSubtype(BasicAuthentication.class, "basic")
                  .registerSubtype(BearerAuthentication.class, "bearer"))
          .create();
  public static final GsonFactory GSON_FACTORY = new GsonFactory();
  public static final HttpTransport HTTP_TRANSPORT = new ApacheHttpTransport();
  public static final HttpRequestFactory REQUEST_FACTORY =
      HTTP_TRANSPORT.createRequestFactory(
          request -> request.setParser(new JsonObjectParser(GSON_FACTORY)));

  @Override
  public Object service(ConnectorContext context) {
    final var request = context.getVariableAsType(HttpJsonRequest.class);

    final Validator validator = new Validator();
    request.validate(validator);
    validator.validate();

    request.replaceSecrets(context.getSecretStore());

    try {
      return handleRequest(request);
    } catch (final Exception e) {
      LOGGER.error("Failed to execute request: " + e.getMessage(), e);

      throw ConnectorResponse.failed(e);
    }
  }

  protected HttpJsonResult handleRequest(final HttpJsonRequest request) throws IOException {
    final com.google.api.client.http.HttpRequest externalRequest = createRequest(request);
    final com.google.api.client.http.HttpResponse externalResponse = sendRequest(externalRequest);
    return toHttpJsonResponse(externalResponse);
  }

  protected com.google.api.client.http.HttpResponse sendRequest(
      final com.google.api.client.http.HttpRequest request1) throws IOException {
    return request1.execute();
  }

  private com.google.api.client.http.HttpRequest createRequest(final HttpJsonRequest request)
      throws IOException {
    final var content = createContent(request);

    final var method =
        Objects.requireNonNull(request.getMethod(), "Missing method parameter").toUpperCase();
    final var url = Objects.requireNonNull(request.getUrl(), "Missing URL parameter");

    final GenericUrl genericUrl = new GenericUrl(url);

    if (request.hasQueryParameters()) {
      genericUrl.putAll(request.getQueryParameters());
    }

    final var httpRequest = REQUEST_FACTORY.buildRequest(method, genericUrl, content);

    final var headers = createHeaders(request);
    httpRequest.setHeaders(headers);
    return httpRequest;
  }

  private HttpContent createContent(final HttpJsonRequest request) {
    if (request.hasBody()) {
      return new JsonHttpContent(GSON_FACTORY, request.getBody());
    } else {
      return null;
    }
  }

  private HttpHeaders createHeaders(final HttpJsonRequest request) {
    final HttpHeaders httpHeaders = new HttpHeaders();

    if (request.hasBody()) {
      httpHeaders.setContentType("application/json");
    }

    if (request.hasAuthentication()) {
      request.getAuthentication().setHeaders(httpHeaders);
    }

    if (request.hasHeaders()) {
      httpHeaders.putAll(request.getHeaders());
    }

    return httpHeaders;
  }

  private HttpJsonResult toHttpJsonResponse(
      final com.google.api.client.http.HttpResponse externalResponse) {
    final HttpJsonResult httpJsonResult = new HttpJsonResult();
    httpJsonResult.setStatus(externalResponse.getStatusCode());
    final Map<String, Object> headers = new HashMap<>();
    externalResponse
        .getHeaders()
        .forEach(
            (k, v) -> {
              if (v instanceof List && ((List<?>) v).size() == 1) {
                headers.put(k, ((List<?>) v).get(0));
              } else {
                headers.put(k, v);
              }
            });
    httpJsonResult.setHeaders(headers);
    try {
      final Object body = externalResponse.parseAs(Object.class);
      if (body != null) {
        httpJsonResult.setBody(body);
      }
    } catch (final Exception e) {
      // ignore
    }
    return httpJsonResult;
  }

}
