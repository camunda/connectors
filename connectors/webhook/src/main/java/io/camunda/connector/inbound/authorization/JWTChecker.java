/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.auth0.jwt.interfaces.Verification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapperException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JWTChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(JWTChecker.class);

  public static boolean verify(
      final WebhookProcessingPayload payload,
      final WebhookConnectorProperties webhookConnectorProperties,
      final JwkProvider jwkProvider,
      final ObjectMapper objectMapper)
      throws JsonProcessingException {
    final String jwtToken = JWTChecker.extractJWTFomHeader(payload);
    DecodedJWT verifiedJWT = JWTChecker.verifyJWT(jwtToken, jwkProvider);

    if (verifiedJWT == null) {
      return false;
    }
    // parse this as json and expose this FEEL to extract roles, and test that against the
    // expression
    byte[] decodedBytes = Base64.getDecoder().decode(verifiedJWT.getPayload());
    String decodedPayload = new String(decodedBytes);
    JsonNode jsonNode = objectMapper.readTree(decodedPayload);
    // FeelParserWrapper.parseIfIsFeelExpressionOrGetOriginal(jsonNode);
    FeelEngineWrapper feelEngine = new FeelEngineWrapper();
    List<String> roles = new ArrayList<>();
    try {
      roles = feelEngine.evaluate(webhookConnectorProperties.getJwtRolePath(), jsonNode);
    } catch (FeelEngineWrapperException ex) {
      LOGGER.warn("Failed to evaluate FEEL expression! Reason: " + ex.getReason());
    }
    if (!roles.containsAll(webhookConnectorProperties.getRequiredPermissions())) {
      return false;
    }
    return true;
  }

  public static String extractJWTFomHeader(final WebhookProcessingPayload payload) {
    final String jwtToken = payload.headers().get("Authorization");
    if (jwtToken == null) {
      return null;
    }
    return jwtToken.replace("Bearer", "").trim();
  }

  public static DecodedJWT verifyJWT(String jwtToken, JwkProvider jwkProvider) {
    try {
      DecodedJWT decodedJWT = JWT.decode(jwtToken);

      Jwk jwk = jwkProvider.get(decodedJWT.getKeyId());
      RSAPublicKey publicKey =
          (RSAPublicKey)
              jwk.getPublicKey(); // TODO: RSAPublicKey assumes that we use RS256 encoding, but it
      // could be a million other types

      Verification verification = JWT.require(Algorithm.RSA256(publicKey));

      JWTVerifier jwtVerifier = verification.build();
      DecodedJWT verifiedJWT = jwtVerifier.verify(jwtToken);
      LOGGER.debug("Token verified successfully!");

      return verifiedJWT;

    } catch (JWTVerificationException e) {
      // Token is not valid or expired
      LOGGER.warn("Token verification failed: " + e.getMessage());
      return null;
    } catch (JwkException e) {
      throw new RuntimeException(e);
    }
  }
}
