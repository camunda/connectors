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
package io.camunda.connector.http.client.model.auth;

import io.camunda.connector.http.client.authentication.OAuthConstants;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public record OAuthRefreshTokenAuthentication(
    String oauthTokenEndpoint,
    String clientId,
    String clientSecret,
    String refreshToken,
    String scopes)
    implements HttpAuthentication {

  public static final String TYPE = "oauth-refresh-token";
  public static final String GRANT_TYPE = "refresh_token";

  public Map<String, String> getDataForAuthRequestBody() {
    Map<String, String> data = new HashMap<>();
    data.put(OAuthConstants.GRANT_TYPE, GRANT_TYPE);
    data.put(OAuthConstants.CLIENT_ID, clientId());
    data.put(OAuthConstants.REFRESH_TOKEN, refreshToken());
    if (StringUtils.isNotBlank(clientSecret())) {
      data.put(OAuthConstants.CLIENT_SECRET, clientSecret());
    }
    if (StringUtils.isNotBlank(scopes())) {
      data.put(OAuthConstants.SCOPE, scopes());
    }
    return data;
  }

  @Override
  public String toString() {
    return "OAuthRefreshTokenAuthentication{"
        + "oauthTokenEndpoint='"
        + oauthTokenEndpoint
        + "'"
        + ", clientId='"
        + clientId
        + "'"
        + ", clientSecret=[REDACTED]"
        + ", refreshToken=[REDACTED]"
        + ", scopes='"
        + scopes
        + "'"
        + "}";
  }
}
