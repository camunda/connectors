/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.azurefoundry.http;

import com.anthropic.backends.Backend;
import com.anthropic.core.RequestOptions;
import com.anthropic.core.http.HttpClient;
import com.anthropic.core.http.HttpRequest;
import com.anthropic.core.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link HttpClient} decorator that applies a {@link Backend}'s request-preparation and
 * authorization logic to every outgoing request before delegating to the underlying transport.
 *
 * <p>The Anthropic Java SDK's {@link Backend} interface provides three hooks that must be invoked
 * in order for each HTTP call:
 *
 * <ol>
 *   <li>{@link Backend#prepareRequest(HttpRequest)} — transforms path segments and headers (e.g.
 *       the Foundry backend inserts an {@code anthropic} path segment prefix required by Azure AI
 *       Foundry).
 *   <li>{@link Backend#authorizeRequest(HttpRequest)} — adds authentication headers (API-key or
 *       bearer-token).
 *   <li>{@link Backend#prepareResponse(com.anthropic.core.http.HttpResponse)} — adapts the raw
 *       response if the backend requires any post-processing (currently a no-op for Foundry).
 * </ol>
 *
 * <p>These hooks are NOT invoked by {@link com.anthropic.core.ClientOptions} or any built-in SDK
 * layer; it is the responsibility of the {@link HttpClient} implementation to call them. This class
 * fulfils that contract so that the {@link com.anthropic.foundry.backends.FoundryBackend} is
 * correctly integrated when using our {@link JdkAnthropicHttpClient} transport.
 */
public final class BackendAwareAnthropicHttpClient implements HttpClient {

  private static final Logger LOG = LoggerFactory.getLogger(BackendAwareAnthropicHttpClient.class);

  private final HttpClient delegate;
  private final Backend backend;

  public BackendAwareAnthropicHttpClient(HttpClient delegate, Backend backend) {
    this.delegate = delegate;
    this.backend = backend;
  }

  @Override
  public HttpResponse execute(HttpRequest request, RequestOptions requestOptions) {
    HttpResponse response = delegate.execute(prepareAndAuthorize(request), requestOptions);
    return backend.prepareResponse(response);
  }

  @Override
  public CompletableFuture<HttpResponse> executeAsync(
      HttpRequest request, RequestOptions requestOptions) {
    return delegate
        .executeAsync(prepareAndAuthorize(request), requestOptions)
        .thenApply(backend::prepareResponse);
  }

  @Override
  public void close() {
    LOG.debug("Closing BackendAwareAnthropicHttpClient");
    delegate.close();
    backend.close();
  }

  private HttpRequest prepareAndAuthorize(HttpRequest request) {
    return backend.authorizeRequest(backend.prepareRequest(request));
  }
}
