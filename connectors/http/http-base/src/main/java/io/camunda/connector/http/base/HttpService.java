/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentReturn;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.auth.AuthenticationMapper;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.exception.ConnectorExceptionMapper;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.utils.HeadersHelper;
import io.camunda.connector.http.client.utils.HttpStatusHelper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.Optional;
import org.apache.hc.core5.http.HttpHeaders;

public class HttpService {

  private static final HttpClient HTTP_CLIENT = new CustomApacheHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  public HttpCommonResult executeConnectorRequest(HttpCommonRequest request) {
    return (HttpCommonResult) executeConnectorRequest(request, null, null);
  }

  public HttpCommonResult executeConnectorRequest(
      final HttpCommonRequest request, final DocumentFactory documentFactory) {
    return (HttpCommonResult) executeConnectorRequest(request, documentFactory, null);
  }

  /**
   * Executes the HTTP request. A non-null {@code choice} takes the {@link DocumentReturn} flow: the
   * body stream is handed to the runtime for conversion, and the runtime owns closing it. A null
   * {@code choice} takes the legacy {@code storeResponse} path.
   */
  public Object executeConnectorRequest(
      final HttpCommonRequest request,
      final DocumentFactory documentFactory,
      final DocumentReturnChoice choice) {
    HttpClientRequest httpClientRequest = mapToHttpClientRequest(request);

    if (choice != null) {
      StreamingHttpResponse live = HTTP_CLIENT.executeStreaming(httpClientRequest);
      int status = live.status();
      // Match the legacy path: an error status fails the job with the response in error variables,
      // rather than streaming the error body to the document store. Reads and closes the stream.
      if (HttpStatusHelper.isError(status)) {
        throw ConnectorExceptionMapper.from(live);
      }
      String reason = live.reason();
      var flatHeaders = HeadersHelper.flattenHeaders(live.headers());
      String contentType =
          HeadersHelper.getHeaderIgnoreCase(live.headers(), HttpHeaders.CONTENT_TYPE);
      return DocumentReturn.of(
          live.body(),
          contentType,
          null,
          (converted, c) -> {
            if (c == DocumentReturnChoice.DOCUMENT) {
              return new HttpCommonResult(status, flatHeaders, null, reason, (Document) converted);
            }
            return new HttpCommonResult(status, flatHeaders, converted, reason, null);
          });
    }

    HttpCommonResultMapper responseMapper =
        new HttpCommonResultMapper(documentFactory, request.isStoreResponse(), OBJECT_MAPPER);
    return HTTP_CLIENT.execute(httpClientRequest, responseMapper).entity();
  }

  public HttpClientRequest mapToHttpClientRequest(HttpCommonRequest request) {
    HttpClientRequest httpClientRequest = new HttpClientRequest();
    httpClientRequest.setMethod(
        io.camunda.connector.http.client.model.HttpMethod.valueOf(request.getMethod().name()));
    httpClientRequest.setUrl(request.getUrl());
    httpClientRequest.setHeaders(request.getHeaders().orElse(null));
    httpClientRequest.setQueryParameters(request.getQueryParameters());
    httpClientRequest.setBody(request.getBody());
    httpClientRequest.setAuthentication(AuthenticationMapper.map(request.getAuthentication()));
    httpClientRequest.setConnectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds());
    httpClientRequest.setReadTimeoutInSeconds(request.getReadTimeoutInSeconds());
    httpClientRequest.setSkipEncoding(request.getSkipEncoding());
    httpClientRequest.setIgnoreNullValues(request.isIgnoreNullValues());
    httpClientRequest.setFollowRedirects(request.isFollowRedirects());
    Optional.ofNullable(request.getClientTls())
        .map(
            tls ->
                new io.camunda.connector.http.client.model.ClientTls(
                    tls.clientCertificate(),
                    tls.clientPrivateKey(),
                    tls.privateKeyPassword(),
                    tls.trustedCertificate()))
        .ifPresent(httpClientRequest::setClientTls);
    return httpClientRequest;
  }
}
