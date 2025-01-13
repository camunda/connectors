/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.exceptions.SignatureVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.Forbidden;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.model.JWTProperties;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class JWTAuthHandler extends WebhookAuthorizationHandler<JwtAuth> {

  private static final Logger LOGGER = LoggerFactory.getLogger(JWTAuthHandler.class);
  private static final AuthorizationResult JWT_AUTH_FAILED_RESULT =
      new InvalidCredentials("JWT auth failed");
  private static final AuthorizationResult JWT_AUTH_MISSING_PERMISSIONS_RESULT =
      new Forbidden("Missing required permissions");
  private final JwkProvider jwkProvider;
  private final ObjectMapper objectMapper;

  public JWTAuthHandler(JwtAuth authorization, JwkProvider jwkProvider, ObjectMapper objectMapper) {
    super(authorization);
    this.jwkProvider = jwkProvider;
    this.objectMapper = objectMapper;
  }

  private static Optional<DecodedJWT> getDecodedVerifiedJWT(
      Map<String, String> headers, JwkProvider jwkProvider) {
    final String jwtToken =
        JWTAuthHandler.extractJWTFomHeader(headers)
            .orElseThrow(() -> new RuntimeException("Cannot extract JWT from header!"));
    try {
      return Optional.of(JWTAuthHandler.verifyJWT(jwtToken, jwkProvider));
    } catch (JWTDecodeException ex) {
      LOGGER.warn("Failed to decode JWT token! Cause: " + ex.getCause());
      return Optional.empty();
    } catch (SignatureVerificationException ex) {
      LOGGER.warn("Failed to verify JWT token! Cause: " + ex.getCause());
      return Optional.empty();
    } catch (TokenExpiredException ex) {
      LOGGER.warn("JWT token expired! Cause: " + ex.getCause());
      return Optional.empty();
    }
  }

  private static List<String> extractRoles(
      JWTProperties jwtProperties, DecodedJWT verifiedJWT, ObjectMapper objectMapper) {
    try {
      JsonNode jsonNode = getJsonPayloadFromToken(verifiedJWT, objectMapper);
      return jwtProperties.permissionsExpression().apply(jsonNode);
    } catch (FeelEngineWrapperException ex) {
      LOGGER.warn("Failed to evaluate FEEL expression! Reason: " + ex.getReason());
      return new ArrayList<>();
    }
  }

  private static JsonNode getJsonPayloadFromToken(
      DecodedJWT verifiedJWT, ObjectMapper objectMapper) {
    return Optional.ofNullable(verifiedJWT.getPayload())
        .map(payload -> Base64.getDecoder().decode(payload))
        .map(String::new)
        .map(
            decodedPayload -> {
              try {
                return objectMapper.readTree(decodedPayload);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .orElseThrow(() -> new RuntimeException("JWT payload is null!"));
  }

  private static Optional<String> extractJWTFomHeader(final Map<String, String> headers) {
    return Optional.ofNullable(
            Optional.ofNullable(headers.get("Authorization")).orElse(headers.get("authorization")))
        .map(authorizationHeader -> authorizationHeader.replace("Bearer", "").trim());
  }

  private static DecodedJWT verifyJWT(String jwtToken, JwkProvider jwkProvider)
      throws SignatureVerificationException, TokenExpiredException {
    DecodedJWT verifiedJWT =
        Optional.of(JWT.decode(jwtToken))
            .map(
                decodedJWT -> {
                  try {
                    Jwk jwk = jwkProvider.get(decodedJWT.getKeyId());
                    return JWT.require(
                            getAlgorithm(
                                Optional.ofNullable(jwk.getAlgorithm())
                                    .orElse(decodedJWT.getAlgorithm()),
                                jwk.getPublicKey()))
                        .build();
                  } catch (InvalidPublicKeyException e) {
                    LOGGER.warn("Token verification failed: " + e.getMessage());
                    throw new RuntimeException(e);
                  } catch (JwkException e) {
                    LOGGER.warn("Cannot find JWK for the JWT token: " + e.getMessage());
                    throw new RuntimeException(e);
                  }
                })
            .map(jwtVerifier -> jwtVerifier.verify(jwtToken))
            .orElseThrow(() -> new RuntimeException("Cannot decode jwtToken!"));
    LOGGER.debug("Token verified successfully!");
    return verifiedJWT;
  }

  private static Algorithm getAlgorithm(String algorithm, PublicKey publicKey)
      throws InvalidPublicKeyException {
    return switch (algorithm) {
      case "RS256" -> Algorithm.RSA256((RSAPublicKey) publicKey);
      case "RS384" -> Algorithm.RSA384((RSAPublicKey) publicKey);
      case "RS512" -> Algorithm.RSA512((RSAPublicKey) publicKey);
      case "ES256" -> Algorithm.ECDSA256((ECPublicKey) publicKey, null);
      case "ES384" -> Algorithm.ECDSA384((ECPublicKey) publicKey, null);
      case "ES512" -> Algorithm.ECDSA512((ECPublicKey) publicKey, null);
      default -> throw new RuntimeException("Unknown algorithm: " + algorithm);
    };
  }

  @Override
  public AuthorizationResult checkAuthorization(WebhookProcessingPayload payload) {

    JWTProperties jwtProperties = expectedAuthorization.jwt();
    Map<String, String> headers = payload.headers();

    Optional<DecodedJWT> decodedJWT = getDecodedVerifiedJWT(headers, jwkProvider);
    if (decodedJWT.isEmpty()) {
      return JWT_AUTH_FAILED_RESULT;
    }
    if (jwtProperties.requiredPermissions() != null
        && !jwtProperties.requiredPermissions().isEmpty()) {

      List<String> roles = extractRoles(jwtProperties, decodedJWT.get(), objectMapper);
      if (!roles.containsAll(jwtProperties.requiredPermissions())) {
        LOGGER.debug("JWT auth failed");
        return JWT_AUTH_MISSING_PERMISSIONS_RESULT;
      }
    }
    LOGGER.debug("JWT auth was successful");
    return Success.INSTANCE;
  }
}
