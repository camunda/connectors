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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.Forbidden;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.model.JWTProperties;
import io.camunda.connector.inbound.model.WebhookAuthorization.JwtAuth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

public class JWTAuthHandlerTest {

  private static final String JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjQxMDI0NDQ4MDAsImlhdCI6MTc4NjgxOTAxNiwianRpIjoiMTE0ZjhjODRjNTM3MDNhYzIxMjBkMzAyNjExZTM1OGMiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.w3mTRTsBp01mvhUxdMER0WyimeR_PVprqpbMwvIS_3tSuylFPYeXMAawys0SUivWbGcskrgVw2cZvcfwP4Ja0d9KUiJOMyBlQgZlMNfYSAoi_W3pViDvBL_TAyxQF3nZk3rxlnX8k89PiyIwo4jCuNVgOS1_1safc-pOGuzb8jf1RInb38kAiy8gz0A5MfrmavrSpap-DKgyGr2eTHxtNs8zpH7u7nOT3ksGRHf2tY0nmTm-XAVbNXWwBr6w_bJosM8lBLTi_Q3--2FHCM1rA2DktzGevDRH1WhrkB6cUalboo_H27s0q2P41rUO57OZARKWzwBPt_pZqRgt2hMzxl35AC1DREHQBxv8c6Ny1KhN5xYSKWtfYyrOr2cuzfiAhN7susdI54TooBQPmhUj6j_7v91GrlnGTz8YfY7bMgDlmNzIQJ41BH15GsQl7gthvjsKyBNE7HAJUFXVm9ztzCT4zCQsP6au9xCuzGA7npyRoSPSmY9HCqev1om_G-04";
  private static final String JWT_WITH_ES512_ALGORITHM_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJFUzUxMiIsImtpZCI6IjlkNjVjNTBjYzFmNWMwMThhOGQ1OTZhMWYyMGU3MTY5In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjQxMDI0NDQ4MDAsImlhdCI6MTc4NjgxOTAxNiwianRpIjoiMTE0ZjhjODRjNTM3MDNhYzIxMjBkMzAyNjExZTM1OGMiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.AFwA6lwzkWuZrs4pH0JmOEKdSsIW2QfscsL0_TArm9pGQyTOO7L79Dro31sUmVHSaDzfS0Qyt3uyYoQ-4U3zkFa8AIJpUvIbE0Fjf9iyX4ib61w0pHq-lO5d7wMxoDdprdg50L4B0M9UYDjZTsTyKXRpu_kcMtVJlAOjkJXMjUyPpJji";
  private static final String WRONG_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6IjNjZDljMWM4NDU3ZjRkMDhiNDlkMDI2OGNhNWYwMDhiIn0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE2ODc3OTM4MzUsImlhdCI6MTY4Nzc5MDIzNSwianRpIjoiNmE3ZDllNDljNWViZjYzNWM2MjVjNWQwZDAxOGNmYjIiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.YP4Zw8graOY5wMJpxIZzYNN01xtOquWzT74boxMkhCdKMU_35PCoufZqUbyvNTD5YLltBe_dYe-sLuN4s-ZjeivL4ySSDtaeCd60D5JnjLq7vuC6MUd9nBHo2fIbIAwkEiWi_flCCiyzNa3Ir4KPCWxEL2cdibnjxeovUKBhnjRdf3tq4ADWrczHpf4wxZXL8aLEHzM6I5nSV6I3R9Arb6Cie-gHDfwxjGB_PoD3L5syB7izdNAMJPLlv4XHwIZ_5Pdsle546cwaZqJhmEjjHgsRJ_JEa_Xpm1zfmShHCDixkEKGfQ0JN5nYqE2JCnhlpjyWNrkqMmnAxb1AsDzwrA";
  private static final String EXPIRED_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE2ODcwMDAwMDAsImlhdCI6MTY4NzAwMDAwMCwianRpIjoiZTg0MWU1NzczZmUxN2ExNTYzNTM0ZWFhODRkOTNiNGQiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.e0w7LwLKIpeXnms1eUHuNysoqxPzvhreVLKBhtOpRgiFr60Nrmn04EXEU4YdzGW4zU9tDdc9z8xTyfouQ7ImcLAj7p74v3fsIpckHwaAvi9FRu0kPVrCsmNC8a9M7pwRJsPPCi8DReQVnR0G0mTF12m9SIIpdf6VfaJeuNsHhQB5on6md4uxZ7X5fXZz3Z9A5xp3ZjPji6nknZUyTyTNcJ_GvEzZ4Jx9svHOm6OpDjVM57D8WI_6YNwqnEMQs-JxYNoWBSoIm1V_0rvMxLltINv0G6kvHjDApxcyUAbarpYVUUe0Sm2CoefNVXZPbb-X5gabqGrlKCFOf9ovprZ9NbgpHGawrhUgrJ3-ltkwwpi4zs7i0kj3iuGBRPh_8qJhH5NRvuPJVWN4RUhnuLuxhjenbE9UGPjIkqgYdWUHQ19qCVhf52m3UdHRatKG0GG1DLH4BEDZysvpa9y112oHSvWRmIasJMC3r4hrXnV1iLLIqZz7lv3UfTtXJAjqwGyY";
  private static final String NOT_ENOUGH_PERMISSION_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjQxMDI0NDQ4MDAsImlhdCI6MTc4NjgxOTAxNiwianRpIjoiMTE0ZjhjODRjNTM3MDNhYzIxMjBkMzAyNjExZTM1OGMiLCJyb2xlcyI6WyJ1c2VyIl19.EROiAZJz7FTVKUdmBFYDcq740aTyV73MM4TrvGoU-Xr8lbh0KQyhs75wa8BYOJulL9-ixezsBxaPpIG_o69UBOhxY00OPazMmtuSu-jNku8FV8Uk3FzxMl86du4Cf6jCg58NfVIAN0_HTg7iDK8YOeI1Ac4dhWSt7vuFIQ_Oxtz6LdjN0-YDKMp4KwAKu4Zj9fgAK7GU5lGD-vBji9QQaw0jnk1o5nzds8Ww6WoHKWeoiLcCuD8PgLM98xIwyqDXpjRNoq5ap_UhSdtEXgwrWCttSsG7w6TPsI0Sc81ZIDJ3RbSEn0qnpgnNVcGpM1GM5KvrcQ62nxcBJY5Cxq5mJM33cig3NEab82PgLM-voS7Us8JHykTVMw8Hyq8WKPZXo7rB7uXO6EAhy7GdUzviuYkXorwpNqQ_0bw2Z0SesM_-uX5jXJv9T0yNCkD0O6f0USXFMsJ2GK6JWXkPoUt1V9rOGLHmjIIgSnKsv4ghdspYe6zLMDsaYrZnImJuX-yh";
  private static final String NO_ALG_PRESENT_JWT =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsIng1dCI6InoxcnNZSEhKOS04bWdndDRIc1p1OEJLa0JQdyIsImtpZCI6InoxcnNZSEhKOS04bWdndDRIc1p1OEJLa0JQdyJ9.eyJhdWQiOiJhcGk6Ly83YWJlOGQzNi1iMDViLTQ1OGItOTdkNy0zYjhiM2VjOWM4ZTkiLCJpc3MiOiJodHRwczovL3N0cy53aW5kb3dzLm5ldC84ZWJlMjQ5ZC04MzEyLTRmZmItOWI2Yi0wOGU1NjY2OWQ1NzgvIiwiaWF0IjoxNzM2NzYzMDk4LCJuYmYiOjE3MzY3NjMwOTgsImV4cCI6MTczNjc2Njk5OCwiYWlvIjoiazJSZ1lJaHp2Tzg4MldoMnBwV0M3cjdyaVpsbUFBPT0iLCJhcHBpZCI6IjdhYmU4ZDM2LWIwNWItNDU4Yi05N2Q3LTNiOGIzZWM5YzhlOSIsImFwcGlkYWNyIjoiMSIsImlkcCI6Imh0dHBzOi8vc3RzLndpbmRvd3MubmV0LzhlYmUyNDlkLTgzMTItNGZmYi05YjZiLTA4ZTU2NjY5ZDU3OC8iLCJvaWQiOiIxOTZkYzU0NC1kMzAyLTQxYmQtYjJiMS04ODE0YWUzNmRmZmEiLCJyaCI6IjEuQVRrQW5TUy1qaEtELTAtYmF3amxabW5WZURhTnZucGJzSXRGbDljN2l6N0p5T2s1QUFBNUFBLiIsInN1YiI6IjE5NmRjNTQ0LWQzMDItNDFiZC1iMmIxLTg4MTRhZTM2ZGZmYSIsInRpZCI6IjhlYmUyNDlkLTgzMTItNGZmYi05YjZiLTA4ZTU2NjY5ZDU3OCIsInV0aSI6InZkaDlRQjBySEVlUm1fZFZ4VkVLQUEiLCJ2ZXIiOiIxLjAifQ.kAEtEYbMD47IyhgZL8KDX1I65j7gPtjXdL9iv4JwcCwTx8NL0R1gKHZPvWyyg09XqyQxVF8m5r0SxXVhvaGZCbMDrkaGOKDlwNjTzQIta3gtCiLxHdsmbrMAOt8ktVGRHLKzQcvYpVSUJhSxX4XikqugusNlU1acvKWUgkzal98YF-RvwcqlevkbHeyYmaful-6gP9Yf7p4mawlupOzl_A30Qf13a07kH-39CO5H2z_akA1eB0u8sINY-Y8l0We0ncKmP-C0vlQe5T2z3vyWuTPESRtCWgXipuYzzD1T9ZupkMTa72DAWhCOLCHKeuckLTajhj_9ZmfWRkePZUC0SQ";
  private final ObjectMapper objectMapper;
  private final FeelEngineWrapper feelEngineWrapper;

