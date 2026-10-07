/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;

import com.box.sdkgen.networking.fetchoptions.FetchOptions;
import com.box.sdkgen.networking.fetchresponse.FetchResponse;
import com.box.sdkgen.networking.network.NetworkSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.box.model.BoxRequest.Authentication;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import org.junit.jupiter.api.Test;

public class BoxAuthenticationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final List<FetchOptions> requests = new ArrayList<>();

  private NetworkSession recordingSession() {
    return new NetworkSession()
        .withNetworkClient(
            options -> {
              requests.add(options);
              if (options.getUrl().endsWith("/oauth2/token")) {
                return new FetchResponse.Builder(200, Map.of())
                    .data(
                        MAPPER
                            .createObjectNode()
                            .put("access_token", "token")
                            .put("expires_in", 3600)
                            .put("token_type", "bearer"))
                    .build();
              }
              throw new IllegalStateException("unexpected request: " + options.getUrl());
            });
  }

  private FetchOptions tokenRequestFor(Authentication authentication) {
    var client = BoxOperations.connectToApi(authentication, recordingSession());
    client.auth.retrieveToken(client.networkSession);
    return requests.stream()
        .filter(r -> r.getUrl().endsWith("/oauth2/token"))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void clientCredentialsUserExchangesTokenForTheUserSubject() {
    var request =
        tokenRequestFor(new Authentication.ClientCredentialsUser("client", "secret", "user-1"));

    JsonNode data = request.getData();
    assertThat(data.get("grant_type").asText()).isEqualTo("client_credentials");
    assertThat(data.get("box_subject_type").asText()).isEqualTo("user");
    assertThat(data.get("box_subject_id").asText()).isEqualTo("user-1");
    assertThat(data.get("client_id").asText()).isEqualTo("client");
  }

  @Test
  void clientCredentialsEnterpriseExchangesTokenForTheEnterpriseSubject() {
    var request =
        tokenRequestFor(
            new Authentication.ClientCredentialsEnterprise("client", "secret", "enterprise-1"));

    JsonNode data = request.getData();
    assertThat(data.get("grant_type").asText()).isEqualTo("client_credentials");
    assertThat(data.get("box_subject_type").asText()).isEqualTo("enterprise");
    assertThat(data.get("box_subject_id").asText()).isEqualTo("enterprise-1");
  }

  @Test
  void jwtUsesTheEnterpriseIdFromTheConfigAsSubject() throws Exception {
    var request = tokenRequestFor(new Authentication.JWTJsonConfig(jwtConfigJson("enterprise-42")));

    JsonNode data = request.getData();
    assertThat(data.get("grant_type").asText())
        .isEqualTo("urn:ietf:params:oauth:grant-type:jwt-bearer");
    String[] jwt = data.get("assertion").asText().split("\\.");
    JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(jwt[1]));
    assertThat(claims.get("sub").asText()).isEqualTo("enterprise-42");
    assertThat(claims.get("box_sub_type").asText()).isEqualTo("enterprise");
    assertThat(claims.get("iss").asText()).isEqualTo("jwt-client-id");
  }

  private static String jwtConfigJson(String enterpriseId) throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var privateKey = generator.generateKeyPair().getPrivate();

    var algorithm = "PBEWithSHA1AndDESede";
    var salt = new byte[8];
    new SecureRandom().nextBytes(salt);
    var key =
        SecretKeyFactory.getInstance(algorithm)
            .generateSecret(new PBEKeySpec("passphrase".toCharArray()));
    var cipher = Cipher.getInstance(algorithm);
    cipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(salt, 10_000));
    var encrypted =
        new EncryptedPrivateKeyInfo(
            cipher.getParameters(), cipher.doFinal(privateKey.getEncoded()));
    var pem =
        "-----BEGIN ENCRYPTED PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encrypted.getEncoded())
            + "\n-----END ENCRYPTED PRIVATE KEY-----\n";

    return MAPPER
        .createObjectNode()
        .put("enterpriseID", enterpriseId)
        .set(
            "boxAppSettings",
            MAPPER
                .createObjectNode()
                .put("clientID", "jwt-client-id")
                .put("clientSecret", "jwt-secret")
                .set(
                    "appAuth",
                    MAPPER
                        .createObjectNode()
                        .put("publicKeyID", "key-id")
                        .put("privateKey", pem)
                        .put("passphrase", "passphrase")))
        .toString();
  }
}
