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
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.CreateChannelResult;
import io.camunda.connector.appintegrations.model.MessageContent;
import io.camunda.connector.appintegrations.model.Recipient;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.http.client.model.auth.OAuthAuthentication;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Talks to the App Integrations backend: resolves the effective configuration from the runtime
 * environment, builds the request payloads and performs the authenticated HTTP calls. Keeping this
 * out of {@link AppIntegrationsConnector} leaves the connector itself a thin SPI entry point, in
 * line with other connectors (e.g. the HTTP connector's {@code HttpService}).
 *
 * <p>The backend is Camunda-operated infrastructure in both SaaS and Self-Managed, so its URL and
 * credentials come from environment variables rather than from the element template — nothing about
 * the connection is part of the process model.
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
  private static final String API_KEY_ENV_VAR = "APP_INTEGRATIONS_API_KEY";
  private static final String OAUTH_TOKEN_ENDPOINT_ENV_VAR =
      "APP_INTEGRATIONS_OAUTH_TOKEN_ENDPOINT";
  private static final String OAUTH_CLIENT_ID_ENV_VAR = "APP_INTEGRATIONS_OAUTH_CLIENT_ID";
  private static final String OAUTH_CLIENT_SECRET_ENV_VAR = "APP_INTEGRATIONS_OAUTH_CLIENT_SECRET";
  private static final String OAUTH_AUDIENCE_ENV_VAR = "APP_INTEGRATIONS_OAUTH_AUDIENCE";
  private static final String OAUTH_SCOPES_ENV_VAR = "APP_INTEGRATIONS_OAUTH_SCOPES";
  private static final String OAUTH_CLIENT_AUTHENTICATION_ENV_VAR =
      "APP_INTEGRATIONS_OAUTH_CLIENT_AUTHENTICATION";

  static final String NOT_CONFIGURED_ERROR_CODE = "APP_INTEGRATIONS_NOT_CONFIGURED";

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
    return post(
        SEND_MESSAGE_PATH, messagePayload(request, formResourceKey), SendMessageResult.class);
  }

  CreateChannelResult createChannel(CreateChannelRequest request) {
    var payload =
        new CreateChannelPayload(
            request.teamId(),
            request.displayName(),
            request.description(),
            request.membershipType());
    return post(CREATE_CHANNEL_PATH, payload, CreateChannelResult.class);
  }

  /**
   * Flattens the switchable recipient and content onto the backend's flat wire contract. Absent
   * fields stay null so {@code @JsonInclude(NON_NULL)} omits them; empty candidate lists are
   * normalised to null rather than sent as empty arrays.
   */
  private MessagePayload messagePayload(SendMessageRequest request, String formResourceKey) {
    String email = null;
    List<String> candidateUsers = null;
    List<String> candidateGroups = null;
    String channelId = null;
    switch (request.recipient()) {
      case Recipient.CamundaRecipient camunda -> {
        email = blankToNull(camunda.email());
        candidateUsers = emptyToNull(camunda.candidateUsers());
        candidateGroups = emptyToNull(camunda.candidateGroups());
      }
      case Recipient.TeamsRecipient teams -> channelId = blankToNull(teams.channelId());
    }

    String message = null;
    String adaptiveCardJson = null;
    switch (request.content()) {
      case MessageContent.TextContent text -> message = text.message();
      case MessageContent.AdaptiveCardContent card -> adaptiveCardJson = card.adaptiveCardJson();
      case MessageContent.FormContent ignored -> {
        // The form travels as formResourceKey, resolved from the job's linked resources.
      }
    }

    return new MessagePayload(
        email,
        channelId,
        candidateUsers,
        candidateGroups,
        message,
        adaptiveCardJson,
        formResourceKey);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static List<String> emptyToNull(List<String> value) {
    return value == null || value.isEmpty() ? null : value;
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
  private <T> T post(String path, Object payload, Class<T> resultType) {
    var config = resolveConfig();
    var body = serialize(payload);

    HttpResponse<String> response;
    try {
      response = send(config, path, body);
    } catch (ConnectorException e) {
      if ("401".equals(e.getErrorCode()) && config.oauth() != null) {
        LOGGER.debug("Received 401 from {}; invalidating OAuth token and retrying", path);
        OAuthTokenCacheHolder.get().invalidate(config.oauth());
        response = send(config, path, body);
      } else {
        throw e;
      }
    }

    LOGGER.debug("POST {} → {}", path, response.status());
    return deserialize(response.entity(), resultType);
  }

  /**
   * Resolves the backend URL and credentials from the environment. OAuth wins when fully configured
   * (this is what SaaS injects); otherwise an API key is used. With neither, the connector is not
   * usable on this runtime and the job fails without retrying — see {@link #notConfigured}.
   */
  private EffectiveConfig resolveConfig() {
    var baseUrl = env(BASE_URL_ENV_VAR);
    if (baseUrl == null) {
      throw notConfigured("set " + BASE_URL_ENV_VAR);
    }

    var tokenEndpoint = env(OAUTH_TOKEN_ENDPOINT_ENV_VAR);
    var clientId = env(OAUTH_CLIENT_ID_ENV_VAR);
    var clientSecret = env(OAUTH_CLIENT_SECRET_ENV_VAR);
    if (tokenEndpoint != null && clientId != null && clientSecret != null) {
      var clientAuthentication = env(OAUTH_CLIENT_AUTHENTICATION_ENV_VAR);
      return new EffectiveConfig(
          baseUrl,
          null,
          new OAuthAuthentication(
              tokenEndpoint,
              clientId,
              clientSecret,
              env(OAUTH_AUDIENCE_ENV_VAR),
              clientAuthentication == null ? OAuthConstants.CREDENTIALS_BODY : clientAuthentication,
              env(OAUTH_SCOPES_ENV_VAR)));
    }

    var apiKey = env(API_KEY_ENV_VAR);
    if (apiKey != null) {
      return new EffectiveConfig(baseUrl, apiKey, null);
    }

    // A partially configured OAuth block is more likely a deployment mistake than an intent to use
    // an API key, so name the missing OAuth variables rather than the generic alternatives.
    if (tokenEndpoint != null || clientId != null || clientSecret != null) {
      List<String> missing = new ArrayList<>();
      if (tokenEndpoint == null) {
        missing.add(OAUTH_TOKEN_ENDPOINT_ENV_VAR);
      }
      if (clientId == null) {
        missing.add(OAUTH_CLIENT_ID_ENV_VAR);
      }
      if (clientSecret == null) {
        missing.add(OAUTH_CLIENT_SECRET_ENV_VAR);
      }
      throw notConfigured("OAuth is partially configured, also set " + String.join(" + ", missing));
    }
    throw notConfigured(
        "set "
            + API_KEY_ENV_VAR
            + ", or "
            + OAUTH_TOKEN_ENDPOINT_ENV_VAR
            + " + "
            + OAUTH_CLIENT_ID_ENV_VAR
            + " + "
            + OAUTH_CLIENT_SECRET_ENV_VAR);
  }

  /**
   * Fails the job immediately, raising an incident without retrying: no number of retries can
   * supply a missing environment variable, and a plain {@link ConnectorException} would first burn
   * the element template's retries at its configured backoff.
   *
   * <p>{@code detail} names the missing variables so the incident is actionable. It must never
   * carry their values — the API key and client secret are credentials, and incident messages are
   * visible in Operate.
   */
  private ConnectorRetryException notConfigured(String detail) {
    return ConnectorRetryException.builder()
        .errorCode(NOT_CONFIGURED_ERROR_CODE)
        .message("App Integrations are not configured on this connector runtime: " + detail)
        .retries(0)
        .build();
  }

  /** Reads an environment variable, treating blank as absent. */
  private String env(String name) {
    var value = getenv.apply(name);
    return value == null || value.isBlank() ? null : value;
  }

  private boolean isSaaS() {
    return getenv.apply(SAAS_ENV_VAR) != null;
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

  private HttpResponse<String> send(EffectiveConfig config, String path, String body) {
    var headers = new HashMap<String, String>();
    headers.put("Content-Type", "application/json");
    if (config.apiKey() != null) {
      headers.put(API_KEY_HEADER, config.apiKey());
    }
    applyContextHeaders(headers);

    var request = new HttpClientRequest();
    request.setMethod(HttpMethod.POST);
    request.setUrl(config.baseUrl().replaceAll("/+$", "") + path);
    request.setHeaders(headers);
    request.setBody(body);
    request.setConnectionTimeoutInSeconds(REQUEST_TIMEOUT_SECONDS);
    request.setReadTimeoutInSeconds(REQUEST_TIMEOUT_SECONDS);
    // OAuth is delegated to the SDK HttpClient: execute() fetches, caches and attaches the
    // client-credentials token (shared OAuth token cache), the same way the HTTP connector does.
    if (config.oauth() != null) {
      request.setAuthentication(config.oauth());
    }

    return httpClient.execute(request, ResponseMappers.asString());
  }

  /**
   * Attaches the SaaS context-identification headers ({@code X-Org-Id}, {@code X-Cluster-Id}) when
   * running in SaaS and the corresponding values are available, so the backend can attribute the
   * call to the originating organization/cluster. A runtime without them is a valid Self-Managed
   * runtime, so these are not part of the not-configured check.
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

  /** Exactly one of {@code apiKey} / {@code oauth} is non-null. */
  private record EffectiveConfig(String baseUrl, String apiKey, OAuthAuthentication oauth) {}

  @JsonInclude(Include.NON_NULL)
  private record MessagePayload(
      String email,
      String channelId,
      List<String> candidateUsers,
      List<String> candidateGroups,
      String message,
      String adaptiveCardJson,
      String formResourceKey) {}

  @JsonInclude(Include.NON_NULL)
  private record CreateChannelPayload(
      String teamId, String displayName, String description, String membershipType) {}
}
