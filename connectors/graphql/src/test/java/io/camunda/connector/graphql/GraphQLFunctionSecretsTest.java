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
import io.camunda.connector.graphql.model.GraphQLRequestWrapper;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class GraphQLFunctionSecretsTest extends BaseTest {
  private static final String SUCCESS_REPLACE_SECRETS_CASES_PATH =
      "src/test/resources/requests/success-cases-replace-secrets.json";

  private OutboundConnectorContext context;

  protected static Stream<String> successReplaceSecretsCases() throws IOException {
    return loadTestCasesFromResourceFile(SUCCESS_REPLACE_SECRETS_CASES_PATH);
  }

  @ParameterizedTest(name = "Should replace auth secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceAuthSecrets(String input) {
    // Given request with secrets
    context = getContextBuilderWithSecrets().variables(input).build();
    var graphQLRequest = context.bindVariables(GraphQLRequestWrapper.class);
    // Then should replace secrets
    Authentication authentication = graphQLRequest.getAuthentication();
    if (authentication instanceof NoAuthentication) {
      // nothing check in this case
    } else if (authentication instanceof BearerAuthentication bearerAuth) {
      assertThat(bearerAuth.getToken()).isEqualTo(ActualValue.Authentication.TOKEN);
    } else if (authentication instanceof BasicAuthentication basicAuth) {
      assertThat(basicAuth.getPassword()).isEqualTo(ActualValue.Authentication.PASSWORD);
      assertThat(basicAuth.getUsername()).isEqualTo(ActualValue.Authentication.USERNAME);
    } else if (authentication instanceof OAuthAuthentication oAuthAuthentication) {
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
    context = getContextBuilderWithSecrets().variables(input).build();
    var graphQLRequest = context.bindVariables(GraphQLRequestWrapper.class);
    // Then should replace secrets
    JsonObject variables =
        gson.toJsonTree(graphQLRequest.getGraphql().getVariables()).getAsJsonObject();
    assertThat(variables.get(JsonKeys.ID).getAsString()).isEqualTo(ActualValue.Variables.ID);
  }

  @ParameterizedTest(name = "Should replace query secrets")
  @MethodSource("successReplaceSecretsCases")
  void replaceSecrets_shouldReplaceQuerySecrets(String input) {
    context = getContextBuilderWithSecrets().variables(input).build();
    var graphQLRequest = context.bindVariables(GraphQLRequestWrapper.class);
    // Then should replace secrets
    String query = graphQLRequest.getGraphql().getQuery();
    assertFalse(query.contains("{{secrets.QUERY_ID}}"));
    assertTrue(query.contains(ActualValue.Query.ID));
  }
}
