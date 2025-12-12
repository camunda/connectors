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
package io.camunda.connector.runtime.core.intrinsic.functions;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.http.client.mapper.ResponseMappers;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import io.camunda.connector.http.client.model.auth.BearerAuthentication;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionProvider;
import java.io.IOException;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;

public class CreateGithubAppInstallationTokenFunction implements IntrinsicFunctionProvider {

  // This is the default expiration time for the JWT, not the installation token.
  // GitHub documentation specifies a maximum of 1 minute.
  static final long JWT_EXPIRATION_SECONDS = 60L;
  private static final String RSA_ALGORITHM = "RSA";
  private static final String GITHUB_API_BASE_URL = "https://api.github.com";
  private static final String INSTALLATION_TOKEN_URL_FORMAT =
      GITHUB_API_BASE_URL + "/app/installations/%s/access_tokens";
  private static final String HEADER_ACCEPT = "Accept";
  private static final String GITHUB_API_VERSION_ACCEPT_HEADER = "application/vnd.github.v3+json";
  public static final String RESPONSE_TOKEN_FIELD = "token";

  private final ObjectMapper objectMapper = new ObjectMapper();
  HttpClient HTTP_CLIENT = new CustomApacheHttpClient();

  static {
    Security.addProvider(new BouncyCastleProvider());
  }

  @IntrinsicFunction(name = "createGithubAppInstallationToken")
  public String execute(String privateKey, String appId, String installationId) {

    try {
      RSAPrivateKey rsaPrivateKey = parsePrivateKey(privateKey);
      Instant now = Instant.now();

      // Build JWT with RS256 algorithm
      Algorithm algorithm = Algorithm.RSA256(rsaPrivateKey);
      var jwt =
          JWT.create()
              .withIssuer(appId)
              .withIssuedAt(Date.from(now))
              .withExpiresAt(now.plusSeconds(JWT_EXPIRATION_SECONDS))
              .sign(algorithm);

      final String url = String.format(INSTALLATION_TOKEN_URL_FORMAT, installationId);
      var httpClientRequest = createInstallationAccessTokenRequest(jwt, url);
      var response =
          HTTP_CLIENT.execute(
              httpClientRequest, ResponseMappers.asObject(() -> objectMapper, Map.class));

      return (String) response.entity().get(RESPONSE_TOKEN_FIELD);
    } catch (Exception e) {
      throw new RuntimeException("Failed to create JWT: " + e.getMessage(), e);
    }
  }

  private HttpClientRequest createInstallationAccessTokenRequest(String jwt, String url) {
    var httpClientRequest = new HttpClientRequest();
    httpClientRequest.setMethod(HttpMethod.POST);
    httpClientRequest.setAuthentication(new BearerAuthentication(jwt));
    httpClientRequest.setUrl(url);
    httpClientRequest.setHeaders(Map.of(HEADER_ACCEPT, GITHUB_API_VERSION_ACCEPT_HEADER));
    return httpClientRequest;
  }

  private RSAPrivateKey parsePrivateKey(String privateKey)
      throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    try (PemReader pemReader = new PemReader(new StringReader(privateKey))) {
      PemObject pemObject = pemReader.readPemObject();
      byte[] content = pemObject.getContent();
      PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(content);
      KeyFactory kf = KeyFactory.getInstance(RSA_ALGORITHM);
      return (RSAPrivateKey) kf.generatePrivate(spec);
    }
  }
}
