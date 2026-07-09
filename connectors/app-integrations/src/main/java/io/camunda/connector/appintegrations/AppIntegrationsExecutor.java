/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.appintegrations.model.AppIntegrationsConfiguration;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.CreateChannelResult;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.appintegrations.model.auth.ApiKeyAuthentication;
import io.camunda.connector.appintegrations.model.auth.AppIntegrationsAuthentication;
import io.camunda.connector.appintegrations.model.auth.OAuthAuthentication;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Talks to the App Integrations backend: resolves the effective configuration (self-managed
 * template vs. SaaS environment), builds the request payloads and performs the authenticated HTTP
 * calls. Keeping this out of {@link AppIntegrationsConnector} leaves the connector itself a thin
 * SPI entry point, in line with other connectors (e.g. the HTTP connector's {@code HttpService}).
 *
 * <p>All HTTP calls go through the connector SDK's {@link HttpClient}, so the connector shares the
 * SDK's transport (timeouts, proxy support, TLS). OAuth is delegated to the client as well: setting
 * the authentication on the request makes {@code execute()} fetch, cache (shared OAuth token cache)
 * and attach the client-credentials token, rather than maintaining a second HTTP stack or
 * re-implementing token acquisition.
 */
class AppIntegrationsExecutor {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppIntegrationsExecutor.class);

  private static final String SEND_MESSAGE_PATH = "/api/connector/message";
  private static final String CREATE_CHANNEL_PATH = "/api/connector/channel";
  private static final int REQUEST_TIMEOUT_SECONDS = 30;

  private static final String SAAS_ENV_VAR = "CAMUNDA_CONNECTOR_RUNTIME_SAAS";
  private static final String ORG_ID_ENV_VAR = "CAMUNDA_CONNECTOR_CLOUD_ORGANIZATION_ID";
  private static final String CLUSTER_ID_ENV_VAR = "CAMUNDA_CLIENT_CLOUD_CLUSTERID";

  private static final String ORG_ID_HEADER = "X-Org-Id";
  private static final String CLUSTER_ID_HEADER = "X-Cluster-Id";
  private static final String API_KEY_HEADER = "X-API-KEY";

  private static final String BASE_URL_ENV_VAR = "APP_INTEGRATIONS_BASE_URL";
  private static final String OAUTH_TOKEN_ENDPOINT_ENV_VAR =
      "APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT";
  private static final String OAUTH_CLIENT_ID_ENV_VAR = "APP_INTEGRATIONS_OAUTH_CLIENT_ID";
  private static final String OAUTH_CLIENT_SECRET_ENV_VAR = "APP_INTEGRATIONS_OAUTH_CLIENT_SECRET";
  private static final String OAUTH_AUDIENCE_ENV_VAR = "APP_INTEGRATIONS_OAUTH_AUDIENCE";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;
  private final UnaryOperator<String> getenv;

  AppIntegrationsExecutor(
      ObjectMapper objectMapper, HttpClient httpClient, UnaryOperator<String> getenv) {
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
    this.getenv = getenv;
  }

  SendMessageResult sendMessage(SendMessageRequest request, String formResourceKey) {
    var payload =
        new MessagePayload(
            request.email(),
            request.channelId(),
            request.message(),
            request.adaptiveCardJson(),
            formResourceKey);
    return post(request.configuration(), SEND_MESSAGE_PATH, payload, SendMessageResult.class);
  }

  CreateChannelResult createChannel(CreateChannelRequest request) {
    var payload =
        new CreateChannelPayload(
            request.teamId(),
            request.displayName(),
            request.description(),
            request.membershipType());
    return post(request.configuration(), CREATE_CHANNEL_PATH, payload, CreateChannelResult.class);
  }

  /**
   * Sends a POST to the App Integrations backend, authenticating via the configured mechanism.
   *
   * <p>The SDK {@link HttpClient} throws a {@link ConnectorException} (with the HTTP status code as
   * its error code) for any response status {@code >= 400}, so success is implied when {@link
   * #send} returns. On a {@code 401} from an OAuth-authenticated request the cached token may be
   * stale or revoked: it is invalidated and the call is retried once with a freshly fetched token.
   * Any other failure — or a second {@code 401} on the retry — propagates to the caller.
   */
  private <T> T post(
      AppIntegrationsConfiguration config, String path, Object payload, Class<T> resultType) {
    var effective = resolveConfig(config);
    var baseUrl = effective.baseUrl();
    var auth = effective.authentication();
    var body = serialize(payload);

    HttpResponse<String> response;
    try {
      response = send(baseUrl, path, body, auth);
    } catch (ConnectorException e) {
      if ("401".equals(e.getErrorCode()) && auth instanceof OAuthAuthentication oauth) {
        LOGGER.debug("Received 401 from {}; invalidating OAuth token and retrying", path);
        OAuthTokenCacheHolder.get().invalidate(toClientAuthentication(oauth));
        response = send(baseUrl, path, body, auth);
      } else {
        throw e;
      }
    }

    LOGGER.debug("POST {} → {}", path, response.status());
    return deserialize(response.entity(), resultType);
  }

  private AppIntegrationsConfiguration resolveConfig(AppIntegrationsConfiguration config) {
    if (isSaaS()) {
      return new AppIntegrationsConfiguration(requireEnv(BASE_URL_ENV_VAR), oauthFromEnv());
    }
    var baseUrl = config == null ? null : config.baseUrl();
    var auth = config == null ? null : config.authentication();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new ConnectorException("VALIDATION_ERROR", "Base URL is required");
    }
    if (auth == null) {
      throw new ConnectorException("VALIDATION_ERROR", "Authentication is required");
    }
    return new AppIntegrationsConfiguration(baseUrl, auth);
  }

  private boolean isSaaS() {
    return getenv.apply(SAAS_ENV_VAR) != null;
  }

  private OAuthAuthentication oauthFromEnv() {
    return new OAuthAuthentication(
        requireEnv(OAUTH_TOKEN_ENDPOINT_ENV_VAR),
        requireEnv(OAUTH_CLIENT_ID_ENV_VAR),
        requireEnv(OAUTH_CLIENT_SECRET_ENV_VAR),
        requireEnv(OAUTH_AUDIENCE_ENV_VAR),
        OAuthConstants.CREDENTIALS_BODY,
        null);
  }

  private String requireEnv(String name) {
    var value = getenv.apply(name);
    if (value == null || value.isBlank()) {
      throw new ConnectorException(
          "VALIDATION_ERROR", "Missing required environment variable: " + name);
    }
    return value;
  }

  private String serialize(Object payload) {
    try {
      return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "IO_ERROR", "Failed to serialize App Integrations payload: " + e.getMessage(), e);
    }
  }

  private <T> T deserialize(String responseBody, Class<T> resultType) {
    if (responseBody == null || responseBody.isBlank()) {
      // Successful ack with no body (e.g. 204) — nothing to deserialize.
      return null;
    }
    try {
      return objectMapper.readValue(responseBody, resultType);
    } catch (JsonProcessingException e) {
      throw new ConnectorException(
          "IO_ERROR", "Failed to parse App Integrations response: " + e.getMessage(), e);
    }
  }

  private HttpResponse<String> send(
      String baseUrl, String path, String body, AppIntegrationsAuthentication auth) {
    var headers = new HashMap<String, String>();
    headers.put("Content-Type", "application/json");
    if (auth instanceof ApiKeyAuthentication apiKey) {
      headers.put(API_KEY_HEADER, apiKey.apiKey());
    }
    applyContextHeaders(headers);

    var request = new HttpClientRequest();
    request.setMethod(HttpMethod.POST);
    request.setUrl(baseUrl.replaceAll("/+$", "") + path);
    request.setHeaders(headers);
    request.setBody(body);
    request.setConnectionTimeoutInSeconds(REQUEST_TIMEOUT_SECONDS);
    request.setReadTimeoutInSeconds(REQUEST_TIMEOUT_SECONDS);
    // OAuth is delegated to the SDK HttpClient: execute() fetches, caches and attaches the
    // client-credentials token (shared OAuth token cache), the same way the HTTP connector does.
    if (auth instanceof OAuthAuthentication oauth) {
      request.setAuthentication(toClientAuthentication(oauth));
    }

    return httpClient.execute(request, ResponseMappers.asString());
  }

  /**
   * Maps the connector's OAuth model onto the HTTP client SDK model. Set on the request, it lets
   * the SDK HttpClient fetch, cache and attach the client-credentials token; it is also the key
   * used to invalidate the cached token on a 401.
   */
  private io.camunda.connector.http.client.model.auth.OAuthAuthentication toClientAuthentication(
      OAuthAuthentication oauth) {
    return new io.camunda.connector.http.client.model.auth.OAuthAuthentication(
        oauth.oauthTokenEndpoint(),
        oauth.clientId(),
        oauth.clientSecret(),
        oauth.audience(),
        oauth.clientAuthentication(),
        oauth.scopes());
  }

  /**
   * Attaches the SaaS context-identification headers ({@code X-Org-Id}, {@code X-Cluster-Id}) when
   * running in SaaS and the corresponding values are available, so the backend can attribute the
   * call to the originating organization/cluster.
   */
  private void applyContextHeaders(Map<String, String> headers) {
    if (!isSaaS()) {
      return;
    }
    var orgId = getenv.apply(ORG_ID_ENV_VAR);
    var clusterId = getenv.apply(CLUSTER_ID_ENV_VAR);
    if (orgId != null && !orgId.isBlank() && !"null".equals(orgId)) {
      headers.put(ORG_ID_HEADER, orgId);
    }
    if (clusterId != null && !clusterId.isBlank()) {
      headers.put(CLUSTER_ID_HEADER, clusterId);
    }
  }

  @JsonInclude(Include.NON_NULL)
  private record MessagePayload(
      String email,
      String channelId,
      String message,
      String adaptiveCardJson,
      String formResourceKey) {}

  @JsonInclude(Include.NON_NULL)
  private record CreateChannelPayload(
      String teamId, String displayName, String description, String membershipType) {}
}
