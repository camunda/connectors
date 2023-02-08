/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.common.auth.Authentication;
import io.camunda.connector.common.auth.BasicAuthentication;
import io.camunda.connector.common.auth.BearerAuthentication;
import io.camunda.connector.common.auth.NoAuthentication;
import io.camunda.connector.common.auth.OAuthAuthentication;
import io.camunda.connector.graphql.model.GraphQLRequest;
import io.camunda.connector.graphql.utils.JsonSerializeHelper;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class GraphQLFunctionSecretsTest extends BaseTest {
  private static final String SUCCESS_REPLACE_SECRETS_CASES_PATH =
      "src/test/resources/requests/success-cases-replace-secrets.json";

  private OutboundConnectorContext context;

  protected static Stream<String> successReplaceSecretsCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_CASES_PATH);
  }

  @ParameterizedTest(name = "Should replace request secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceRequestSecrets(String input) {
    // Given request with secrets
    GraphQLRequest graphQLRequest = JsonSerializeHelper.serializeRequest(gson, input);
    context = getContextBuilderWithSecrets().variables(graphQLRequest).build();
    // When
    context.replaceSecrets(graphQLRequest);
    // Then should replace secrets
    assertThat(graphQLRequest.getUrl()).isEqualTo(ActualValue.URL);
    assertThat(graphQLRequest.getMethod()).isEqualTo(ActualValue.METHOD);
    assertThat(graphQLRequest.getConnectionTimeoutInSeconds())
        .isEqualTo(ActualValue.CONNECT_TIMEOUT);
  }

  @ParameterizedTest(name = "Should replace auth secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceAuthSecrets(String input) {
    // Given request with secrets
    GraphQLRequest graphQLRequest = JsonSerializeHelper.serializeRequest(gson, input);
    context = getContextBuilderWithSecrets().variables(graphQLRequest).build();
    // When
    context.replaceSecrets(graphQLRequest);
    // Then should replace secrets
    Authentication authentication = graphQLRequest.getAuthentication();
    if (authentication instanceof NoAuthentication) {
      // nothing check in this case
    } else if (authentication instanceof BearerAuthentication) {
      BearerAuthentication bearerAuth = (BearerAuthentication) authentication;
      assertThat(bearerAuth.getToken()).isEqualTo(ActualValue.Authentication.TOKEN);
    } else if (authentication instanceof BasicAuthentication) {
      BasicAuthentication basicAuth = (BasicAuthentication) authentication;
      assertThat(basicAuth.getPassword()).isEqualTo(ActualValue.Authentication.PASSWORD);
      assertThat(basicAuth.getUsername()).isEqualTo(ActualValue.Authentication.USERNAME);
    } else if (authentication instanceof OAuthAuthentication) {
      OAuthAuthentication oAuthAuthentication = (OAuthAuthentication) authentication;
      assertThat(oAuthAuthentication.getOauthTokenEndpoint())
          .isEqualTo(ActualValue.Authentication.OAUTH_TOKEN_ENDPOINT);
      assertThat(oAuthAuthentication.getClientId()).isEqualTo(ActualValue.Authentication.CLIENT_ID);
      assertThat(oAuthAuthentication.getClientSecret())
          .isEqualTo(ActualValue.Authentication.CLIENT_SECRET);
      assertThat(oAuthAuthentication.getAudience()).isEqualTo(ActualValue.Authentication.AUDIENCE);
    } else {
      fail("unknown authentication type");
    }
  }

  @ParameterizedTest(name = "Should replace variables secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceVariablesSecrets(String input) {
    // Given request with secrets
    // GraphQLRequestWrapper graphQLRequest = gson.fromJson(input, GraphQLRequestWrapper.class);
    GraphQLRequest graphQLRequest = JsonSerializeHelper.serializeRequest(gson, input);
    context = getContextBuilderWithSecrets().variables(graphQLRequest).build();
    // When
    context.replaceSecrets(graphQLRequest);
    // Then should replace secrets
    JsonObject variables = gson.toJsonTree(graphQLRequest.getVariables()).getAsJsonObject();

    assertThat(variables.get(JsonKeys.ID).getAsString()).isEqualTo(ActualValue.Variables.ID);
  }

  @ParameterizedTest(name = "Should replace query secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceQuerySecrets(String input) {
    // Given request with secrets
    GraphQLRequest graphQLRequest = JsonSerializeHelper.serializeRequest(gson, input);
    context = getContextBuilderWithSecrets().variables(graphQLRequest).build();
    // When
    context.replaceSecrets(graphQLRequest);
    // Then should replace secrets
    String query = graphQLRequest.getQuery();
    assertFalse(query.contains("{{secrets.QUERY_ID}}"));
    assertTrue(query.contains(ActualValue.Query.ID));
  }

  @Test
  void replaceSecrets_shouldReplaceQueryWhenQueryIsString() {
    // Given request with secrets
    GraphQLRequest request = new GraphQLRequest();
    request.setQuery(
        "{{secrets."
            + SecretsConstant.Query.TEXT_PART_1
            + "}}"
            + "{{secrets."
            + SecretsConstant.Query.TEXT_PART_2
            + "}}"
            + "{{secrets."
            + SecretsConstant.Query.TEXT_PART_3
            + "}}");
    context = getContextBuilderWithSecrets().variables(request).build();
    // When
    context.replaceSecrets(request);
    // Then should replace secrets
    assertThat(request.getQuery().toString()).isEqualTo(ActualValue.Query.TEXT);
  }
}
