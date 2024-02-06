/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import java.util.Map;
import java.util.stream.Collectors;

public final class GraphQLRequestMapper {

  private final ObjectMapper objectMapper;

  public GraphQLRequestMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public HttpCommonRequest toHttpCommonRequest(GraphQLRequest graphQLRequest) {
    HttpCommonRequest httpCommonRequest = new HttpCommonRequest();
    httpCommonRequest.setMethod(graphQLRequest.graphql().method());

    final Map<String, Object> queryAndVariablesMap =
        JsonSerializeHelper.queryAndVariablesToMap(graphQLRequest);
    if (httpCommonRequest.getMethod().supportsBody) {
      httpCommonRequest.setBody(queryAndVariablesMap);
    } else {
      httpCommonRequest.setQueryParameters(mapQueryAndVariablesToQueryParams(queryAndVariablesMap));
    }

    httpCommonRequest.setHeaders(graphQLRequest.graphql().headers());
    httpCommonRequest.setAuthentication(graphQLRequest.authentication());
    httpCommonRequest.setUrl(graphQLRequest.graphql().url());
    httpCommonRequest.setConnectionTimeoutInSeconds(
        graphQLRequest.graphql().connectionTimeoutInSeconds());

    return httpCommonRequest;
  }

  private Map<String, String> mapQueryAndVariablesToQueryParams(
      Map<String, Object> queryAndVariablesMap) {
    return queryAndVariablesMap.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                  if (e.getValue() instanceof Map m) {
                    try {
                      return objectMapper.writeValueAsString(m);
                    } catch (JsonProcessingException ex) {
                      throw new RuntimeException(ex);
                    }
                  }
                  return String.valueOf(e.getValue());
                }));
  }
}
