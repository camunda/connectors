/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.BearerAuthentication;
import io.camunda.connector.http.base.model.auth.RestAuthenticationConfiguration;
import io.camunda.connector.http.graphql.model.GraphQLRequest.GraphQL;
import org.junit.jupiter.api.Test;

/** Verifies the per-connector consumption of a bound authentication credential (configuration). */
class GraphQLRequestTest {

  private GraphQL graphQL() {
    return graphQL("https://example.com/graphql");
  }

  private GraphQL graphQL(String url) {
    return new GraphQL("query { field }", null, HttpMethod.POST, url, null, false, 20, 20);
  }

  @Test
  void usesCredentialAuthenticationWhenBound() {
    var request =
        new GraphQLRequest(
            graphQL(),
            new RestAuthenticationConfiguration(
                new BearerAuthentication("credential-token"), "https://credential.example.com"),
            new BearerAuthentication("inline-token"));

    assertThat(request.authentication()).isInstanceOf(BearerAuthentication.class);
    assertThat(((BearerAuthentication) request.authentication()).token())
        .isEqualTo("credential-token");
  }

  @Test
  void fallsBackToInlineAuthenticationWhenNoCredential() {
    var request = new GraphQLRequest(graphQL(), null, new BearerAuthentication("inline-token"));

    assertThat(((BearerAuthentication) request.authentication()).token()).isEqualTo("inline-token");
  }

  @Test
  void usesCredentialUrlWhenInlineUrlIsBlank() {
    var request =
        new GraphQLRequest(
            graphQL(null),
            new RestAuthenticationConfiguration(
                new BearerAuthentication("credential-token"), "https://credential.example.com"),
            null);

    assertThat(request.getEffectiveUrl()).isEqualTo("https://credential.example.com");
  }

  @Test
  void inlineUrlOverridesCredentialUrl() {
    var request =
        new GraphQLRequest(
            graphQL("https://override.example.com"),
            new RestAuthenticationConfiguration(
                new BearerAuthentication("credential-token"), "https://credential.example.com"),
            null);

    assertThat(request.getEffectiveUrl()).isEqualTo("https://override.example.com");
  }
}