  /* JWT token content *
  {
    "iss": "https://idp.local",
    "aud": "api1",
    "sub": "5be86359073c434bad2da3932222dabe",
    "client_id": "my_client_app",
    "exp": 4102444800,
    "iat": 1786819016,
    "jti": "114f8c84c53703ac2120d302611e358c",
    "roles": ["admin", "superadmin"],
    "admin": true
  }
  * */

  /* JWK *
  {
    "kid": "c6f8386d31b98b77d83bba35a457aef4",
    "kty": "RSA",
    "alg": "RS256",
    "use": "sig",
    "d": "EiqH3SGMnz6MEelFNL7elLc3EmpUFm6Zzx1sr1fa5_LmT50TMrgksxoaoKVnfOCK8RmnLaKSKvoQZY2iz6DEYymqpZy778lEAzf7hgmFIChd1JaV2NXAPIBImmF34R3v7W37FG-UnTvgfqVFKJQkF__0iu8FJq1qw4vCtZQnoGD6oKewCURD42MUHTsosTvvL_PlgqrU3hklozzZDLFuPHdh0CEoZHj4OZKxjX2iMAnEX6kNZ3bMtxymxKCayeXXPk2DSjPu4y2EvbShx18EKbEHIqeHpiiZXBPzpraFZXsLXvSwyc16JGxNmxw0QyCOBlPZO1E6fjEv9hhsizyE-oRT_PS9nRas779iv-EQnKvEe97ERKYZm_u9Y42aJcbFrsitrUx2r4oNqTwyYD0UK560Lai4ex2XzZHPwgNSixmVtrWfFiKs_Zlqkd-R8BIzmMfCMKVoiOz-eeGbZbrEDvnZBZqPu-09qVAKW0vJ8BJ7Jgve-MggS1O_T2It-NEJ",
    "e": "AQAB",
    "dp": "m1B_1bGWvqgD09wTvO64jZr2av6cE7STNtfbid8eDQJ69BRGIBQmNBc8lIA-Mr-LYPKSYuspXT2GDOfQY5ucmThTZXBZPuAl7NXxC3bNhV5Aq8sEblCBbQPh7wrfLYnOytRJVNloOE6DNVQ8dfuFqkWVinJMfH1XgovJqoraactUzMxAaLfrfeAYkdSx1R_pTsvrZPiyVNXJOS9Fg8CuvYlGTOxyufLr9ZXZPFG7RdJ3GX98nBsKyUdcNIDKgnEX",
    "dq": "V_ZpfWZS7iQDgpY40pryBu-CiUuXdmNDd_7K8QnmBtutKKr4lao8S6GF5-Z5MdxHNDExhTajDPVOUFGhRSg78LtOBD4Rk9XOFb9MbJCwI6FMPuJji6PX-FFfEFdmP260DXm92UrKW0KrC3aEBx5Lp8F55-walMbbie753ug-TUdIWv8zK7QJUibpRoDYLI6oXRirE1LoYNAT7IWx0P5y6bd-zOo9B6o-9zkTKZ-maZcPRr9yDLnRnuoDZY_WjRx5",
    "n": "0E19Jt_OljwfdqSQw3gTVLZJqe49nvhI0QwyShAXSEK_3FG79DxDD_WBxOw7ItNyoBAjFXc-7snXt1nu5uBEQe8a_65fHQ5BurV6v8t30o9IwpamXuSdIuSGlJK-yfO6ub309JXqfgSC_aNR2QuysqviqEIdUv_z3DDsMgZek5ycNnq2S5M1-raWpO5ILNGMevQg_bVnK_ZnK3I0yZQkL6PVbVrKkh9t6vHfzcxXmHE_sFY5fUQFuq5GPnRiYeU6isR3qRq01F4uAU9xNZ6uz-IGPQwgTuK51AN-lHT3fJtbkb3rRYCZgkLgSVVQfbAsvKZNIOZrFFtughZ-h6I9ZRw6PZGWl4Mud9Edup2YncGwD_ahLicNVe3OZmHASps_cELivS5lzau7J-oaORinZcsg5VWaWGl3EgIGvJhKA1550qyTX8c105ahLGAljboyV5Jc_H7uTEYadATtv7ccSSLuTJRgnA-Y7NT6q98BOiIzDmJiA-Y33QbvTG0VDka7",
    "p": "955FOk5PJ6srgwXew1oOCgYEHCfGxRWSc7dmgvvob3QNUXWF8-UpHDbIV4QcipBIcW4bX6Kcpx5H2Ed6AJXdCwwhCt2b5FU_wjIrUNpF1oKOg4nZtU-38W95gEBCHYNDIb3s9oHnKY_JhQAd9NPWaRSn7CuP3q5WOjMBel7NoOomn-uOUGaoa1ZTAGzVElnuEZtjJLzLB-qD1TtJDOxxKGLrQcZd_XtZuZOYHIQl76HRmpFUQFeHaOfaKPJfvTZT",
    "q": "11qH0PJgHZkOqpGHVpMIQIJ1HrCaaKru3XNGgjPrdxuqstql7eoBCBRJ9QF-Jxb4vva6_EzuAe9pB7eQSf7_LV7ieUhYOuduXaNQap9P7G2YIAsKR-QcFFNFdJwZC2_u-qINoMZW6WlmaiX_64S8iAoJyX1BX6Mfzjfnuit8gAKntGhMiL9j_HHWB9fSTQL7pXbr2ZzdLWa-3r6oPoIeDKpsR5To3Y49soS-B4ss0OBmzbuNs3kkQvCKA3Rge9D5",
    "qi": "LbaKGEPI8OvEiDYFSam3UMwqSdRiYrr7GV3_heN6ak_cz_YP5TiavZJm-rQzzB4mm-CUllqDCDqOgfq7FdLCl3c4_N88xU5j7rkI5cA5FwispyI-WTSgtpW9CvCATCJJchx2PC2H8--EfDzBMaZtqLs1rtIPPYKMQRIIkoFw5tIBodagSKbb1Aiwib1Zp5QY9POIL6G-iYRFuTG03gbWYh3T7C5NjFcF_Uvl_GL6dwzsc7MyOWdIdfrxu87thN_H"
  }
  * */

