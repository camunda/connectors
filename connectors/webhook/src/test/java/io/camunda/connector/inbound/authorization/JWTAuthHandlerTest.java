/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.Forbidden;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.model.JWTProperties;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class JWTAuthHandlerTest {

  // Tokens signed below are minted fresh at test-run time (instead of being baked in as fixed
  // strings) so their "exp" claim never falls into the past and turns these tests into a time
  // bomb. The RSA key material is the same RSA key that TestJwkProvider exposes as a JWK; the EC
  // key pair is generated once per test run and its public coordinates are exposed via
  // TestES512JwkProvider/JwkProviderNoAlg so verification uses a matching key pair.

  private static final String RSA_KID = "c6f8386d31b98b77d83bba35a457aef4";
  private static final String EC_KID = "fcaf10be37ad8b7461f8daab6d390c70";

  private static final String RSA_N =
      "0E19Jt_OljwfdqSQw3gTVLZJqe49nvhI0QwyShAXSEK_3FG79DxDD_WBxOw7ItNyoBAjFXc-7snXt1nu5uBEQe8a_65fHQ5BurV6v8t30o9IwpamXuSdIuSGlJK-yfO6ub309JXqfgSC_aNR2QuysqviqEIdUv_z3DDsMgZek5ycNnq2S5M1-raWpO5ILNGMevQg_bVnK_ZnK3I0yZQkL6PVbVrKkh9t6vHfzcxXmHE_sFY5fUQFuq5GPnRiYeU6isR3qRq01F4uAU9xNZ6uz-IGPQwgTuK51AN-lHT3fJtbkb3rRYCZgkLgSVVQfbAsvKZNIOZrFFtughZ-h6I9ZRw6PZGWl4Mud9Edup2YncGwD_ahLicNVe3OZmHASps_cELivS5lzau7J-oaORinZcsg5VWaWGl3EgIGvJhKA1550qyTX8c105ahLGAljboyV5Jc_H7uTEYadATtv7ccSSLuTJRgnA-Y7NT6q98BOiIzDmJiA-Y33QbvTG0VDka7";
  private static final String RSA_E = "AQAB";
  private static final String RSA_D =
      "EiqH3SGMnz6MEelFNL7elLc3EmpUFm6Zzx1sr1fa5_LmT50TMrgksxoaoKVnfOCK8RmnLaKSKvoQZY2iz6DEYymqpZy778lEAzf7hgmFIChd1JaV2NXAPIBImmF34R3v7W37FG-UnTvgfqVFKJQkF__0iu8FJq1qw4vCtZQnoGD6oKewCURD42MUHTsosTvvL_PlgqrU3hklozzZDLFuPHdh0CEoZHj4OZKxjX2iMAnEX6kNZ3bMtxymxKCayeXXPk2DSjPu4y2EvbShx18EKbEHIqeHpiiZXBPzpraFZXsLXvSwyc16JGxNmxw0QyCOBlPZO1E6fjEv9hhsizyE-oRT_PS9nRas779iv-EQnKvEe97ERKYZm_u9Y42aJcbFrsitrUx2r4oNqTwyYD0UK560Lai4ex2XzZHPwgNSixmVtrWfFiKs_Zlqkd-R8BIzmMfCMKVoiOz-eeGbZbrEDvnZBZqPu-09qVAKW0vJ8BJ7Jgve-MggS1O_T2It-NEJ";
  private static final String RSA_P =
      "955FOk5PJ6srgwXew1oOCgYEHCfGxRWSc7dmgvvob3QNUXWF8-UpHDbIV4QcipBIcW4bX6Kcpx5H2Ed6AJXdCwwhCt2b5FU_wjIrUNpF1oKOg4nZtU-38W95gEBCHYNDIb3s9oHnKY_JhQAd9NPWaRSn7CuP3q5WOjMBel7NoOomn-uOUGaoa1ZTAGzVElnuEZtjJLzLB-qD1TtJDOxxKGLrQcZd_XtZuZOYHIQl76HRmpFUQFeHaOfaKPJfvTZT";
  private static final String RSA_Q =
      "11qH0PJgHZkOqpGHVpMIQIJ1HrCaaKru3XNGgjPrdxuqstql7eoBCBRJ9QF-Jxb4vva6_EzuAe9pB7eQSf7_LV7ieUhYOuduXaNQap9P7G2YIAsKR-QcFFNFdJwZC2_u-qINoMZW6WlmaiX_64S8iAoJyX1BX6Mfzjfnuit8gAKntGhMiL9j_HHWB9fSTQL7pXbr2ZzdLWa-3r6oPoIeDKpsR5To3Y49soS-B4ss0OBmzbuNs3kkQvCKA3Rge9D5";
  private static final String RSA_DP =
      "m1B_1bGWvqgD09wTvO64jZr2av6cE7STNtfbid8eDQJ69BRGIBQmNBc8lIA-Mr-LYPKSYuspXT2GDOfQY5ucmThTZXBZPuAl7NXxC3bNhV5Aq8sEblCBbQPh7wrfLYnOytRJVNloOE6DNVQ8dfuFqkWVinJMfH1XgovJqoraactUzMxAaLfrfeAYkdSx1R_pTsvrZPiyVNXJOS9Fg8CuvYlGTOxyufLr9ZXZPFG7RdJ3GX98nBsKyUdcNIDKgnEX";
  private static final String RSA_DQ =
      "V_ZpfWZS7iQDgpY40pryBu-CiUuXdmNDd_7K8QnmBtutKKr4lao8S6GF5-Z5MdxHNDExhTajDPVOUFGhRSg78LtOBD4Rk9XOFb9MbJCwI6FMPuJji6PX-FFfEFdmP260DXm92UrKW0KrC3aEBx5Lp8F55-walMbbie753ug-TUdIWv8zK7QJUibpRoDYLI6oXRirE1LoYNAT7IWx0P5y6bd-zOo9B6o-9zkTKZ-maZcPRr9yDLnRnuoDZY_WjRx5";
  private static final String RSA_QI =
      "LbaKGEPI8OvEiDYFSam3UMwqSdRiYrr7GV3_heN6ak_cz_YP5TiavZJm-rQzzB4mm-CUllqDCDqOgfq7FdLCl3c4_N88xU5j7rkI5cA5FwispyI-WTSgtpW9CvCATCJJchx2PC2H8--EfDzBMaZtqLs1rtIPPYKMQRIIkoFw5tIBodagSKbb1Aiwib1Zp5QY9POIL6G-iYRFuTG03gbWYh3T7C5NjFcF_Uvl_GL6dwzsc7MyOWdIdfrxu87thN_H";

  private static final RSAPublicKey RSA_PUBLIC_KEY = buildRsaPublicKey();
  private static final RSAPrivateKey RSA_PRIVATE_KEY = buildRsaPrivateKey();
  private static final Algorithm RSA_ALGORITHM = Algorithm.RSA256(RSA_PUBLIC_KEY, RSA_PRIVATE_KEY);

  private static final KeyPair EC_KEY_PAIR = generateEcKeyPair();
  private static final ECPublicKey EC_PUBLIC_KEY = (ECPublicKey) EC_KEY_PAIR.getPublic();
  private static final ECPrivateKey EC_PRIVATE_KEY = (ECPrivateKey) EC_KEY_PAIR.getPrivate();
  private static final Algorithm EC_ALGORITHM = Algorithm.ECDSA512(EC_PUBLIC_KEY, EC_PRIVATE_KEY);
  private static final String EC_X = encodeUnsigned(EC_PUBLIC_KEY.getW().getAffineX());
  private static final String EC_Y = encodeUnsigned(EC_PUBLIC_KEY.getW().getAffineY());

  private static final Instant FAR_FUTURE_EXPIRY = Instant.now().plus(Duration.ofDays(3650));

  private static final String JWT_TOKEN =
      sign(RSA_ALGORITHM, RSA_KID, List.of("admin", "superadmin"), true, FAR_FUTURE_EXPIRY);
  private static final String JWT_WITH_ES512_ALGORITHM_TOKEN =
      sign(EC_ALGORITHM, EC_KID, List.of("admin", "superadmin"), true, FAR_FUTURE_EXPIRY);
  private static final String NOT_ENOUGH_PERMISSION_JWT_TOKEN =
      sign(RSA_ALGORITHM, RSA_KID, List.of("user"), null, FAR_FUTURE_EXPIRY);

  // Wrong signature and expiry are independent of wall-clock time, so these can stay fixed.
  private static final String WRONG_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6IjNjZDljMWM4NDU3ZjRkMDhiNDlkMDI2OGNhNWYwMDhiIn0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE2ODc3OTM4MzUsImlhdCI6MTY4Nzc5MDIzNSwianRpIjoiNmE3ZDllNDljNWViZjYzNWM2MjVjNWQwZDAxOGNmYjIiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.YP4Zw8graOY5wMJpxIZzYNN01xtOquWzT74boxMkhCdKMU_35PCoufZqUbyvNTD5YLltBe_dYe-sLuN4s-ZjeivL4ySSDtaeCd60D5JnjLq7vuC6MUd9nBHo2fIbIAwkEiWi_flCCiyzNa3Ir4KPCWxEL2cdibnjxeovUKBhnjRdf3tq4ADWrczHpf4wxZXL8aLEHzM6I5nSV6I3R9Arb6Cie-gHDfwxjGB_PoD3L5syB7izdNAMJPLlv4XHwIZ_5Pdsle546cwaZqJhmEjjHgsRJ_JEa_Xpm1zfmShHCDixkEKGfQ0JN5nYqE2JCnhlpjyWNrkqMmnAxb1AsDzwrA";
  private static final String EXPIRED_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE2ODcwMDAwMDAsImlhdCI6MTY4NzAwMDAwMCwianRpIjoiZTg0MWU1NzczZmUxN2ExNTYzNTM0ZWFhODRkOTNiNGQiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.e0w7LwLKIpeXnms1eUHuNysoqxPzvhreVLKBhtOpRgiFr60Nrmn04EXEU4YdzGW4zU9tDdc9z8xTyfouQ7ImcLAj7p74v3fsIpckHwaAvi9FRu0kPVrCsmNC8a9M7pwRJsPPCi8DReQVnR0G0mTF12m9SIIpdf6VfaJeuNsHhQB5on6md4uxZ7X5fXZz3Z9A5xp3ZjPji6nknZUyTyTNcJ_GvEzZ4Jx9svHOm6OpDjVM57D8WI_6YNwqnEMQs-JxYNoWBSoIm1V_0rvMxLltINv0G6kvHjDApxcyUAbarpYVUUe0Sm2CoefNVXZPbb-X5gabqGrlKCFOf9ovprZ9NbgpHGawrhUgrJ3-ltkwwpi4zs7i0kj3iuGBRPh_8qJhH5NRvuPJVWN4RUhnuLuxhjenbE9UGPjIkqgYdWUHQ19qCVhf52m3UdHRatKG0GG1DLH4BEDZysvpa9y112oHSvWRmIasJMC3r4hrXnV1iLLIqZz7lv3UfTtXJAjqwGyY";

  private final ObjectMapper objectMapper;
  private final FeelEngineWrapper feelEngineWrapper;

  public JWTAuthHandlerTest() {
    this.objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    this.feelEngineWrapper = new FeelEngineWrapper();
  }

  private static BigInteger decodeUnsigned(String base64Url) {
    return new BigInteger(1, Base64.getUrlDecoder().decode(base64Url));
  }

  private static String encodeUnsigned(BigInteger value) {
    byte[] bytes = value.toByteArray();
    if (bytes.length > 1 && bytes[0] == 0) {
      bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static RSAPublicKey buildRsaPublicKey() {
    try {
      var spec = new RSAPublicKeySpec(decodeUnsigned(RSA_N), decodeUnsigned(RSA_E));
      return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to build test RSA public key", e);
    }
  }

  private static RSAPrivateKey buildRsaPrivateKey() {
    try {
      var spec =
          new RSAPrivateCrtKeySpec(
              decodeUnsigned(RSA_N),
              decodeUnsigned(RSA_E),
              decodeUnsigned(RSA_D),
              decodeUnsigned(RSA_P),
              decodeUnsigned(RSA_Q),
              decodeUnsigned(RSA_DP),
              decodeUnsigned(RSA_DQ),
              decodeUnsigned(RSA_QI));
      return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to build test RSA private key", e);
    }
  }

  private static KeyPair generateEcKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
      generator.initialize(new ECGenParameterSpec("secp521r1"));
      return generator.generateKeyPair();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to generate test EC key pair", e);
    }
  }

  private static String sign(
      Algorithm algorithm, String keyId, List<String> roles, Boolean admin, Instant expiresAt) {
    var creator =
        JWT.create()
            .withKeyId(keyId)
            .withIssuer("https://idp.local")
            .withAudience("api1")
            .withSubject("5be86359073c434bad2da3932222dabe")
            .withClaim("client_id", "my_client_app")
            .withIssuedAt(Instant.now())
            .withExpiresAt(expiresAt)
            .withClaim("roles", roles);
    if (admin != null) {
      creator = creator.withClaim("admin", admin);
    }
    return creator.sign(algorithm);
  }

  @Test
  public void jwtCheckSuccessTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            "https://mockUrl.com",
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            List.of("admin"));
    var headers = Map.of("Authorization", "Bearer " + JWT_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(Success.class);
  }

  Function<Object, List<String>> getRoleExpressionFunction(String rawFeelExpression) {
    return variables -> this.feelEngineWrapper.evaluate(rawFeelExpression, variables);
  }

  @Test
  public void jwtCheckSuccessWithDifferentAlgorithmTest() {
    // given
    JwkProvider jwkProvider = new TestES512JwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            "https://mockUrl.com",
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            List.of("admin"));
    var headers = Map.of("Authorization", "Bearer " + JWT_WITH_ES512_ALGORITHM_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(Success.class);
  }

  @Test
  public void jwtCheckWrongTokenTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            "https://mockUrl.com",
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            List.of("admin"));
    var headers = Map.of("Authorization", "Bearer " + WRONG_JWT_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(InvalidCredentials.class);
  }

  @Test
  public void jwtCheckTokenExpiredTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            "https://mockUrl.com",
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            List.of("admin"));
    var headers = Map.of("Authorization", "Bearer " + EXPIRED_JWT_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(InvalidCredentials.class);
  }

  @Test
  public void jwtCheckTokenNotEnoughPermissionTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            "https://mockUrl.com",
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            List.of("admin"));
    var headers = Map.of("Authorization", "Bearer " + NOT_ENOUGH_PERMISSION_JWT_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(Forbidden.class);
  }

  @Test
  public void jwtCheckWrongRoleExpressionTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            "https://mockUrl.com",
            getRoleExpressionFunction(
                "=if admin = true then [\"wrongPermission\"] else wrongPermission"),
            List.of("admin"));
    var headers = Map.of("Authorization", "Bearer " + JWT_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(Forbidden.class);
  }

  @Test
  public void jwtCheckWithOutRoles() {
    // given jwt, check only signature
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties = new JWTProperties("https://mockUrl.com", null, null);
    var headers = Map.of("Authorization", "Bearer " + JWT_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(Success.class);
  }

  @Test
  public void noAlgProvidedByJwkProvider() {
    // given jwt, check only signature
    JwkProvider jwkProvider = new JwkProviderNoAlg();
    JWTProperties jwtProperties = new JWTProperties("https://mockUrl.com", null, null);
    var headers = Map.of("Authorization", "Bearer " + JWT_WITH_ES512_ALGORITHM_TOKEN);
    var handler = new JWTAuthHandler(new JwtAuth(jwtProperties), jwkProvider, objectMapper);
    var payload = new TestWebhookProcessingPayload(headers);

    // when
    var verificationResult = handler.checkAuthorization(payload);

    // then
    assertThat(verificationResult).isInstanceOf(Success.class);
  }

  static class TestJwkProvider implements JwkProvider {

    @Override
    public Jwk get(String keyId) {
      Map<String, Object> jwkMap = new HashMap<>();
      jwkMap.put("kid", RSA_KID);
      jwkMap.put("use", "sig");
      jwkMap.put("alg", "RS256");
      jwkMap.put("kty", "RSA");
      jwkMap.put("key_ops", List.of("sign"));
      jwkMap.put("d", RSA_D);
      jwkMap.put("dp", RSA_DP);
      jwkMap.put("dq", RSA_DQ);
      jwkMap.put("e", RSA_E);
      jwkMap.put("n", RSA_N);
      jwkMap.put("p", RSA_P);
      jwkMap.put("q", RSA_Q);
      jwkMap.put("qi", RSA_QI);
      jwkMap.put("crv", "P-256");

      return Jwk.fromValues(jwkMap);
    }
  }

  static class TestES512JwkProvider implements JwkProvider {

    @Override
    public Jwk get(String keyId) {
      Map<String, Object> jwkMap = new HashMap<>();
      jwkMap.put("kid", EC_KID);
      jwkMap.put("use", "sig");
      jwkMap.put("alg", "ES512");
      jwkMap.put("kty", "EC");
      jwkMap.put("key_ops", List.of("sign"));
      jwkMap.put("x", EC_X);
      jwkMap.put("y", EC_Y);
      jwkMap.put("crv", "P-521");

      return Jwk.fromValues(jwkMap);
    }
  }

  static class JwkProviderNoAlg implements JwkProvider {

    @Override
    public Jwk get(String keyId) {
      Map<String, Object> jwkMap = new HashMap<>();
      jwkMap.put("kid", EC_KID);
      jwkMap.put("use", "sig");
      jwkMap.put("kty", "EC");
      jwkMap.put("key_ops", List.of("sign"));
      jwkMap.put("x", EC_X);
      jwkMap.put("y", EC_Y);
      jwkMap.put("crv", "P-521");

      return Jwk.fromValues(jwkMap);
    }
  }
}
