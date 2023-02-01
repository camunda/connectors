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
package io.camunda.connector.http.auth;

import com.google.api.client.http.HttpRequest;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProxyOAuthHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProxyOAuthHelper.class);

  private ProxyOAuthHelper() {}

  public static OAuth2Credentials initializeCredentials(String proxyUrl) {
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

  public static void addOauthHeaders(HttpRequest request, OAuth2Credentials credentials)
      throws IOException {
    if (credentials != null) {
      credentials.refreshIfExpired();
      request
          .getHeaders()
          .setAuthorization("Bearer " + credentials.getAccessToken().getTokenValue());
    }
  }

  private static IdTokenProvider createIdTokenProvider() throws IOException {
    // Searches credentials via GOOGLE_APPLICATION_CREDENTIALS
    // See
    // https://cloud.google.com/java/docs/reference/google-auth-library/latest/com.google.auth.oauth2.GoogleCredentials
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
