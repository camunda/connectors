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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.appintegrations.model.AppIntegrationsConfiguration;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.CreateChannelResult;
import io.camunda.connector.appintegrations.model.LinkedResource;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.appintegrations.model.auth.ApiKeyAuthentication;
import io.camunda.connector.appintegrations.model.auth.AppIntegrationsAuthentication;
import io.camunda.connector.appintegrations.model.auth.OAuthAuthentication;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.http.client.authentication.OAuthTokenCacheHolder;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.mapper.HttpResponse;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(name = "App Integrations Connector", type = "io.camunda:app-integrations")
@ElementTemplate(
    id = "io.camunda.connectors.AppIntegrations.v1",
    name = "App Integrations Connector",
    version = 1,
    description = "Send notifications and manage channels via Microsoft Teams",
    keywords = {
      "teams",
      "microsoft teams",
      "send message",
      "notification",
      "channel",
      "adaptive card"
    },
    icon = "icon.svg",
    engineVersion = "^8.8",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "authentication", label = "Authentication"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message"),
      @ElementTemplate.PropertyGroup(id = "form", label = "Form"),
      @ElementTemplate.PropertyGroup(id = "channel", label = "Channel")
    })
public class AppIntegrationsConnector implements OutboundConnectorProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppIntegrationsConnector.class);
  private static final String SEND_MESSAGE_PATH = "/api/connector/message";
  private static final String CREATE_CHANNEL_PATH = "/api/connector/channel";
  private static final int REQUEST_TIMEOUT_SECONDS = 30;

  private static final String SAAS_ENV_VAR = "CAMUNDA_CONNECTOR_RUNTIME_SAAS";
  private static final String ORG_ID_ENV_VAR = "CAMUNDA_CONNECTOR_CLOUD_ORGANIZATION_ID";
  private static final String CLUSTER_ID_ENV_VAR = "CAMUNDA_CLIENT_CLOUD_CLUSTERID";

  private static final String ORG_ID_HEADER = "X-Org-Id";
  private static final String CLUSTER_ID_HEADER = "X-Cluster-Id";
  private static final String API_KEY_HEADER = "X-API-KEY";

  // All HTTP calls go through the connector SDK's HttpClient (CustomApacheHttpClient), so the
  // connector shares the SDK's transport (timeouts, proxy support, TLS). OAuth is delegated to the
  // client as well: setting the authentication on the request makes execute() fetch, cache (shared
  // OAuth token cache) and attach the client-credentials token, rather than maintaining a second
  // HTTP stack or re-implementing token acquisition.
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public AppIntegrationsConnector() {
    this.objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    this.httpClient = new CustomApacheHttpClient();
  }

  AppIntegrationsConnector(ObjectMapper objectMapper, HttpClient httpClient) {
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Operation(id = "sendMessage", name = "Send Message")
  public SendMessageResult sendMessage(
      @Variable SendMessageRequest request, OutboundConnectorContext context) {
    LOGGER.debug("Sending message via App Integrations connector");

    var linkedResources = parseLinkedResources(context.getJobContext().getCustomHeaders());
    var formResourceKey =
        linkedResources.stream()
            .filter(r -> "form".equalsIgnoreCase(r.resourceType()))
            .map(LinkedResource::resourceKey)
            .findFirst()
            .orElse(null);

    LOGGER.debug(
        "linkedResources: {} form entries, formResourceKey={}",
        linkedResources.size(),
        formResourceKey);

    boolean hasContent =
        (request.message() != null && !request.message().isBlank())
            || (request.adaptiveCardJson() != null && !request.adaptiveCardJson().isBlank())
            || formResourceKey != null;
    if (!hasContent) {
      throw new ConnectorException(
          "VALIDATION_ERROR",
          "One of 'message', 'adaptiveCardJson', or a linked form must be provided");
    }

    var payload =
        new MessagePayload(
            request.email(),
            request.channelId(),
            request.message(),
            request.adaptiveCardJson(),
            formResourceKey);

    return post(request.configuration(), SEND_MESSAGE_PATH, payload, SendMessageResult.class);
  }

  @Operation(id = "createChannel", name = "Create Channel")
  public CreateChannelResult createChannel(@Variable CreateChannelRequest request) {
    LOGGER.debug("Creating Teams channel via App Integrations connector");

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
    var body = serialize(payload);
    var auth = config.authentication();

    HttpResponse<String> response;
    try {
      response = send(config.baseUrl(), path, body, auth);
    } catch (ConnectorException e) {
      if ("401".equals(e.getErrorCode()) && auth instanceof OAuthAuthentication oauth) {
        LOGGER.debug("Received 401 from {}; invalidating OAuth token and retrying", path);
        OAuthTokenCacheHolder.get().invalidate(toClientAuthentication(oauth));
        response = send(config.baseUrl(), path, body, auth);
      } else {
        throw e;
      }
    }

    LOGGER.debug("POST {} → {}", path, response.status());
    return deserialize(response.entity(), resultType);
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
    if (!System.getenv().containsKey(SAAS_ENV_VAR)) {
      return;
    }
    var orgId = System.getenv(ORG_ID_ENV_VAR);
    var clusterId = System.getenv(CLUSTER_ID_ENV_VAR);
    if (orgId != null && !orgId.isBlank()) {
      headers.put(ORG_ID_HEADER, orgId);
    }
    if (clusterId != null && !clusterId.isBlank()) {
      headers.put(CLUSTER_ID_HEADER, clusterId);
    }
  }

  /**
   * Parses the {@code linkedResources} custom header (JSON array) from the activated job. Returns
   * an empty list if the header is absent or malformed.
   */
  private List<LinkedResource> parseLinkedResources(Map<String, String> headers) {
    var raw = headers.get("linkedResources");
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<List<LinkedResource>>() {});
    } catch (IOException e) {
      LOGGER.warn("Failed to parse linkedResources header, treating as empty: {}", e.getMessage());
      return List.of();
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
