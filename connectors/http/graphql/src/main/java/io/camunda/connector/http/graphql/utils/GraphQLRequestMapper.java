/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql.utils;

import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.graphql.model.GraphQLRequest;
import java.util.Map;
import java.util.stream.Collectors;

public final class GraphQLRequestMapper {

  public static HttpCommonRequest toHttpCommonRequest(GraphQLRequest graphQLRequest) {
    HttpCommonRequest httpCommonRequest = new HttpCommonRequest();
    final Map<String, Object> queryAndVariablesMap =
        JsonSerializeHelper.queryAndVariablesToMap(graphQLRequest);
    if (graphQLRequest.getMethod().supportsBody) {
      httpCommonRequest.setBody(queryAndVariablesMap);
    } else {
      final Map<String, String> queryAndVariablesStringMap =
          queryAndVariablesMap.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
      httpCommonRequest.setQueryParameters(queryAndVariablesStringMap);
    }
    httpCommonRequest.setAuthentication(graphQLRequest.getAuthentication());
    httpCommonRequest.setMethod(graphQLRequest.getMethod());
    httpCommonRequest.setUrl(graphQLRequest.getUrl());
    httpCommonRequest.setConnectionTimeoutInSeconds(graphQLRequest.getConnectionTimeoutInSeconds());
    return httpCommonRequest;
  }
}
