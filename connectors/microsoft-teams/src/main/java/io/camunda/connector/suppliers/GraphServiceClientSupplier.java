/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.suppliers;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.requests.GraphServiceClient;
import io.camunda.connector.model.authentication.ClientSecretAuthentication;
import io.camunda.connector.model.authentication.RefreshTokenAuthentication;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

public class GraphServiceClientSupplier {

  private static final String URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
  private static final String CLIENT_ID = "client_id";
  private static final String GRANT_TYPE = "grant_type";
  private static final String REFRESH_TOKEN = "refresh_token";
  private static final String CLIENT_SECRET = "client_secret";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
  private static final String ACCESS_TOKEN = "access_token";

  private final OkHttpClient okHttpClient;

  public GraphServiceClientSupplier() {
    this.okHttpClient = new OkHttpClient();
  }

  public GraphServiceClientSupplier(final OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

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

  public GraphServiceClient<Request> buildAndGetGraphServiceClient(
      final RefreshTokenAuthentication authentication) {
    return buildAndGetGraphServiceClient(getAccessToken(buildRequest(authentication)));
  }

  public GraphServiceClient<Request> buildAndGetGraphServiceClient(final String token) {
    return GraphServiceClient.builder()
        .authenticationProvider(new DelegateAuthenticationProvider(token))
        .buildClient();
  }

  @NotNull
  private Request buildRequest(final RefreshTokenAuthentication authentication) {
    RequestBody formBody =
        new FormBody.Builder()
            .add(CLIENT_ID, authentication.getClientId())
            .add(GRANT_TYPE, REFRESH_TOKEN)
            .add(CLIENT_SECRET, authentication.getClientSecret())
            .add(REFRESH_TOKEN, authentication.getToken())
            .build();
    return new Request.Builder()
        .url(String.format(URL, authentication.getTenantId()))
        .header(CONTENT_TYPE, X_WWW_FORM_URLENCODED)
        .post(formBody)
        .build();
  }

  private String getAccessToken(final Request request) {
    try (Response execute = okHttpClient.newCall(request).execute()) {
      if (execute.isSuccessful()) {
        return ObjectMapperSupplier.objectMapper()
            .readTree(execute.body().string())
            .get(ACCESS_TOKEN)
            .asText();
      } else {
        throw new RuntimeException(execute.message());
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error while parse refresh token response", e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class DelegateAuthenticationProvider implements IAuthenticationProvider {
    private final String token;

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