  public JWTAuthHandlerTest() {
    this.objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    this.feelEngineWrapper = new FeelEngineWrapper();
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
      jwkMap.put("kid", "c6f8386d31b98b77d83bba35a457aef4");
      jwkMap.put("use", "sig");
      jwkMap.put("alg", "RS256");
      jwkMap.put("kty", "RSA");
      jwkMap.put("key_ops", List.of("sign"));
      jwkMap.put(
          "d",
          "EiqH3SGMnz6MEelFNL7elLc3EmpUFm6Zzx1sr1fa5_LmT50TMrgksxoaoKVnfOCK8RmnLaKSKvoQZY2iz6DEYymqpZy778lEAzf7hgmFIChd1JaV2NXAPIBImmF34R3v7W37FG-UnTvgfqVFKJQkF__0iu8FJq1qw4vCtZQnoGD6oKewCURD42MUHTsosTvvL_PlgqrU3hklozzZDLFuPHdh0CEoZHj4OZKxjX2iMAnEX6kNZ3bMtxymxKCayeXXPk2DSjPu4y2EvbShx18EKbEHIqeHpiiZXBPzpraFZXsLXvSwyc16JGxNmxw0QyCOBlPZO1E6fjEv9hhsizyE-oRT_PS9nRas779iv-EQnKvEe97ERKYZm_u9Y42aJcbFrsitrUx2r4oNqTwyYD0UK560Lai4ex2XzZHPwgNSixmVtrWfFiKs_Zlqkd-R8BIzmMfCMKVoiOz-eeGbZbrEDvnZBZqPu-09qVAKW0vJ8BJ7Jgve-MggS1O_T2It-NEJ");
      jwkMap.put(
          "dp",
          "m1B_1bGWvqgD09wTvO64jZr2av6cE7STNtfbid8eDQJ69BRGIBQmNBc8lIA-Mr-LYPKSYuspXT2GDOfQY5ucmThTZXBZPuAl7NXxC3bNhV5Aq8sEblCBbQPh7wrfLYnOytRJVNloOE6DNVQ8dfuFqkWVinJMfH1XgovJqoraactUzMxAaLfrfeAYkdSx1R_pTsvrZPiyVNXJOS9Fg8CuvYlGTOxyufLr9ZXZPFG7RdJ3GX98nBsKyUdcNIDKgnEX");
      jwkMap.put(
          "dq",
          "V_ZpfWZS7iQDgpY40pryBu-CiUuXdmNDd_7K8QnmBtutKKr4lao8S6GF5-Z5MdxHNDExhTajDPVOUFGhRSg78LtOBD4Rk9XOFb9MbJCwI6FMPuJji6PX-FFfEFdmP260DXm92UrKW0KrC3aEBx5Lp8F55-walMbbie753ug-TUdIWv8zK7QJUibpRoDYLI6oXRirE1LoYNAT7IWx0P5y6bd-zOo9B6o-9zkTKZ-maZcPRr9yDLnRnuoDZY_WjRx5");
      jwkMap.put("e", "AQAB");
      jwkMap.put(
          "n",
          "0E19Jt_OljwfdqSQw3gTVLZJqe49nvhI0QwyShAXSEK_3FG79DxDD_WBxOw7ItNyoBAjFXc-7snXt1nu5uBEQe8a_65fHQ5BurV6v8t30o9IwpamXuSdIuSGlJK-yfO6ub309JXqfgSC_aNR2QuysqviqEIdUv_z3DDsMgZek5ycNnq2S5M1-raWpO5ILNGMevQg_bVnK_ZnK3I0yZQkL6PVbVrKkh9t6vHfzcxXmHE_sFY5fUQFuq5GPnRiYeU6isR3qRq01F4uAU9xNZ6uz-IGPQwgTuK51AN-lHT3fJtbkb3rRYCZgkLgSVVQfbAsvKZNIOZrFFtughZ-h6I9ZRw6PZGWl4Mud9Edup2YncGwD_ahLicNVe3OZmHASps_cELivS5lzau7J-oaORinZcsg5VWaWGl3EgIGvJhKA1550qyTX8c105ahLGAljboyV5Jc_H7uTEYadATtv7ccSSLuTJRgnA-Y7NT6q98BOiIzDmJiA-Y33QbvTG0VDka7");
      jwkMap.put(
          "p",
          "955FOk5PJ6srgwXew1oOCgYEHCfGxRWSc7dmgvvob3QNUXWF8-UpHDbIV4QcipBIcW4bX6Kcpx5H2Ed6AJXdCwwhCt2b5FU_wjIrUNpF1oKOg4nZtU-38W95gEBCHYNDIb3s9oHnKY_JhQAd9NPWaRSn7CuP3q5WOjMBel7NoOomn-uOUGaoa1ZTAGzVElnuEZtjJLzLB-qD1TtJDOxxKGLrQcZd_XtZuZOYHIQl76HRmpFUQFeHaOfaKPJfvTZT");
      jwkMap.put(
          "q",
          "11qH0PJgHZkOqpGHVpMIQIJ1HrCaaKru3XNGgjPrdxuqstql7eoBCBRJ9QF-Jxb4vva6_EzuAe9pB7eQSf7_LV7ieUhYOuduXaNQap9P7G2YIAsKR-QcFFNFdJwZC2_u-qINoMZW6WlmaiX_64S8iAoJyX1BX6Mfzjfnuit8gAKntGhMiL9j_HHWB9fSTQL7pXbr2ZzdLWa-3r6oPoIeDKpsR5To3Y49soS-B4ss0OBmzbuNs3kkQvCKA3Rge9D5");
      jwkMap.put(
          "qi",
          "LbaKGEPI8OvEiDYFSam3UMwqSdRiYrr7GV3_heN6ak_cz_YP5TiavZJm-rQzzB4mm-CUllqDCDqOgfq7FdLCl3c4_N88xU5j7rkI5cA5FwispyI-WTSgtpW9CvCATCJJchx2PC2H8--EfDzBMaZtqLs1rtIPPYKMQRIIkoFw5tIBodagSKbb1Aiwib1Zp5QY9POIL6G-iYRFuTG03gbWYh3T7C5NjFcF_Uvl_GL6dwzsc7MyOWdIdfrxu87thN_H");
      jwkMap.put("crv", "P-256");

      return Jwk.fromValues(jwkMap);
    }
  }

  static class TestES512JwkProvider implements JwkProvider {

    @Override
    public Jwk get(String keyId) {
      Map<String, Object> jwkMap = new HashMap<>();
      jwkMap.put("kid", "9d65c50cc1f5c018a8d596a1f20e7169");
      jwkMap.put("use", "sig");
      jwkMap.put("alg", "ES512");
      jwkMap.put("kty", "EC");
      jwkMap.put("key_ops", List.of("sign"));
      jwkMap.put(
          "x",
          "AM-JUuVi3dvipsKK4eVl0ounGxSWkdfWTCbz_O1Bd5ziEsklxCn0CME8r44qLcFYW-p21Zo1cngS5E7MZ2VtXJz9");
      jwkMap.put(
          "y",
          "AArN04WEtxtIvVIjjhbfnhXeXgwosrJjXGCbCmmuFx9cvk2iL5C3yYlTXz9H0keuf4KIm5XMaEzOkQNUfFaSfWks");
      jwkMap.put("crv", "P-521");

      return Jwk.fromValues(jwkMap);
    }
  }

  static class JwkProviderNoAlg implements JwkProvider {

    @Override
    public Jwk get(String keyId) {
      Map<String, Object> jwkMap = new HashMap<>();
      jwkMap.put("kid", "9d65c50cc1f5c018a8d596a1f20e7169");
      jwkMap.put("use", "sig");
      jwkMap.put("kty", "EC");
      jwkMap.put("key_ops", List.of("sign"));
      jwkMap.put(
          "x",
          "AM-JUuVi3dvipsKK4eVl0ounGxSWkdfWTCbz_O1Bd5ziEsklxCn0CME8r44qLcFYW-p21Zo1cngS5E7MZ2VtXJz9");
      jwkMap.put(
          "y",
          "AArN04WEtxtIvVIjjhbfnhXeXgwosrJjXGCbCmmuFx9cvk2iL5C3yYlTXz9H0keuf4KIm5XMaEzOkQNUfFaSfWks");
      jwkMap.put("crv", "P-521");

      return Jwk.fromValues(jwkMap);
    }
  }
}
