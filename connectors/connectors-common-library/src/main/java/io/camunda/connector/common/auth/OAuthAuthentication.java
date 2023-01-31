/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.common.auth;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.api.annotation.Secret;
import java.util.Objects;
import javax.validation.constraints.NotEmpty;

public class OAuthAuthentication extends Authentication {
  private final String grantType = "client_credentials";
  @NotEmpty @Secret private String oauthTokenEndpoint;
  @NotEmpty @Secret private String clientId;
  @NotEmpty @Secret private String clientSecret;
  @Secret private String audience;
  @NotEmpty private String clientAuthentication;

  private String scopes;

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public String getGrantType() {
    return grantType;
  }

  public String getOauthTokenEndpoint() {
    return oauthTokenEndpoint;
  }

  public void setOauthTokenEndpoint(String oauthTokenEndpoint) {
    this.oauthTokenEndpoint = oauthTokenEndpoint;
  }

  public String getScopes() {
    return scopes;
  }

  public void setScopes(String scopes) {
    this.scopes = scopes;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(String audience) {
    this.audience = audience;
  }

  public String getClientAuthentication() {
    return clientAuthentication;
  }

  public void setClientAuthentication(String clientAuthentication) {
    this.clientAuthentication = clientAuthentication;
  }

  @Override
  public void setHeaders(final HttpHeaders headers) {}

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    OAuthAuthentication that = (OAuthAuthentication) o;
    return oauthTokenEndpoint.equals(that.oauthTokenEndpoint)
        && clientId.equals(that.clientId)
        && clientSecret.equals(that.clientSecret)
        && audience.equals(that.audience)
        && Objects.equals(grantType, that.grantType)
        && clientAuthentication.equals(that.clientAuthentication)
        && Objects.equals(scopes, that.scopes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        oauthTokenEndpoint,
        clientId,
        clientSecret,
        audience,
        grantType,
        clientAuthentication,
        scopes);
  }

  @Override
  public String toString() {
    return "OAuthAuthentication{"
        + "grantType='"
        + grantType
        + '\''
        + ", oauthTokenEndpoint='"
        + oauthTokenEndpoint
        + '\''
        + ", audience='"
        + audience
        + '\''
        + ", clientAuthentication='"
        + clientAuthentication
        + '\''
        + ", scopes='"
        + scopes
        + '\''
        + "} "
        + super.toString();
  }
}
