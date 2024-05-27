/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.http.base.services;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.http.base.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import java.io.IOException;
import java.util.Map;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteExecutionService {

  private static final Logger LOG = LoggerFactory.getLogger(RemoteExecutionService.class);

  /**
   * Wraps the given request into a new request that is targeted at the Google function to execute
   * the request remotely.
   *
   * @param request the request to be executed remotely
   * @param proxyFunctionUrl the URL of the Google function
   * @return the new request that is targeted at the Google function
   * @throws IOException if the request cannot be serialized
   */
  public HttpCommonRequest toRemotelyExecutableRequest(
      final HttpCommonRequest request, final String proxyFunctionUrl) throws IOException {
    // Using the JsonHttpContent cannot work with an element on the root content,
    // hence write it ourselves:
    String contentAsJson =
        ConnectorsObjectMapperSupplier.DEFAULT_MAPPER.writeValueAsString(request);
    String token;
    try {
      token = ProxyOAuthHelper.getOAuthToken(proxyFunctionUrl);
    } catch (Exception e) {
      LOG.error("Failure during OAuth authentication attempt for the Google function", e);
      // this will be visible in Operate, so should hide the internal exception
      throw new ConnectorException(
          "Failure during OAuth authentication attempt for the Google function");
    }
    HttpCommonRequest proxyRequest = new HttpCommonRequest();
    proxyRequest.setMethod(HttpMethod.POST);
    proxyRequest.setUrl(proxyFunctionUrl);
    proxyRequest.setBody(contentAsJson);
    proxyRequest.setHeaders(
        Map.of(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType()));
    proxyRequest.setConnectionTimeoutInSeconds(request.getConnectionTimeoutInSeconds());
    proxyRequest.setReadTimeoutInSeconds(request.getReadTimeoutInSeconds());
    proxyRequest.setAuthentication(new BearerAuthentication(token));

    return proxyRequest;
  }
}
