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
import java.util.regex.Pattern;

public class CreateGithubAppJwtFunction implements IntrinsicFunctionProvider {

  // Default expiration time of 1 minute
  static final long DEFAULT_EXPIRATION_SECONDS = 60L;

  /**
   * A pre-compiled pattern to remove PEM headers/footers and all whitespace characters. This makes
   * the parsing more robust and efficient.
   */
  private static final Pattern PEM_KEY_PATTERN =
      Pattern.compile("-----(BEGIN|END) (RSA )?PRIVATE KEY-----|\\s+");

  private static final String RSA_ALGORITHM = "RSA";

  @IntrinsicFunction(name = "createJwt")
  public String execute(String privateKey, String appId) {

    try {
      RSAPrivateKey rsaPrivateKey = parsePrivateKey(privateKey);

      // Calculate time values - use seconds since epoch for precision
      Instant now = Instant.now();
      long issuedAt = now.getEpochSecond();
      long expiresAt = issuedAt + DEFAULT_EXPIRATION_SECONDS;

      // Build JWT with RS256 algorithm
      Algorithm algorithm = Algorithm.RSA256(null, rsaPrivateKey);
      return JWT.create()
          .withIssuer(appId)
          .withIssuedAt(Date.from(now))
          .withExpiresAt(Date.from(Instant.ofEpochSecond(expiresAt)))
          .sign(algorithm);

    } catch (Exception e) {
      throw new RuntimeException("Failed to create JWT: " + e.getMessage(), e);
    }
  }

  private RSAPrivateKey parsePrivateKey(String privateKey) throws Exception {
    // Remove PEM headers/footers and all whitespace to isolate the Base64-encoded key
    final String keyContent = PEM_KEY_PATTERN.matcher(privateKey).replaceAll("");

    final byte[] keyBytes = Base64.getDecoder().decode(keyContent);
    final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    final KeyFactory keyFactory = KeyFactory.getInstance(RSA_ALGORITHM);
    return (RSAPrivateKey) keyFactory.generatePrivate(spec);
  }
}
