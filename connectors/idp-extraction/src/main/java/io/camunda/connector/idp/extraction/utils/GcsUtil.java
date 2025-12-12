/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.utils;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class GcsUtil {

  public static GoogleCredentials getCredentials(
      GcpAuthenticationType authType,
      String bearerToken,
      String serviceAccountJson,
      String oauthClientId,
      String oauthClientSecret,
      String oauthRefreshToken) {
    if (authType == GcpAuthenticationType.BEARER) {
      AccessToken accessToken = new AccessToken(bearerToken, null);
      return GoogleCredentials.create(accessToken);
    } else if (authType == GcpAuthenticationType.SERVICE_ACCOUNT) {
      ByteArrayInputStream credentialsStream =
          new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8));
      try {
        return GoogleCredentials.fromStream(credentialsStream);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return UserCredentials.newBuilder()
        .setClientId(oauthClientId)
        .setClientSecret(oauthClientSecret)
        .setRefreshToken(oauthRefreshToken)
        .build();
  }
}
