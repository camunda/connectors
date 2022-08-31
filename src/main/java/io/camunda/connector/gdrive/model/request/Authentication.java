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
package io.camunda.connector.gdrive.model.request;

import com.google.api.client.util.Key;
import com.google.api.services.drive.DriveScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import io.camunda.connector.api.ConnectorInput;
import io.camunda.connector.api.SecretStore;
import io.camunda.connector.api.Validator;
import java.util.Objects;

public class Authentication implements ConnectorInput {

  @Key private AuthenticationType authType;
  @Key private String bearerToken;
  @Key private String oauthClientId;
  @Key private String oauthClientSecret;
  @Key private String oauthRefreshToken;

  @Override
  public void validateWith(final Validator validator) {
    validator.require(authType, "Authentication type");

    if (authType == AuthenticationType.BEARER) {
      validator.require(bearerToken, "Bearer token");
    }

    if (authType == AuthenticationType.REFRESH) {
      validator.require(oauthClientId, "OAuth client ID");
      validator.require(oauthClientSecret, "OAuth client secret");
      validator.require(oauthRefreshToken, "OAuth Refresh token");
    }
  }

  @Override
  public void replaceSecrets(final SecretStore secretStore) {
    bearerToken = secretStore.replaceSecret(bearerToken);
    oauthClientId = secretStore.replaceSecret(oauthClientId);
    oauthClientSecret = secretStore.replaceSecret(oauthClientSecret);
    oauthRefreshToken = secretStore.replaceSecret(oauthRefreshToken);
  }

  public GoogleCredentials fetchCredentials() {
    if (authType == AuthenticationType.BEARER) {
      AccessToken accessToken = new AccessToken(bearerToken, null);
      return new GoogleCredentials(accessToken).createScoped(DriveScopes.DRIVE);
    }

    if (authType == AuthenticationType.REFRESH) {
      return UserCredentials.newBuilder()
          .setClientId(oauthClientId)
          .setClientSecret(oauthClientSecret)
          .setRefreshToken(oauthRefreshToken)
          .build();
    }

    throw new RuntimeException("Unsupported authentication type");
  }

  public AuthenticationType getAuthType() {
    return authType;
  }

  public void setAuthType(AuthenticationType authType) {
    this.authType = authType;
  }

  public String getBearerToken() {
    return bearerToken;
  }

  public void setBearerToken(String bearerToken) {
    this.bearerToken = bearerToken;
  }

  public String getOauthClientId() {
    return oauthClientId;
  }

  public void setOauthClientId(String oauthClientId) {
    this.oauthClientId = oauthClientId;
  }

  public String getOauthClientSecret() {
    return oauthClientSecret;
  }

  public void setOauthClientSecret(String oauthClientSecret) {
    this.oauthClientSecret = oauthClientSecret;
  }

  public String getOauthRefreshToken() {
    return oauthRefreshToken;
  }

  public void setOauthRefreshToken(String oauthRefreshToken) {
    this.oauthRefreshToken = oauthRefreshToken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Authentication that = (Authentication) o;
    return Objects.equals(authType, that.authType)
        && Objects.equals(bearerToken, that.bearerToken)
        && Objects.equals(oauthClientId, that.oauthClientId)
        && Objects.equals(oauthClientSecret, that.oauthClientSecret)
        && Objects.equals(oauthRefreshToken, that.oauthRefreshToken);
  }

  @Override
  public int hashCode() {
    return Objects.hash(authType, bearerToken, oauthClientId, oauthClientSecret, oauthRefreshToken);
  }

  @Override
  public String toString() {
    return "Authentication{" + "authType='" + authType + '\'' + '}';
  }
}
