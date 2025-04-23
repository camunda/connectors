package io.camunda.connector.runtime.core.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSession;

public class DummyHttpResponse<T> implements HttpResponse<T> {
  private final int statusCode;
  private final Map<String, String> headers;
  private final T body;
  private final String uri;

  public DummyHttpResponse(int statusCode, Map<String, String> headers, T body, String uri) {
    this.statusCode = statusCode;
    this.headers = headers;
    this.body = body;
    this.uri = uri;
  }

  @Override
  public int statusCode() {
    return statusCode;
  }

  @Override
  public HttpRequest request() {
    return null;
  }

  @Override
  public Optional<HttpResponse<T>> previousResponse() {
    return Optional.empty();
  }

  @Override
  public HttpHeaders headers() {
    return HttpHeaders.of(
        headers.entrySet().stream()
            .map(entry -> Map.entry(entry.getKey(), List.of(entry.getValue())))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)),
        (s, s2) -> true);
  }

  @Override
  public T body() {
    return body;
  }

  @Override
  public Optional<SSLSession> sslSession() {
    return Optional.empty();
  }

  @Override
  public URI uri() {
    return URI.create(uri);
  }

  @Override
  public HttpClient.Version version() {
    return null;
  }

  public record Person(String name, int age) {}
}
