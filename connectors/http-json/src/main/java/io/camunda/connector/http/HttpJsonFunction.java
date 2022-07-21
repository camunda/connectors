package io.camunda.connector.http;

import static org.apache.http.entity.ContentType.APPLICATION_JSON;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.gson.Gson;
import io.camunda.connector.api.ConnectorContext;
import io.camunda.connector.api.ConnectorFunction;
import io.camunda.connector.api.Validator;
import io.camunda.connector.http.components.GsonComponentSupplier;
import io.camunda.connector.http.components.HttpTransportComponentSupplier;
import io.camunda.connector.http.model.HttpJsonRequest;
import io.camunda.connector.http.model.HttpJsonResult;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpJsonFunction implements ConnectorFunction {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpJsonFunction.class);

  private final Gson gson;
  private final GsonFactory gsonFactory;
  private final HttpRequestFactory requestFactory;

  public HttpJsonFunction() {
    this(
        GsonComponentSupplier.gsonInstance(),
        HttpTransportComponentSupplier.httpRequestFactoryInstance(),
        GsonComponentSupplier.gsonFactoryInstance());
  }

  public HttpJsonFunction(
      final Gson gson, final HttpRequestFactory requestFactory, final GsonFactory gsonFactory) {
    this.gson = gson;
    this.requestFactory = requestFactory;
    this.gsonFactory = gsonFactory;
  }

  @Override
  public Object execute(final ConnectorContext context) throws IOException {
    final var json = context.getVariables();
    final var request = gson.fromJson(json, HttpJsonRequest.class);

    final var validator = new Validator();
    request.validate(validator);
    validator.validate();

    request.replaceSecrets(context.getSecretStore());

    return handleRequest(request);
  }

  protected HttpJsonResult handleRequest(final HttpJsonRequest request) throws IOException {
    final HttpRequest externalRequest = createRequest(request);
    final HttpResponse externalResponse = sendRequest(externalRequest);
    return toHttpJsonResponse(externalResponse);
  }

  protected HttpResponse sendRequest(final HttpRequest request) throws IOException {
    return request.execute();
  }

  private HttpRequest createRequest(final HttpJsonRequest request) throws IOException {
    final var content = createContent(request);

    final var method = request.getMethod().toUpperCase();
    final var url = request.getUrl();

    final GenericUrl genericUrl = new GenericUrl(url);

    if (request.hasQueryParameters()) {
      genericUrl.putAll(request.getQueryParameters());
    }

    final var httpRequest = requestFactory.buildRequest(method, genericUrl, content);

    final var headers = createHeaders(request);
    httpRequest.setHeaders(headers);
    return httpRequest;
  }

  private HttpContent createContent(final HttpJsonRequest request) {
    if (request.hasBody()) {
      return new JsonHttpContent(gsonFactory, request.getBody());
    } else {
      return null;
    }
  }

  private HttpHeaders createHeaders(final HttpJsonRequest request) {
    final HttpHeaders httpHeaders = new HttpHeaders();

    if (request.hasBody()) {
      httpHeaders.setContentType(APPLICATION_JSON.getMimeType());
    }

    if (request.hasAuthentication()) {
      request.getAuthentication().setHeaders(httpHeaders);
    }

    if (request.hasHeaders()) {
      httpHeaders.putAll(request.getHeaders());
    }

    return httpHeaders;
  }

  private HttpJsonResult toHttpJsonResponse(final HttpResponse externalResponse) {
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
      LOGGER.error("Failed to parse external response: {}", e);
    }
    return httpJsonResult;
  }
}
