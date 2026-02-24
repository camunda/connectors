/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.microsoft.common.auth;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.time.OffsetDateTime;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Mono;

/**
 * Builds {@link GraphServiceClient} instances for different Microsoft authentication types. This
 * class is shared between Microsoft connectors (Teams, Email Inbound, etc.) to avoid duplicating
 * OAuth token exchange logic.
 */
public class GraphServiceClientSupplier {

  private static final String TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";
  private static final String CLIENT_ID = "client_id";
  private static final String GRANT_TYPE = "grant_type";
  private static final String REFRESH_TOKEN = "refresh_token";
  private static final String CLIENT_SECRET = "client_secret";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
  private static final String ACCESS_TOKEN = "access_token";
  private static final String DEFAULT_SCOPE = "https://graph.microsoft.com/.default";

  private static final ObjectMapper OBJECT_MAPPER = ConnectorsObjectMapperSupplier.getCopy();

  private final OkHttpClient okHttpClient;

  public GraphServiceClientSupplier() {
    this.okHttpClient = new OkHttpClient();
  }

  public GraphServiceClientSupplier(final OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

  public GraphServiceClient buildAndGetGraphServiceClient(
      final MicrosoftAuthentication authentication) {
    return switch (authentication) {
      case ClientCredentialsAuthentication clientCreds -> {
        ClientSecretCredential credential =
            new ClientSecretCredentialBuilder()
                .tenantId(clientCreds.tenantId())
                .clientId(clientCreds.clientId())
                .clientSecret(clientCreds.clientSecret())
                .build();
        yield new GraphServiceClient(credential, DEFAULT_SCOPE);
      }
      case RefreshTokenAuthentication refreshToken ->
          buildAndGetGraphServiceClient(getAccessToken(buildRequest(refreshToken)));
      case BearerAuthentication bearer -> buildAndGetGraphServiceClient(bearer.token());
    };
  }

  public GraphServiceClient buildAndGetGraphServiceClient(final String token) {
    return new GraphServiceClient(new DelegateAuthenticationProvider(token), DEFAULT_SCOPE);
  }

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
        .url(String.format(TOKEN_URL, authentication.tenantId()))
        .header(CONTENT_TYPE, X_WWW_FORM_URLENCODED)
        .post(formBodyBuilder.build())
        .build();
  }

  private String getAccessToken(final Request request) {
    try (Response response = okHttpClient.newCall(request).execute()) {
      if (response.isSuccessful() && response.body() != null) {
        JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body().string());
        if (jsonNode.has(ACCESS_TOKEN)) {
          return jsonNode.get(ACCESS_TOKEN).asText();
        } else {
          throw new RuntimeException("Access token not found in the response");
        }
      } else {
        String responseBody =
            response.body() != null ? response.body().string() : "no response body";
        throw new RuntimeException(
            "Failed to fetch access token. Verify authentication details. "
                + "Note: Client secret is optional, depending on the client's privacy status. Status code: "
                + response.code()
                + ", response: "
                + responseBody);
      }
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error while parsing refresh token response", e);
    } catch (IOException e) {
      throw new RuntimeException("Network error occurred", e);
    }
  }

  /**
   * A {@link TokenCredential} implementation that wraps a pre-obtained access token. Used for
   * Bearer token and Refresh token authentication types where the token is obtained externally.
   */
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
