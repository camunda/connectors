/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.framework.langchain4j.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.mcp.client.model.auth.OAuthAuthentication;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.http.client.authentication.OAuthConstants;
import io.camunda.connector.http.client.authentication.OAuthService;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OAuthHeadersSupplier implements Supplier<Map<String, String>> {

  private static final Logger LOGGER = LoggerFactory.getLogger(OAuthHeadersSupplier.class);

  public static final Duration DEFAULT_EXPIRY = Duration.ofMinutes(5);
  public static final String ERROR_CODE_INVALID_OAUTH_RESPONSE = "INVALID_OAUTH_RESPONSE";

  private final OAuthService oAuthService;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final OAuthAuthentication config;

  private TokenResponse tokenResponse;

  public OAuthHeadersSupplier(
      OAuthService oAuthService,
      HttpClient httpClient,
      ObjectMapper objectMapper,
      OAuthAuthentication config) {
    this.oAuthService = oAuthService;
    this.httpClient = httpClient;
    this.objectMapper = objectMapper;
    this.config = config;
  }

  @Override
  public Map<String, String> get() {
    if (tokenResponse == null || tokenResponse.isExpired()) {
      LOGGER.debug(
          "Fetching MCP client OAuth token from token endpoint: {}", config.oauthTokenEndpoint());
      tokenResponse = fetchOAuthToken();
      LOGGER.debug("Successfully fetched MCP client OAuth token");
    }

    return Map.of("Authorization", "Bearer " + tokenResponse.accessToken());
  }

  private TokenResponse fetchOAuthToken() {
    HttpClientRequest oAuthRequest =
        oAuthService.createOAuthRequestFrom(
            new io.camunda.connector.http.client.model.auth.OAuthAuthentication(
                config.oauthTokenEndpoint(),
                config.clientId(),
                config.clientSecret(),
                config.audience(),
                config.clientAuthentication().oauthConstant(),
                config.scopes()));

    try {
      return httpClient.execute(oAuthRequest, this::extractTokenFromResponse).entity();
    } catch (ConnectorException e) {
      throw new ConnectorException(
          e.getErrorCode(),
          "MCP client authentication failed: %s. Body: %s"
              .formatted(e.getMessage(), getErrorResponseBody(e)),
          e);
    }
  }

  private TokenResponse extractTokenFromResponse(StreamingHttpResponse body) {
    final var jsonNode = ResponseMappers.asJsonNode(() -> objectMapper).apply(body);
    if (jsonNode == null || !jsonNode.isObject()) {
      final var nodeType = Optional.ofNullable(jsonNode).map(JsonNode::getNodeType).orElse(null);

      throw new ConnectorException(
          ERROR_CODE_INVALID_OAUTH_RESPONSE,
          "Invalid OAuth token response. Expected a JSON object, but received %s"
              .formatted(nodeType));
    }

    final var accessToken =
        Optional.ofNullable(jsonNode.findValue(OAuthConstants.ACCESS_TOKEN))
            .map(JsonNode::asText)
            .filter(t -> !t.isBlank())
            .orElseThrow(
                () ->
                    new ConnectorException(
                        ERROR_CODE_INVALID_OAUTH_RESPONSE,
                        "Invalid OAuth token response. Missing '%s' field."
                            .formatted(OAuthConstants.ACCESS_TOKEN)));

    final var expiresIn =
        Optional.ofNullable(jsonNode.findValue(OAuthConstants.EXPIRES_IN))
            .map(JsonNode::asLong)
            .map(Duration::ofSeconds)
            .orElse(DEFAULT_EXPIRY);

    return new TokenResponse(accessToken, Instant.now().plus(expiresIn));
  }

  private Object getErrorResponseBody(ConnectorException exception) {
    Object response = exception.getErrorVariables().get("response");
    if (response instanceof Map<?, ?> responseMap) {
      return responseMap.get("body");
    }

    return null;
  }

  private record TokenResponse(String accessToken, Instant expiresAt) {
    boolean isExpired() {
      return Instant.now().isAfter(expiresAt);
    }

    @Override
    public String toString() {
      return new ToStringBuilder(this)
          .append("accessToken", "REDACTED")
          .append("expiresAt", expiresAt)
          .toString();
    }
  }
}
