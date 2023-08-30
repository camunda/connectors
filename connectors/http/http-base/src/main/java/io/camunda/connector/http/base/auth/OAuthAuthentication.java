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
package io.camunda.connector.http.base.auth;

import com.google.api.client.http.HttpHeaders;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.http.base.constants.Constants;
import jakarta.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class OAuthAuthentication extends Authentication {
  private final String grantType = "client_credentials";
  @FEEL @NotEmpty private String oauthTokenEndpoint;
  @FEEL @NotEmpty private String clientId;
  @FEEL @NotEmpty private String clientSecret;
  @FEEL private String audience;
  @FEEL @NotEmpty private String clientAuthentication;

  private String scopes;

  public Map<String, String> getDataForAuthRequestBody() {
    Map<String, String> data = new HashMap<>();
    data.put(Constants.GRANT_TYPE, this.getGrantType());
    data.put(Constants.AUDIENCE, this.getAudience());
    data.put(Constants.SCOPE, this.getScopes());

    if (Constants.CREDENTIALS_BODY.equals(this.getClientAuthentication())) {
      data.put(Constants.CLIENT_ID, this.getClientId());
      data.put(Constants.CLIENT_SECRET, this.getClientSecret());
    }
    return data;
  }

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
