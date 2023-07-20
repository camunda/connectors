/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.google.model;

import com.google.api.services.drive.DriveScopes;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class Authentication {

  @NotNull private AuthenticationType authType;
  private String bearerToken;
  private String oauthClientId;
  private String oauthClientSecret;
  private String oauthRefreshToken;

  @AssertTrue
  private boolean isHasAuthData() {
    if (authType == AuthenticationType.BEARER) {
      return bearerToken != null;
    } else if (authType == AuthenticationType.REFRESH) {
      return oauthClientId != null && oauthClientSecret != null && oauthRefreshToken != null;
    } else {
      return false;
    }
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
