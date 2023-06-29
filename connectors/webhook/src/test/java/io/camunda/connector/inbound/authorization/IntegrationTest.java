/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.catchThrowable;
import static org.junit.Assert.assertEquals;

import com.auth0.jwk.InvalidPublicKeyException;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.inbound.HttpWebhookExecutable;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.test.inbound.InboundConnectorPropertiesBuilder;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class IntegrationTest {

  private static final int PORT = 8089;

  private static WireMockServer wireMockServer;

  private final String processId = "Process_id";

  private final String REQ_BODY = "{\"key\": \"value\"}";

  private static final String JWK_BASE_PATH = "/jwk";

  private static final String JWK_PATH_ENDING = "/.well-known/jwks.json";

  private static final String JWK_ENDPOINT_PATH = JWK_BASE_PATH + JWK_PATH_ENDING;

  private final String JWK_FULL_URL = "http://localhost:" + PORT + JWK_BASE_PATH;

  private final ObjectMapper objectMapper;

  public IntegrationTest() {
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @BeforeAll
  public static void before() {
    ClasspathFileSource fileSource = new ClasspathFileSource("src/test/resources/authorization");
    wireMockServer = new WireMockServer(PORT, fileSource, true);
    wireMockServer.start();
    configureFor("localhost", PORT);
    // Mock the JWK endpoint
    stubFor(
        get(urlEqualTo(JWK_ENDPOINT_PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("jwks.json")));
  }

  @AfterAll
  public static void after() {
    wireMockServer.stop();
  }

  @Test
  public void happyPathTest() throws Exception {
    // given
    List<String> roles = Arrays.asList("admin", "superadmin");
    // webhook connector setup
    String propertiesJsonString =
        "{\"inbound.shouldValidateHmac\":\"disabled\", "
            + "\"inbound.shouldValidateJwt\":\"enabled\", "
            + "\"inbound.requiredPermissions\":\"admin\", "
            + // because we will skip feel type conversion
            "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
            + "}";
    HttpWebhookExecutable httpWebhookExecutable = setUpWebhook(propertiesJsonString);

    // webhook connector trigger
    TestWebhookProcessingPayload payload =
        new TestWebhookProcessingPayload(generateJWTToken(roles, false), REQ_BODY);
    WebhookProcessingResult webhookProcessingResult = httpWebhookExecutable.triggerWebhook(payload);

    // then
    assertEquals(objectMapper.readValue(REQ_BODY, Map.class), webhookProcessingResult.body());
  }

  @Test
  public void notEnoughPermissionButIsAdminTest() throws Exception {
    // given
    List<String> roles = Arrays.asList("user");
    // webhook connector setup
    String propertiesJsonString =
        "{\"inbound.shouldValidateHmac\":\"disabled\", "
            + "\"inbound.shouldValidateJwt\":\"enabled\", "
            + "\"inbound.requiredPermissions\":\"admin\", "
            + // because we will skip feel type conversion
            "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
            + "}";
    HttpWebhookExecutable httpWebhookExecutable = setUpWebhook(propertiesJsonString);

    // webhook connector trigger
    TestWebhookProcessingPayload payload =
        new TestWebhookProcessingPayload(generateJWTToken(roles, true), REQ_BODY);
    WebhookProcessingResult webhookProcessingResult = httpWebhookExecutable.triggerWebhook(payload);

    // then
    assertEquals(objectMapper.readValue(REQ_BODY, Map.class), webhookProcessingResult.body());
  }

  @Test
  public void notEnoughPermissionTest() throws Exception {
    // given
    List<String> roles = Arrays.asList("user");
    // webhook connector setup
    String propertiesJsonString =
        "{\"inbound.shouldValidateHmac\":\"disabled\", "
            + "\"inbound.shouldValidateJwt\":\"enabled\", "
            + "\"inbound.requiredPermissions\":\"admin\", "
            + // because we will skip feel type conversion
            "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
            + "}";
    HttpWebhookExecutable httpWebhookExecutable = setUpWebhook(propertiesJsonString);

    // webhook connector trigger
    TestWebhookProcessingPayload payload =
        new TestWebhookProcessingPayload(generateJWTToken(roles, false), REQ_BODY);

    // then
    Throwable thrown = catchThrowable(() -> httpWebhookExecutable.triggerWebhook(payload));
    assertThat(thrown)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Webhook failed: JWT check didn't pass");
  }

  @Test
  public void wrongTokenTest() throws Exception {
    // given
    List<String> roles = Arrays.asList("user");
    // webhook connector setup
    String propertiesJsonString =
        "{\"inbound.shouldValidateHmac\":\"disabled\", "
            + "\"inbound.shouldValidateJwt\":\"enabled\", "
            + "\"inbound.requiredPermissions\":\"admin\", "
            + // because we will skip feel type conversion
            "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
            + "}";
    HttpWebhookExecutable httpWebhookExecutable = setUpWebhook(propertiesJsonString);

    // webhook connector trigger
    TestWebhookProcessingPayload payload =
        new TestWebhookProcessingPayload("WRONG_TOKEN", REQ_BODY);

    // then
    Throwable thrown = catchThrowable(() -> httpWebhookExecutable.triggerWebhook(payload));
    assertThat(thrown)
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Webhook failed: JWT check didn't pass");
  }

  @Test
  public void wrongJwkUrlTest() throws Exception {
    // given
    List<String> roles = Arrays.asList("admin", "superadmin");
    // webhook connector setup
    String propertiesJsonString =
        "{\"inbound.shouldValidateHmac\":\"disabled\", "
            + "\"inbound.shouldValidateJwt\":\"enabled\", "
            + "\"inbound.requiredPermissions\":\"admin\", "
            + // because we will skip feel type conversion
            "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwkUrl\":\"https://google.com\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
            + "}";
    HttpWebhookExecutable httpWebhookExecutable = setUpWebhook(propertiesJsonString);

    // webhook connector trigger
    TestWebhookProcessingPayload payload =
        new TestWebhookProcessingPayload(generateJWTToken(roles, false), REQ_BODY);

    // then
    Throwable thrown = catchThrowable(() -> httpWebhookExecutable.triggerWebhook(payload));
    assertThat(thrown)
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Cannot obtain jwks from url");
  }

  private HttpWebhookExecutable setUpWebhook(String propertiesJsonString) throws Exception {
    Map<String, String> propertiesMap =
        this.objectMapper.readValue(propertiesJsonString, Map.class);
    InboundConnectorPropertiesBuilder inboundConnectorPropertiesBuilder =
        InboundConnectorPropertiesBuilder.create()
            .properties(propertiesMap)
            .bpmnProcessId(processId);
    WebhookConnectorProperties webhookConnectorProperties =
        new WebhookConnectorProperties(inboundConnectorPropertiesBuilder.build());
    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .propertiesAsType(webhookConnectorProperties)
            .properties(inboundConnectorPropertiesBuilder)
            .build();
    HttpWebhookExecutable httpWebhookExecutable = new HttpWebhookExecutable();
    httpWebhookExecutable.activate(context);
    return httpWebhookExecutable;
  }

  private String generateJWTToken(List<String> roles, boolean isAdmin) throws Exception {
    JwkProvider jwkProvider = new JwkProviderBuilder(JWK_FULL_URL).build();
    Jwk jwk = jwkProvider.get("c6f8386d31b98b77d83bba35a457aef4");

    RSAKeyProvider keyProvider =
        new RSAKeyProvider() {
          @Override
          public RSAPublicKey getPublicKeyById(String keyId) {
            try {
              return (RSAPublicKey) jwk.getPublicKey();
            } catch (InvalidPublicKeyException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public RSAPrivateKey getPrivateKey() {
            try {
              // You may need to provide the private key if signing is required
              // Otherwise, you can leave this method empty
              KeyFactory keyFactory = KeyFactory.getInstance("RSA");
              BigInteger modulus =
                  new BigInteger(
                      1,
                      Base64.getUrlDecoder()
                          .decode(
                              "0E19Jt_OljwfdqSQw3gTVLZJqe49nvhI0QwyShAXSEK_3FG79DxDD_WBxOw7ItNyoBAjFXc-7snXt1nu5uBEQe8a_65fHQ5BurV6v8t30o9IwpamXuSdIuSGlJK-yfO6ub309JXqfgSC_aNR2QuysqviqEIdUv_z3DDsMgZek5ycNnq2S5M1-raWpO5ILNGMevQg_bVnK_ZnK3I0yZQkL6PVbVrKkh9t6vHfzcxXmHE_sFY5fUQFuq5GPnRiYeU6isR3qRq01F4uAU9xNZ6uz-IGPQwgTuK51AN-lHT3fJtbkb3rRYCZgkLgSVVQfbAsvKZNIOZrFFtughZ-h6I9ZRw6PZGWl4Mud9Edup2YncGwD_ahLicNVe3OZmHASps_cELivS5lzau7J-oaORinZcsg5VWaWGl3EgIGvJhKA1550qyTX8c105ahLGAljboyV5Jc_H7uTEYadATtv7ccSSLuTJRgnA-Y7NT6q98BOiIzDmJiA-Y33QbvTG0VDka7"));
              BigInteger privateExponent =
                  new BigInteger(
                      1,
                      Base64.getUrlDecoder()
                          .decode(
                              "EiqH3SGMnz6MEelFNL7elLc3EmpUFm6Zzx1sr1fa5_LmT50TMrgksxoaoKVnfOCK8RmnLaKSKvoQZY2iz6DEYymqpZy778lEAzf7hgmFIChd1JaV2NXAPIBImmF34R3v7W37FG-UnTvgfqVFKJQkF__0iu8FJq1qw4vCtZQnoGD6oKewCURD42MUHTsosTvvL_PlgqrU3hklozzZDLFuPHdh0CEoZHj4OZKxjX2iMAnEX6kNZ3bMtxymxKCayeXXPk2DSjPu4y2EvbShx18EKbEHIqeHpiiZXBPzpraFZXsLXvSwyc16JGxNmxw0QyCOBlPZO1E6fjEv9hhsizyE-oRT_PS9nRas779iv-EQnKvEe97ERKYZm_u9Y42aJcbFrsitrUx2r4oNqTwyYD0UK560Lai4ex2XzZHPwgNSixmVtrWfFiKs_Zlqkd-R8BIzmMfCMKVoiOz-eeGbZbrEDvnZBZqPu-09qVAKW0vJ8BJ7Jgve-MggS1O_T2It-NEJ"));
              RSAPrivateKeySpec privateKeySpec = new RSAPrivateKeySpec(modulus, privateExponent);

              RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(privateKeySpec);
              return privateKey;
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
              throw new RuntimeException(e);
            }
          }

          @Override
          public String getPrivateKeyId() {
            return null;
          }
        };

    // Create the JWT token
    // Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey());
    Map<String, Object> headerClaims =
        Map.of(
            "typ", "at+jwt",
            "alg", jwk.getAlgorithm(),
            "kid", jwk.getId());
    Algorithm algorithm = Algorithm.RSA256(keyProvider);
    JWTCreator.Builder jwtBuilder =
        JWT.create()
            .withIssuer("https://idp.local")
            .withSubject("5be86359073c434bad2da3932222dabe")
            .withClaim("roles", roles)
            .withClaim("admin", isAdmin)
            .withAudience("api1")
            .withIssuedAt(new Date())
            .withHeader(headerClaims)
            .withExpiresAt(
                new Date(
                    System.currentTimeMillis()
                        + 3600000)) // Set expiration time (e.g., 1 hour from now)
            .withJWTId(UUID.randomUUID().toString());

    String jwtToken = jwtBuilder.sign(algorithm);
    return jwtToken;
  }
}
