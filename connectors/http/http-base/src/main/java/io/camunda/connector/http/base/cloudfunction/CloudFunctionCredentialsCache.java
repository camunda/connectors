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

import com.google.auth.oauth2.OAuth2Credentials;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudFunctionCredentialsCache {

  private static final Logger LOG = LoggerFactory.getLogger(CloudFunctionCredentialsCache.class);
  private OAuth2Credentials credentials;

  /**
   * Get credentials from cache or fetch new credentials if cache is empty or expired.
   *
   * @param credentialsSupplier Supplier to fetch new credentials
   * @return Optional of credentials
   */
  public Optional<OAuth2Credentials> get(Supplier<OAuth2Credentials> credentialsSupplier) {
    if (credentials == null) {
      LOG.debug("Credentials cache is empty, fetching new credentials");
      credentials = credentialsSupplier.get();
    }

    try {
      refreshAccessTokenIfExpired();
    } catch (Exception e) {
      LOG.warn("Failed to refresh access token", e);
      this.credentials = credentialsSupplier.get();
    }
    return Optional.ofNullable(credentials);
  }

  private void refreshAccessTokenIfExpired() throws IOException {
    Date expirationTime = credentials.getAccessToken().getExpirationTime();
    if (expirationTime != null && expirationTime.before(new Date())) {
      LOG.debug("Access token expired, refreshing");
      credentials.refreshIfExpired();
    }
  }
}
