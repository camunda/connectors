/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.cloudfunction;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudFunctionHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudFunctionHelper.class);

  private CloudFunctionHelper() {}

  private static OAuth2Credentials initializeCredentials(String proxyUrl) {
    if (proxyUrl == null) {
      return null;
    }
    // Statically try to initialize
    try {
      IdTokenProvider idTokenProvider = createIdTokenProvider();
      return createIdTokenCredentials(proxyUrl, idTokenProvider);
    } catch (Exception ex) {
      // and run without OAuth if not provided properly
      LOGGER.warn("Could not wire OAuth for proxy, not using OAuth", ex);
      return null;
    }
  }

  public static String getOAuthToken(String proxyUrl) throws IOException {
    OAuth2Credentials credentials = initializeCredentials(proxyUrl);
    if (credentials != null) {
      credentials.refreshIfExpired();
      return credentials.getAccessToken().getTokenValue();
    }
    return null;
  }

  private static IdTokenProvider createIdTokenProvider() throws IOException {
    // Searches credentials via GOOGLE_APPLICATION_CREDENTIALS
    // See
    // https://cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.GoogleCredentials#com_google_auth_oauth2_GoogleCredentials_getApplicationDefault__
    final var googleCredentials = GoogleCredentials.getApplicationDefault();
    if (!(googleCredentials instanceof IdTokenProvider)) {
      throw new IOException("Google Credentials are not an instance of IdTokenProvider.");
    }
    return (IdTokenProvider) googleCredentials;
  }

  private static IdTokenCredentials createIdTokenCredentials(
      final String url, final IdTokenProvider idTokenProvider) {
    return IdTokenCredentials.newBuilder()
        .setIdTokenProvider(idTokenProvider)
        .setTargetAudience(url)
        .build();
  }
}
