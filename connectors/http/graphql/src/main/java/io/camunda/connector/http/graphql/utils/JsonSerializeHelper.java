/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql.utils;

import io.camunda.connector.http.graphql.model.GraphQLRequest;
import java.util.HashMap;
import java.util.Map;

public final class JsonSerializeHelper {

  public static Map<String, Object> queryAndVariablesToMap(GraphQLRequest graphQLRequest) {
    final Map<String, Object> map = new HashMap<>();
    map.put("query", getEscapedQuery(graphQLRequest.graphql().query()));
    if (graphQLRequest.graphql().variables() != null) {
      map.put("variables", graphQLRequest.graphql().variables());
    }
    return map;
  }

  public static String getEscapedQuery(String query) {
    return query.replace("\\n", "").replace("\\\"", "\"");
  }
}
