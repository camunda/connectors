/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.utils;

import io.camunda.connector.common.constants.Constants;
import io.camunda.connector.common.model.CommonRequest;
import io.camunda.connector.graphql.model.GraphQLRequest;
import java.util.Map;
import java.util.stream.Collectors;

public final class GraphQLRequestMapper {

  public static CommonRequest toCommonRequest(GraphQLRequest graphQLRequest) {
    CommonRequest commonRequest = new CommonRequest();
    final Map<String, Object> queryAndVariablesMap =
        JsonSerializeHelper.queryAndVariablesToMap(graphQLRequest);
    if (Constants.POST.equalsIgnoreCase(graphQLRequest.getMethod())) {
      commonRequest.setBody(queryAndVariablesMap);
    } else {
      final Map<String, String> queryAndVariablesStringMap =
          queryAndVariablesMap.entrySet().stream()
              .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
      commonRequest.setQueryParameters(queryAndVariablesStringMap);
    }
    commonRequest.setAuthentication(graphQLRequest.getAuthentication());
    commonRequest.setMethod(graphQLRequest.getMethod());
    commonRequest.setUrl(graphQLRequest.getUrl());
    commonRequest.setConnectionTimeoutInSeconds(graphQLRequest.getConnectionTimeoutInSeconds());
    return commonRequest;
  }
}
