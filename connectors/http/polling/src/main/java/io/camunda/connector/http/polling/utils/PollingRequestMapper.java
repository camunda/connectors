/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.polling.model.PollingRuntimeProperties;

public class PollingRequestMapper {
  private final ObjectMapper objectMapper;

  public PollingRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public HttpCommonRequest toHttpCommonRequest(PollingRuntimeProperties pollingRuntimeProperties) {
    HttpCommonRequest httpCommonRequest = new HttpCommonRequest();
    httpCommonRequest.setMethod(pollingRuntimeProperties.getMethod());
    httpCommonRequest.setUrl(pollingRuntimeProperties.getUrl());
    httpCommonRequest.setHeaders(pollingRuntimeProperties.getHeaders());
    httpCommonRequest.setQueryParameters(pollingRuntimeProperties.getQueryParameters());
    httpCommonRequest.setBody(pollingRuntimeProperties.getBody());
    httpCommonRequest.setAuthentication(pollingRuntimeProperties.getAuthentication());
    httpCommonRequest.setConnectionTimeoutInSeconds(
        pollingRuntimeProperties.getConnectionTimeoutInSeconds());
    httpCommonRequest.setReadTimeoutInSeconds(pollingRuntimeProperties.getReadTimeoutInSeconds());
    httpCommonRequest.setSkipEncoding(pollingRuntimeProperties.getSkipEncoding());
    return httpCommonRequest;
  }
}
