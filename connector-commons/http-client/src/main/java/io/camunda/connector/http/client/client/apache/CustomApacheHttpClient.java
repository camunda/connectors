/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.client.apache;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.http.client.blocklist.DefaultHttpBlocklistManager;
import io.camunda.connector.http.client.blocklist.HttpBlockListManager;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.proxy.ProxyAwareHttpClient;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMapper;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.ClientProtocolException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpStatus;

public class CustomApacheHttpClient implements HttpClient {

  private final HttpBlockListManager httpBlocklistManager = new DefaultHttpBlocklistManager();

  /**
   * Converts the given {@link HttpClientRequest} to an Apache {@link
   * org.apache.hc.core5.http.ClassicHttpRequest} and executes it.
   *
   * @param request the request to execute
   * @return the {@link StreamingHttpResponse} containing the response details
   */
  @Override
  public <T> HttpResponse<T> execute(HttpClientRequest request, ResponseMapper<T> responseMapper) {
    var apacheRequest = prepareApacheRequest(request);

    try (var client = newClient(apacheRequest, request)) {
      var apacheResponseHandler =
          new CustomResponseHandler<>(responseMapper, request.isFollowRedirects());
      return client.execute(apacheRequest, apacheResponseHandler);
    } catch (ClientProtocolException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_SERVER_ERROR),
          "An error with the HTTP protocol occurred",
          e);
    } catch (SocketTimeoutException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT),
          "The request timed out. Please try increasing the read and connection timeouts.",
          e);
    } catch (SSLException e) {
      throw new ConnectorException(
          "SSL_HANDSHAKE_FAILED",
          "TLS handshake failed: "
              + rootMessage(e)
              + ". The server certificate may not be trusted — provide the server's CA "
              + "certificate via 'clientTls.trustedCertificate', or check the client certificate "
              + "configuration.",
          e);
    } catch (IOException e) {
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT),
          "An error occurred while executing the request, or the connection was aborted",
          e);
    }
  }

  /** Returns the message of the deepest cause, e.g. the PKIX text of a wrapped TLS failure. */
  private static String rootMessage(Throwable t) {
    var cause = t;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    return cause.getMessage();
  }

  @Override
  public StreamingHttpResponse executeStreaming(HttpClientRequest request) {
    var apacheRequest = prepareApacheRequest(request);
    ProxyAwareHttpClient client = newClient(apacheRequest, request);
    try {
      ClassicHttpResponse response = client.executeOpen(apacheRequest);
      int status = response.getCode();
      String reason = response.getReasonPhrase();
      Map<String, List<String>> headers = formatHeaders(response.getHeaders());
      InputStream entityStream =
          response.getEntity() != null ? response.getEntity().getContent() : null;
      InputStream body = new ResponseClosingStream(entityStream, response, client);
      return new StreamingHttpResponse(status, reason, headers, body);
    } catch (ClientProtocolException e) {
      closeQuietly(client);
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_SERVER_ERROR),
          "An error with the HTTP protocol occurred",
          e);
    } catch (SocketTimeoutException e) {
      closeQuietly(client);
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT),
          "The request timed out. Please try increasing the read and connection timeouts.",
          e);
    } catch (SSLException e) {
      closeQuietly(client);
      throw new ConnectorException(
          "SSL_HANDSHAKE_FAILED",
          "TLS handshake failed: "
              + rootMessage(e)
              + ". The server certificate may not be trusted — provide the server's CA "
              + "certificate via 'clientTls.trustedCertificate', or check the client certificate "
              + "configuration.",
          e);
    } catch (IOException e) {
      closeQuietly(client);
      throw new ConnectorException(
          String.valueOf(HttpStatus.SC_REQUEST_TIMEOUT),
          "An error occurred while executing the request, or the connection was aborted",
          e);
    } catch (RuntimeException e) {
      closeQuietly(client);
      throw e;
    }
  }

  private ClassicHttpRequest prepareApacheRequest(HttpClientRequest request) {
    httpBlocklistManager.validateUrlAgainstBlocklist(request.getUrl());
    var apacheRequest = ApacheRequestFactory.get().createHttpRequest(request);

    var authority = apacheRequest.getAuthority();
    if (authority == null) {
      throw new ConnectorInputException(
          "Invalid URL: The URL '"
              + request.getUrl()
              + "' cannot be parsed as a valid HTTP request. "
              + "Please ensure the URL includes a valid hostname.");
    }
    if (apacheRequest.getScheme() == null) {
      throw new ConnectorInputException(
          "Invalid URL: The URL '"
              + request.getUrl()
              + "' cannot be parsed as a valid HTTP request. "
              + "Please ensure the URL includes a valid scheme.");
    }
    return apacheRequest;
  }

  private ProxyAwareHttpClient newClient(
      ClassicHttpRequest apacheRequest, HttpClientRequest request) {
    var host = apacheRequest.getAuthority().getHostName();
    var scheme = apacheRequest.getScheme();
    var sslContext =
        request.hasClientTls() ? ClientTlsFactory.create(request.getClientTls()) : null;
    return new ProxyAwareHttpClient(
        new ProxyAwareHttpClient.TimeoutConfiguration(
            request.getConnectionTimeoutInSeconds(), request.getReadTimeoutInSeconds()),
        new ProxyAwareHttpClient.ProxyContext(scheme, host),
        request.isFollowRedirects(),
        sslContext);
  }

  private static Map<String, List<String>> formatHeaders(Header[] headersArray) {
    return Arrays.stream(headersArray)
        .collect(
            Collectors.groupingBy(
                Header::getName, Collectors.mapping(Header::getValue, Collectors.toList())));
  }

  private static void closeQuietly(AutoCloseable closeable) {
    if (closeable == null) return;
    try {
      closeable.close();
    } catch (Exception ignored) {
    }
  }

  /** Closing this stream cascades close to the response and client, releasing the connection. */
  private static final class ResponseClosingStream extends FilterInputStream {

    private final ClassicHttpResponse response;
    private final ProxyAwareHttpClient client;
    private boolean closed;

    ResponseClosingStream(
        InputStream delegate, ClassicHttpResponse response, ProxyAwareHttpClient client) {
      super(delegate != null ? delegate : InputStream.nullInputStream());
      this.response = response;
      this.client = client;
    }

    @Override
    public void close() throws IOException {
      if (closed) {
        return;
      }
      closed = true;
      IOException firstError = null;
      try {
        super.close();
      } catch (IOException e) {
        firstError = e;
      }
      try {
        response.close();
      } catch (IOException e) {
        if (firstError == null) firstError = e;
      }
      try {
        client.close();
      } catch (IOException e) {
        if (firstError == null) firstError = e;
      }
      if (firstError != null) throw firstError;
    }
  }
}
