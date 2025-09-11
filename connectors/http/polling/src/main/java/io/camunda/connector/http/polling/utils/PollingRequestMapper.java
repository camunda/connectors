/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.polling.model.PollingRequest;

public class PollingRequestMapper {
  private final ObjectMapper objectMapper;

  public PollingRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public HttpCommonRequest toHttpCommonRequest(PollingRequest pollingRequest) {
    HttpCommonRequest httpCommonRequest = new HttpCommonRequest();
    httpCommonRequest.setMethod(pollingRequest.getMethod());
    httpCommonRequest.setUrl(pollingRequest.getUrl());
    httpCommonRequest.setHeaders(pollingRequest.getHeaders());
    httpCommonRequest.setQueryParameters(pollingRequest.getQueryParameters());
    httpCommonRequest.setBody(pollingRequest.getBody());
    httpCommonRequest.setAuthentication(pollingRequest.getAuthentication());
    httpCommonRequest.setConnectionTimeoutInSeconds(pollingRequest.getConnectionTimeoutInSeconds());
    httpCommonRequest.setReadTimeoutInSeconds(pollingRequest.getReadTimeoutInSeconds());
    httpCommonRequest.setSkipEncoding(pollingRequest.getSkipEncoding());
    return httpCommonRequest;
  }
}
