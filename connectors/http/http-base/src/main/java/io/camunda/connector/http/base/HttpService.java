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
import io.camunda.connector.api.document.RawPayload;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpCommonResult;
import io.camunda.connector.http.base.model.auth.AuthenticationMapper;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.utils.HeadersHelper;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import org.apache.hc.core5.http.HttpHeaders;

public class HttpService {

  private static final HttpClient HTTP_CLIENT = new CustomApacheHttpClient();
  private static final ObjectMapper OBJECT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  public HttpCommonResult executeConnectorRequest(HttpCommonRequest request) {
    return (HttpCommonResult) executeConnectorRequest(request, null, null);
  }

  /**
   * Convenience overload used by callers that pre-date the DocumentReturn flow (HTTP polling,
   * direct test invocations of the legacy path). Forwards to the 3-arg form with a {@code null}
   * choice so the legacy {@code storeResponse} boolean code path is taken.
   */
  public HttpCommonResult executeConnectorRequest(
      HttpCommonRequest request, DocumentFactory documentFactory) {
    return (HttpCommonResult) executeConnectorRequest(request, documentFactory, null);
  }

  /**
   * Executes the HTTP request. When {@code choice} is non-null (the runtime found a {@code
   * documentReturnFormat} dropdown value in the job variables) the new {@link DocumentReturn} flow
   * is taken: the HTTP body stream is handed to the runtime, which performs the conversion (upload
   * / decode / parse) end-to-end. When {@code choice} is null the legacy {@code storeResponse}
   * boolean path is used.
   *
   * <p>The body stream returned via {@link DocumentReturn}'s {@link RawPayload} owns the underlying
   * HTTP connection and Apache client. Closing it cascades through both — the runtime closes it via
   * try-with-resources in {@code DocumentReturnProcessor.convert}. If the runtime never closes it
   * (e.g. an exception fires before the processor runs), {@code SpringConnectorJobHandler}'s
   * safety-net try/finally closes the stream so the client and connection are released.
   */
  public Object executeConnectorRequest(
      final HttpCommonRequest request,
      final DocumentFactory documentFactory,
      final DocumentReturnChoice choice) {
    HttpClientRequest httpClientRequest = mapToHttpClientRequest(request);

    if (choice != null) {
      StreamingHttpResponse live = HTTP_CLIENT.executeStreaming(httpClientRequest);
      int status = live.status();
      String reason = live.reason();
      var flatHeaders = HeadersHelper.flattenHeaders(live.headers());
      String contentType =
          HeadersHelper.getHeaderIgnoreCase(live.headers(), HttpHeaders.CONTENT_TYPE);
      RawPayload payload = new RawPayload(live.body(), contentType, null);
      return DocumentReturn.of(
          payload,
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
    return httpClientRequest;
  }
}
