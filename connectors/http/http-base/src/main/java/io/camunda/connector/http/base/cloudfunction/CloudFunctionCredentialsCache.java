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
package io.camunda.connector.http.base.cloudfunction;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import java.util.Date;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudFunctionCredentialsCache {

  public static final int MAX_ATTEMPTS = 3;
  private static final Logger LOG = LoggerFactory.getLogger(CloudFunctionCredentialsCache.class);
  private OAuth2Credentials credentials;

  /**
   * Get credentials from cache or fetch new credentials if cache is empty or expired.
   *
   * @param credentialsSupplier Supplier to fetch new credentials
   * @return Optional of credentials
   */
  public synchronized OAuth2Credentials get(Supplier<OAuth2Credentials> credentialsSupplier) {
    return getWithRetries(credentialsSupplier, MAX_ATTEMPTS);
  }

  private OAuth2Credentials getWithRetries(
      Supplier<OAuth2Credentials> credentialsSupplier, int attempts) {
    if (credentials == null) {
      LOG.debug("Credentials cache is empty, fetching new credentials");
      credentials = credentialsSupplier.get();
    }

    try {
      refreshAccessTokenIfExpired(credentials);
    } catch (IOException e) {
      if (attempts > 1) {
        LOG.warn("Failed to refresh access token, retrying... Attempts left: {}", attempts - 1);
        return getWithRetries(credentialsSupplier, attempts - 1);
      } else {
        LOG.error("Failed to refresh access token after retries", e);
        throw new RuntimeException("Failed to refresh access token after retries", e);
      }
    }
    return credentials;
  }

  private void refreshAccessTokenIfExpired(OAuth2Credentials credentials) throws IOException {
    AccessToken accessToken = credentials.getAccessToken();
    if (accessToken == null || hasExpired(accessToken)) {
      // Credentials are not initialized before calling refreshIfExpired
      // See OAuth2Credentials#getAccessToken for more details
      if (accessToken != null) {
        LOG.debug("Access token expired, refreshing");
      }
      credentials.refreshIfExpired();
    }
  }

  private boolean hasExpired(AccessToken accessToken) {
    return accessToken.getExpirationTime() != null
        && accessToken.getExpirationTime().before(new Date());
  }
}
