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
import io.camunda.connector.impl.feel.FeelEngineWrapperException;
import io.camunda.connector.inbound.model.JWTProperties;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JWTChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(JWTChecker.class);

  public static boolean verify(
      final JWTProperties jwtProperties,
      final JwkProvider jwkProvider,
      final ObjectMapper objectMapper) {
    Optional<DecodedJWT> decodedJWT = getDecodedVerifiedJWT(jwtProperties, jwkProvider);
    if (decodedJWT.isEmpty()) {
      return false;
    }
    List<String> roles = extractRoles(jwtProperties, decodedJWT.get(), objectMapper);
    if (!roles.containsAll(jwtProperties.requiredPermissions())) {
      LOGGER.debug("JWT authorization failed");
      return false;
    }
    LOGGER.debug("JWT authorization was successful");
    return true;
  }

  private static Optional<DecodedJWT> getDecodedVerifiedJWT(
      JWTProperties jwtProperties, JwkProvider jwkProvider) {
    final String jwtToken =
        JWTChecker.extractJWTFomHeader(jwtProperties.headers())
            .orElseThrow(() -> new RuntimeException("Cannot extract JWT from header!"));
    try {
      return Optional.of(JWTChecker.verifyJWT(jwtToken, jwkProvider));
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
      return jwtProperties.jwtRoleExpression().apply(jsonNode);
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
        Optional.ofNullable(JWT.decode(jwtToken))
            .map(
                decodedJWT -> {
                  try {
                    return jwkProvider.get(decodedJWT.getKeyId());
                  } catch (JwkException e) {
                    LOGGER.warn("Cannot find JWK for the JWT token: " + e.getMessage());
                    throw new RuntimeException(e);
                  }
                })
            .map(
                jwk -> {
                  try {
                    return JWT.require(getAlgorithm(jwk)).build();
                  } catch (InvalidPublicKeyException e) {
                    LOGGER.warn("Token verification failed: " + e.getMessage());
                    throw new RuntimeException(e);
                  }
                })
            .map(jwtVerifier -> jwtVerifier.verify(jwtToken))
            .orElseThrow(() -> new RuntimeException("Cannot decode jwtToken!"));
    LOGGER.debug("Token verified successfully!");
    return verifiedJWT;
  }

  private static Algorithm getAlgorithm(Jwk jwk) throws InvalidPublicKeyException {
    switch (jwk.getAlgorithm()) {
      case "RS256":
        return Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey());
      case "RS384":
        return Algorithm.RSA384((RSAPublicKey) jwk.getPublicKey());
      case "RS512":
        return Algorithm.RSA512((RSAPublicKey) jwk.getPublicKey());
      case "ES256":
        return Algorithm.ECDSA256((ECPublicKey) jwk.getPublicKey(), null);
      case "ES384":
        return Algorithm.ECDSA384((ECPublicKey) jwk.getPublicKey(), null);
      case "ES512":
        return Algorithm.ECDSA512((ECPublicKey) jwk.getPublicKey(), null);
      default:
        throw new RuntimeException("Unknown algorithm!");
    }
  }
}
