/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.utils;

import com.google.gson.Gson;
import io.camunda.connector.graphql.model.GraphQLRequest;
import io.camunda.connector.graphql.model.GraphQLRequestWrapper;

public class JsonSerializeHelper {
  public static GraphQLRequest serializeRequest(Gson gson, String input) {
    GraphQLRequestWrapper graphQLRequestWrapper = gson.fromJson(input, GraphQLRequestWrapper.class);
    GraphQLRequest graphQLRequest = graphQLRequestWrapper.getGraphql();
    graphQLRequest.setAuthentication(graphQLRequestWrapper.getAuthentication());
    return graphQLRequest;
  }
}
