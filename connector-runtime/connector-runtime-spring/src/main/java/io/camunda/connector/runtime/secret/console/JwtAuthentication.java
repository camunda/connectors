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
package io.camunda.connector.runtime.secret.console;

import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;

public class JwtAuthentication implements Authentication {
  private final JwtCredential jwtCredential;
  private final TokenResponseMapper tokenResponseMapper;
  private String token;
  private LocalDateTime timeout;

  public JwtAuthentication(JwtCredential jwtCredential, TokenResponseMapper tokenResponseMapper) {
    this.jwtCredential = jwtCredential;
    this.tokenResponseMapper = tokenResponseMapper;
  }

  @Override
  public Map<String, String> getTokenHeader() {
    if (token == null || timeout == null || timeout.isBefore(LocalDateTime.now())) {
      TokenResponse response = retrieveToken();
      token = response.getAccessToken();
      timeout = LocalDateTime.now().plusSeconds(response.getExpiresIn()).minusSeconds(30);
    }
    return Map.of("Authorization", "Bearer " + token);
  }

  @Override
  public void resetToken() {
    this.token = null;
    this.timeout = null;
  }

  private TokenResponse retrieveToken() {
    try (CloseableHttpClient client = HttpClients.createSystem()) {
      HttpPost request = buildRequest();
      return client.execute(
          request,
          response -> {
            try {
              return tokenResponseMapper.readToken(EntityUtils.toString(response.getEntity()));
            } catch (Exception e) {
              var errorMessage =
                  String.format(
                      """
              Token retrieval failed from: %s
              Response code: %s
              Audience: %s
              """,
                      jwtCredential.authUrl(), response.getCode(), jwtCredential.audience());
              throw new RuntimeException(errorMessage, e);
            }
          });
    } catch (Exception e) {
      throw new RuntimeException("Authenticating for Operate failed due to " + e.getMessage(), e);
    }
  }

  private HttpPost buildRequest() throws URISyntaxException {
    HttpPost httpPost = new HttpPost(jwtCredential.authUrl().toURI());
    httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
    List<NameValuePair> formParams = new ArrayList<>();
    formParams.add(new BasicNameValuePair("grant_type", "client_credentials"));
    formParams.add(new BasicNameValuePair("client_id", jwtCredential.clientId()));
    formParams.add(new BasicNameValuePair("client_secret", jwtCredential.clientSecret()));
    formParams.add(new BasicNameValuePair("audience", jwtCredential.audience()));
    if (jwtCredential.scope() != null && !jwtCredential.scope().isEmpty()) {
      formParams.add(new BasicNameValuePair("scope", jwtCredential.scope()));
    }
    httpPost.setEntity(new UrlEncodedFormEntity(formParams));
    return httpPost;
  }
}
