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
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunction;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionProvider;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

public class CreateJwtFunction implements IntrinsicFunctionProvider {

  // Default expiration time of 10 minutes
  private static final long DEFAULT_EXPIRATION_SECONDS = 600L;

  @IntrinsicFunction(name = "createJwt")
  public String execute(String privateKey, String clientId) {

    try {
      RSAPrivateKey rsaPrivateKey = parsePrivateKey(privateKey);

      // Calculate time values - use seconds since epoch for precision
      Instant now = Instant.now();
      long issuedAt = now.getEpochSecond();
      long expiresAt = issuedAt + DEFAULT_EXPIRATION_SECONDS;

      // Build JWT with RS256 algorithm
      Algorithm algorithm = Algorithm.RSA256(null, rsaPrivateKey);
      return JWT.create()
          .withIssuer(clientId)
          .withIssuedAt(Date.from(now))
          .withExpiresAt(Date.from(Instant.ofEpochSecond(expiresAt)))
          .sign(algorithm);

    } catch (Exception e) {
      throw new RuntimeException("Failed to create JWT: " + e.getMessage(), e);
    }
  }

  private RSAPrivateKey parsePrivateKey(String privateKey) throws Exception {
    // Remove PEM headers and whitespace
    String keyContent =
        privateKey
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");

    byte[] keyBytes = Base64.getDecoder().decode(keyContent);
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) keyFactory.generatePrivate(spec);
  }
}
