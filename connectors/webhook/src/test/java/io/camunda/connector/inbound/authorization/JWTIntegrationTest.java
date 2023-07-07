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

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.JwkProviderBuilder;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ClasspathFileSource;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingResult;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.inbound.HttpWebhookExecutable;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import io.camunda.connector.inbound.utils.TestRSAKeyProvider;
import io.camunda.connector.test.inbound.InboundConnectorContextBuilder;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled // to be run manually
public class JWTIntegrationTest {

  private static final int PORT = 8089;

  private static WireMockServer wireMockServer;

  private final String processId = "Process_id";

  private final String REQ_BODY = "{\"key\": \"value\"}";

  private static final String JWK_BASE_PATH = "/jwk";

  private static final String JWK_PATH_ENDING = "/.well-known/jwks.json";

  private static final String JWK_ENDPOINT_PATH = JWK_BASE_PATH + JWK_PATH_ENDING;

  private final String JWK_FULL_URL = "http://localhost:" + PORT + JWK_BASE_PATH;

  private final ObjectMapper objectMapper;

  public JWTIntegrationTest() {
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
            + "\"inbound.authorizationType\":\"JWT\", "
            + "\"inbound.jwt.requiredPermissions\":\"=[\\\"admin\\\"]\", "
            + "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwt.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwt.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
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
            + "\"inbound.authorizationType\":\"JWT\", "
            + "\"inbound.jwt.requiredPermissions\":\"=[\\\"admin\\\"]\", "
            + "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwt.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwt.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
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
            + "\"inbound.authorizationType\":\"JWT\", "
            + "\"inbound.jwt.requiredPermissions\":\"=[\\\"admin\\\"]\", "
            + "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwt.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwt.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
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
  public void tokenInWrongFormatTest() throws Exception {
    // given
    List<String> roles = Arrays.asList("user");
    // webhook connector setup
    String propertiesJsonString =
        "{\"inbound.shouldValidateHmac\":\"disabled\", "
            + "\"inbound.authorizationType\":\"JWT\", "
            + "\"inbound.jwt.requiredPermissions\":\"=[\\\"admin\\\"]\", "
            + "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwt.jwkUrl\":\""
            + JWK_FULL_URL
            + JWK_PATH_ENDING
            + "\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwt.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
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
            + "\"inbound.authorizationType\":\"JWT\", "
            + "\"inbound.jwt.requiredPermissions\":\"=[\\\"admin\\\"]\", "
            + "\"inbound.type\":\"io.camunda:webhook:1\", "
            + "\"inbound.subtype\":\"ConfigurableInboundWebhook\", "
            + "\"inbound.jwt.jwkUrl\":\"https://google.com\", "
            + "\"inbound.context\":\"test\", "
            + "\"inbound.jwt.jwtRoleExpression\":\"=if admin = true then [\\\"admin\\\"] else roles\""
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
    InboundConnectorContextBuilder.TestInboundConnectorContext context =
        InboundConnectorContextBuilder.create()
            .result(new MessageCorrelationResult("", 0))
            .properties(propertiesMap)
            .build();
    HttpWebhookExecutable httpWebhookExecutable = new HttpWebhookExecutable();
    httpWebhookExecutable.activate(context);
    return httpWebhookExecutable;
  }

  private String generateJWTToken(List<String> roles, boolean isAdmin) throws Exception {
    JwkProvider jwkProvider = new JwkProviderBuilder(JWK_FULL_URL).build();
    Jwk jwk = jwkProvider.get("c6f8386d31b98b77d83bba35a457aef4");

    Map<String, Object> headerClaims =
        Map.of(
            "typ", "at+jwt",
            "alg", jwk.getAlgorithm(),
            "kid", jwk.getId());
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

    Algorithm algorithm = Algorithm.RSA256(new TestRSAKeyProvider(jwk));
    return jwtBuilder.sign(algorithm);
  }
}
