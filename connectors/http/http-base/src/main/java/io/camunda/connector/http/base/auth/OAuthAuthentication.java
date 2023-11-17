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
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.DropdownPropertyChoice;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import io.camunda.connector.http.base.constants.Constants;
import jakarta.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@TemplateSubType(id = OAuthAuthentication.TYPE, label = "OAuth 2.0")
public final class OAuthAuthentication implements Authentication {
  @TemplateProperty(ignore = true)
  private final String grantType = "client_credentials";

  @FEEL
  @NotEmpty
  @TemplateProperty(group = "authentication", description = "The OAuth token endpoint")
  private String oauthTokenEndpoint;

  @FEEL
  @NotEmpty
  @TemplateProperty(
      group = "authentication",
      description = "Your application's client ID from the OAuth client")
  private String clientId;

  @FEEL
  @NotEmpty
  @TemplateProperty(
      group = "authentication",
      description = "Your application's client secret from the OAuth client")
  private String clientSecret;

  @FEEL
  @TemplateProperty(
      group = "authentication",
      description = "The unique identifier of the target API you want to access",
      optional = true)
  private String audience;

  @FEEL
  @NotEmpty
  @TemplateProperty(
      group = "authentication",
      type = PropertyType.Dropdown,
      choices = {
        @DropdownPropertyChoice(
            value = Constants.CREDENTIALS_BODY,
            label = "Send client credentials in body"),
        @DropdownPropertyChoice(
            value = Constants.BASIC_AUTH_HEADER,
            label = "Send as Basic Auth header")
      },
      description =
          "Send client ID and client secret as Basic Auth request in the header, or as client credentials in the request body")
  private String clientAuthentication;

  @TemplateProperty(
      group = "authentication",
      description = "The scopes which you want to request authorization for (e.g.read:contacts)",
      optional = true)
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

  @TemplateProperty(ignore = true)
  public static final String TYPE = "oauth-client-credentials-flow";
}
