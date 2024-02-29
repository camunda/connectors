/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.supplier.util;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import java.io.IOException;
import java.security.GeneralSecurityException;

public final class GoogleServiceSupplierUtil {

  private GoogleServiceSupplierUtil() {}

  public static HttpCredentialsAdapter getHttpHttpCredentialsAdapter(final Authentication auth) {
    Credentials creds = null;
    if (auth.authType() == AuthenticationType.BEARER) {
      AccessToken accessToken = new AccessToken(auth.bearerToken(), null);
      creds = new GoogleCredentials(accessToken).createScoped(DriveScopes.DRIVE);
    }

    if (auth.authType() == AuthenticationType.REFRESH) {
      creds =
          UserCredentials.newBuilder()
              .setClientId(auth.oauthClientId())
              .setClientSecret(auth.oauthClientSecret())
              .setRefreshToken(auth.oauthRefreshToken())
              .build();
    }

    return new HttpCredentialsAdapter(creds);
  }

  public static NetHttpTransport getNetHttpTransport() {
    try {
      return GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("An error occurred while creating the HTTP_TRANSPORT", e);
    }
  }
}
