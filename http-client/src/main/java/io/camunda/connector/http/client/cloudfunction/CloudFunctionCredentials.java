/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.http.client.cloudfunction;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CloudFunctionCredentials {

  private static final Logger LOGGER = LoggerFactory.getLogger(CloudFunctionCredentials.class);

  private final CloudFunctionCredentialsCache cache;

  public CloudFunctionCredentials() {
    this(new CloudFunctionCredentialsCache());
  }

  CloudFunctionCredentials(CloudFunctionCredentialsCache cache) {
    this.cache = cache;
  }

  private String getAccessTokenValue(OAuth2Credentials credentials) {
    return credentials.getAccessToken().getTokenValue();
  }

  private OAuth2Credentials initializeCredentials(String proxyUrl) {
    try {
      IdTokenProvider idTokenProvider = createIdTokenProvider();
      return createIdTokenCredentials(proxyUrl, idTokenProvider);
    } catch (Exception ex) {
      LOGGER.warn("Could not wire OAuth for proxy, not using OAuth", ex);
      throw new RuntimeException("Could not wire OAuth for proxy, not using OAuth", ex);
    }
  }

  public String getOAuthToken(String proxyUrl) {
    return getAccessTokenValue(cache.get(() -> initializeCredentials(proxyUrl)));
  }

  private IdTokenProvider createIdTokenProvider() {
    // Searches credentials via GOOGLE_APPLICATION_CREDENTIALS
    // See
    // https://cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.GoogleCredentials#com_google_auth_oauth2_GoogleCredentials_getApplicationDefault__
    try {
      final var googleCredentials = GoogleCredentials.getApplicationDefault();
      if (!(googleCredentials instanceof IdTokenProvider)) {
        throw new RuntimeException("Google Credentials are not an instance of IdTokenProvider.");
      }
      return (IdTokenProvider) googleCredentials;
    } catch (IOException e) {
      throw new RuntimeException(
          "Could not get Google Credentials using GOOGLE_APPLICATION_CREDENTIALS", e);
    }
  }

  private IdTokenCredentials createIdTokenCredentials(
      final String url, final IdTokenProvider idTokenProvider) {
    return IdTokenCredentials.newBuilder()
        .setIdTokenProvider(idTokenProvider)
        .setTargetAudience(url)
        .build();
  }
}
