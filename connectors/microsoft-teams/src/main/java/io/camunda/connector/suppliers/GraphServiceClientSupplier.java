/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.suppliers;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.model.authentication.BearerAuthentication;
import io.camunda.connector.model.authentication.ClientSecretAuthentication;
import io.camunda.connector.model.authentication.MSTeamsAuthentication;
import io.camunda.connector.model.authentication.RefreshTokenAuthentication;
import java.io.IOException;
import java.time.OffsetDateTime;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

public class GraphServiceClientSupplier {

  private static final String URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
  private static final String CLIENT_ID = "client_id";
  private static final String GRANT_TYPE = "grant_type";
  private static final String REFRESH_TOKEN = "refresh_token";
  private static final String CLIENT_SECRET = "client_secret";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

  private final OkHttpClient okHttpClient;

  public GraphServiceClientSupplier() {
    this.okHttpClient = new OkHttpClient();
  }

  public GraphServiceClientSupplier(final OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

  public GraphServiceClient buildAndGetGraphServiceClient(
      final ClientSecretAuthentication authentication) {

    ClientSecretCredential credential =
        new ClientSecretCredentialBuilder()
            .tenantId(authentication.tenantId())
            .clientId(authentication.clientId())
            .clientSecret(authentication.clientSecret())
            .build();

    return new GraphServiceClient(credential, DEFAULT_SCOPE);
  }

  public GraphServiceClient buildAndGetGraphServiceClient(
      final RefreshTokenAuthentication authentication) {
    return buildAndGetGraphServiceClient(getAccessToken(buildRequest(authentication)));
  }

  public GraphServiceClient buildAndGetGraphServiceClient(
      final BearerAuthentication bearerAuthentication) {
    return new GraphServiceClient(
        new DelegateAuthenticationProvider(bearerAuthentication.token()), DEFAULT_SCOPE);
  }

  public GraphServiceClient buildAndGetGraphServiceClient(final String token) {
    return new GraphServiceClient((new DelegateAuthenticationProvider(token)), DEFAULT_SCOPE);
  }

  @NotNull
  private Request buildRequest(final RefreshTokenAuthentication authentication) {

    FormBody.Builder formBodyBuilder =
        new FormBody.Builder()
            .add(CLIENT_ID, authentication.clientId())
            .add(GRANT_TYPE, REFRESH_TOKEN)
            .add(REFRESH_TOKEN, authentication.token());
    if (StringUtils.isNoneBlank(authentication.clientSecret())) {
      formBodyBuilder.add(CLIENT_SECRET, authentication.clientSecret());
    }
    return new Request.Builder()
        .url(String.format(URL, authentication.tenantId()))
        .header(CONTENT_TYPE, X_WWW_FORM_URLENCODED)
        .post(formBodyBuilder.build())
        .build();
  }

  private String getAccessToken(final Request request) {
    try (Response response = okHttpClient.newCall(request).execute()) {
      if (response.isSuccessful()) {
        return ObjectMapperSupplier.objectMapper()
            .readTree(response.body().string())
            .get(ACCESS_TOKEN)
            .asText();
      } else {
        throw new RuntimeException(response.message());
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error while parse refresh token response", e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public GraphServiceClient buildAndGetGraphServiceClient(
      final MSTeamsAuthentication authentication) {
    if (authentication instanceof ClientSecretAuthentication clientSecretAuthentication) {
      return buildAndGetGraphServiceClient(clientSecretAuthentication);
    } else if (authentication instanceof RefreshTokenAuthentication refreshTokenAuthentication) {
      return buildAndGetGraphServiceClient(refreshTokenAuthentication);
    } else if (authentication instanceof BearerAuthentication bearerAuthentication) {
      return buildAndGetGraphServiceClient(bearerAuthentication);
    }
    return null;
  }

  public static class DelegateAuthenticationProvider implements TokenCredential {
    private final String token;

    public DelegateAuthenticationProvider(String token) {
      this.token = token;
    }

    @Override
    public Mono<AccessToken> getToken(final TokenRequestContext tokenRequestContext) {
      return Mono.just(new AccessToken(token, OffsetDateTime.MAX));
    }

    @Override
    public AccessToken getTokenSync(final TokenRequestContext request) {
      return new AccessToken(token, OffsetDateTime.MAX);
    }
  }
}
