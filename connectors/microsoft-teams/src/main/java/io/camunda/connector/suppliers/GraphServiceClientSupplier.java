/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.suppliers;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.authentication.ClientSecretAuthentication;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;

public class GraphServiceClientSupplier {

  public GraphServiceClient<Request> buildAndGetGraphServiceClient(
      final ClientSecretAuthentication authentication) {

    ClientSecretCredential build =
        new ClientSecretCredentialBuilder()
            .tenantId(authentication.getTenantId())
            .clientId(authentication.getClientId())
            .clientSecret(authentication.getClientSecret())
            .build();

    TokenCredentialAuthProvider tokenCredentialAuthProvider =
        new TokenCredentialAuthProvider(build);
    return GraphServiceClient.<Request>builder()
        .authenticationProvider(tokenCredentialAuthProvider)
        .buildClient();
  }

  public GraphServiceClient<Request> buildAndGetGraphServiceClient(final String bearerToken) {
    return GraphServiceClient.builder()
        .authenticationProvider(new DelegateAuthenticationProvider(bearerToken))
        .buildClient();
  }

  public static class DelegateAuthenticationProvider implements IAuthenticationProvider {
    private String token;

    public DelegateAuthenticationProvider(String token) {
      this.token = token;
    }

    @NotNull
    @Override
    public CompletableFuture<String> getAuthorizationTokenAsync(@NotNull URL url) {
      return CompletableFuture.completedFuture(token);
    }
  }
}
