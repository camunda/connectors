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

import static io.camunda.connector.runtime.core.intrinsic.functions.CreateGithubAppJwtFunction.DEFAULT_EXPIRATION_SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CreateGithubAppJwtFunctionTest {

  private final CreateGithubAppJwtFunction function = new CreateGithubAppJwtFunction();

  @Test
  void shouldCreateJwtWithClientId() throws Exception {
    // Generate a test RSA key pair
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

    // Convert to PEM format
    String privateKeyPem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(privateKey.getEncoded())
            + "\n-----END PRIVATE KEY-----";

    // Create JWT with clientId
    String clientId = "123456";
    String jwt = function.execute(privateKeyPem, clientId);

    // Verify JWT was created
    assertNotNull(jwt);
    assertThat(jwt).isNotEmpty();
    assertThat(jwt.split("\\.")).hasSize(3); // JWT has 3 parts

    // Decode and verify claims
    DecodedJWT decoded = JWT.decode(jwt);
    assertThat(decoded.getIssuer()).isEqualTo(clientId);
    assertThat(decoded.getExpiresAt()).isNotNull();
    assertThat(decoded.getIssuedAt()).isNotNull();

    // Verify expiration is exactly 1 minute (60 seconds) from issued time
    long expirationDiff = decoded.getExpiresAt().getTime() - decoded.getIssuedAt().getTime();
    assertThat(expirationDiff).isEqualTo(DEFAULT_EXPIRATION_SECONDS * 1000);
  }

  @Test
  void shouldCreateJwtWithDefaultExpiration() throws Exception {
    // Generate a test RSA key pair
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

    // Convert to PEM format
    String privateKeyPem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(privateKey.getEncoded())
            + "\n-----END PRIVATE KEY-----";

    String jwt = function.execute(privateKeyPem, "test-client");

    // Verify JWT was created with default expiration (10 minutes)
    assertNotNull(jwt);
    DecodedJWT decoded = JWT.decode(jwt);
    assertThat(decoded.getExpiresAt()).isNotNull();
    assertThat(decoded.getIssuedAt()).isNotNull();
    assertThat(decoded.getIssuer()).isEqualTo("test-client");

    // Verify expiration is exactly 1 minute (60 seconds) from issued time
    long expirationDiff = decoded.getExpiresAt().getTime() - decoded.getIssuedAt().getTime();
    assertThat(expirationDiff).isEqualTo(DEFAULT_EXPIRATION_SECONDS * 1000);
  }
}
