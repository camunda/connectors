/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.impl.feel.FeelEngineWrapper;
import io.camunda.connector.inbound.model.JWTProperties;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.Test;

public class JWTCheckerTest {

  private final ObjectMapper objectMapper;

  private final FeelEngineWrapper feelEngineWrapper;

  private static final String JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE3ODY4MjI2MTYsImlhdCI6MTY4NjgxOTAxNiwianRpIjoiMTE0ZjhjODRjNTM3MDNhYzIxMjBkMzAyNjExZTM1OGMiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.KsjyrTJdpJnnji3c57wkc6REMl-501n2Nn98xd_2wZSGwpzHtf1ocsouudJ7hm-4W1dLUHJTLYJAO9thzWtH1Yomyq029ffz5CU8B7gtcrqg9OP_QuVCOcb9KPzjA_Lc5s4SELzDrJoedR90W-nL_7BYPvhrhu9dZcH3NbcaeU_531Yqc-YhVByBX_f6MwnpXYJECNGIx9F70SHrEI58paa8KLCvDu5Kcps480YsYHKCo9k5LoSmcDBGG_-n0riWfei0wGCFcHdhdI6ag08-C109oh7Po-PQ7GVTkEJ4pFmQ7dxBxsq_X39jh8w_9XynqbTaQhbwfNZ5u0SLWEp-n2yzxYFMLONI0VtSxw4zUfMUMJFW4iZvduxe_Ui4Jlj4ZmVxa60l7Wb3k4fi6C5-3hXOvb1XngFElSdFvIC2WGlaIfDfb82Bzq41PJc8Fqm3VRVWN7y5gpADT_Y9PYvZWP98AmogEMR_-l7gCr5ICDRlDpoNcCv3vVbJ6rTLvkAC";

  private static final String JWT_WITH_ES512_ALGORITHM_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJFUzUxMiIsImtpZCI6ImZjYWYxMGJlMzdhZDhiNzQ2MWY4ZGFhYjZkMzkwYzcwIn0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE3ODY4MjI2MTYsImlhdCI6MTY4NjgxOTAxNiwianRpIjoiMTE0ZjhjODRjNTM3MDNhYzIxMjBkMzAyNjExZTM1OGMiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.AGpm312EBWHyjLDh3nd6hyKQ3xQDJCpTwYYbufQ_ZQzT0URFC24TeR_8Rc-ITrCIv6sSc1JoNFUdEt1PEpiiZ1mZAN0X4LGlrDUVvIgAR2YJbc9vFSCn7rGHvN6gQjKXYB8ZTjYefgqSusGugdKwTolpKQP-Zm_C3vp-S5cUG0oGWejX";

  private static final String WRONG_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6IjNjZDljMWM4NDU3ZjRkMDhiNDlkMDI2OGNhNWYwMDhiIn0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE2ODc3OTM4MzUsImlhdCI6MTY4Nzc5MDIzNSwianRpIjoiNmE3ZDllNDljNWViZjYzNWM2MjVjNWQwZDAxOGNmYjIiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.YP4Zw8graOY5wMJpxIZzYNN01xtOquWzT74boxMkhCdKMU_35PCoufZqUbyvNTD5YLltBe_dYe-sLuN4s-ZjeivL4ySSDtaeCd60D5JnjLq7vuC6MUd9nBHo2fIbIAwkEiWi_flCCiyzNa3Ir4KPCWxEL2cdibnjxeovUKBhnjRdf3tq4ADWrczHpf4wxZXL8aLEHzM6I5nSV6I3R9Arb6Cie-gHDfwxjGB_PoD3L5syB7izdNAMJPLlv4XHwIZ_5Pdsle546cwaZqJhmEjjHgsRJ_JEa_Xpm1zfmShHCDixkEKGfQ0JN5nYqE2JCnhlpjyWNrkqMmnAxb1AsDzwrA";

  private static final String EXPIRED_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE2ODcwMDAwMDAsImlhdCI6MTY4NzAwMDAwMCwianRpIjoiZTg0MWU1NzczZmUxN2ExNTYzNTM0ZWFhODRkOTNiNGQiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.e0w7LwLKIpeXnms1eUHuNysoqxPzvhreVLKBhtOpRgiFr60Nrmn04EXEU4YdzGW4zU9tDdc9z8xTyfouQ7ImcLAj7p74v3fsIpckHwaAvi9FRu0kPVrCsmNC8a9M7pwRJsPPCi8DReQVnR0G0mTF12m9SIIpdf6VfaJeuNsHhQB5on6md4uxZ7X5fXZz3Z9A5xp3ZjPji6nknZUyTyTNcJ_GvEzZ4Jx9svHOm6OpDjVM57D8WI_6YNwqnEMQs-JxYNoWBSoIm1V_0rvMxLltINv0G6kvHjDApxcyUAbarpYVUUe0Sm2CoefNVXZPbb-X5gabqGrlKCFOf9ovprZ9NbgpHGawrhUgrJ3-ltkwwpi4zs7i0kj3iuGBRPh_8qJhH5NRvuPJVWN4RUhnuLuxhjenbE9UGPjIkqgYdWUHQ19qCVhf52m3UdHRatKG0GG1DLH4BEDZysvpa9y112oHSvWRmIasJMC3r4hrXnV1iLLIqZz7lv3UfTtXJAjqwGyY";

  private static final String NOT_ENOUGH_PERMISSION_JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE3ODY4MjI2MTYsImlhdCI6MTY4NjgxOTAxNiwianRpIjoiMTE0ZjhjODRjNTM3MDNhYzIxMjBkMzAyNjExZTM1OGMiLCJyb2xlcyI6WyJ1c2VyIl19.Tcicz2XdMXI4Kios6fqND_gmg5DdD0U_VdraGSh6Qylv_PJYWCDXsGCbt7GofDMYML60tqikj-LvWPeZ7O-rS8Fy0jke3a866AAj1PA0Pbf1_jYJIMdiuhK1F983RDRNPNVSjPWPqKWftmDAX-S1_k2zmX3yUPakFzlAvtF7emue9K-lueJwi3x_0raq3k4YtQYfqV9Dt9kDv-S51wjnvnhJSaKu77uYYZjH92ud-OVh-AkBoH7XC6-W3WUpKXKpGQO4QkeVnTSAuXOMLw9Yn1v-rtiS0zJ9WknyydAeg9KTLZtORjgXji4QR1VqCoCxt3LvHA7PHNuIevDw4L5aMdNMRMpN0urCAegoPWYQ011n15yMD_7GfC4wlDK9XyNsWjilVoxoZIP8QhZh1IoH1XDd3YjbmIFC04yYmV-jNRS8TOzrvd4iQOmKzT7E4n58JNB9OWONKYDMbtihSy9zCpufOfVjUmItBxYFJyd_sWtOtN3gtL4Ru6Y_IKa8Ahdm";

  /* JWT token content *
  {
    "iss": "https://idp.local",
    "aud": "api1",
    "sub": "5be86359073c434bad2da3932222dabe",
    "client_id": "my_client_app",
    "exp": 1786822616,
    "iat": 1686819016,
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

  public JWTCheckerTest() {
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
    this.feelEngineWrapper = new FeelEngineWrapper();
  }

  @Test
  public void jwtCheckSuccessTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            List.of("admin"),
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            "https://mockUrl.com");
    var headers = Map.of("Authorization", "Bearer " + JWT_TOKEN);

    // when
    boolean verificationResult =
        JWTChecker.verify(jwtProperties, headers, jwkProvider, objectMapper);

    // then
    assertTrue(verificationResult);
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
            List.of("admin"),
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            "https://mockUrl.com");
    var headers = Map.of("Authorization", "Bearer " + JWT_WITH_ES512_ALGORITHM_TOKEN);

    // when
    boolean verificationResult =
        JWTChecker.verify(jwtProperties, headers, jwkProvider, objectMapper);

    // then
    assertTrue(verificationResult);
  }

  @Test
  public void jwtCheckWrongTokenTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            List.of("admin"),
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            "https://mockUrl.com");
    var headers = Map.of("Authorization", "Bearer " + WRONG_JWT_TOKEN);

    // when
    boolean verificationResult =
        JWTChecker.verify(jwtProperties, headers, jwkProvider, objectMapper);

    // then
    assertFalse(verificationResult);
  }

  @Test
  public void jwtCheckTokenExpiredTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            List.of("admin"),
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            "https://mockUrl.com");
    var headers = Map.of("Authorization", "Bearer " + EXPIRED_JWT_TOKEN);

    // when
    boolean verificationResult =
        JWTChecker.verify(jwtProperties, headers, jwkProvider, objectMapper);

    // then
    assertFalse(verificationResult);
  }

  @Test
  public void jwtCheckTokenNotEnoughPermissionTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            List.of("admin"),
            getRoleExpressionFunction("=if admin = true then [\"admin\"] else roles"),
            "https://mockUrl.com");
    var headers = Map.of("Authorization", "Bearer " + NOT_ENOUGH_PERMISSION_JWT_TOKEN);

    // when
    boolean verificationResult =
        JWTChecker.verify(jwtProperties, headers, jwkProvider, objectMapper);

    // then
    assertFalse(verificationResult);
  }

  @Test
  public void jwtCheckWrongRoleExpressionTest() {
    // given
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties =
        new JWTProperties(
            List.of("admin"),
            getRoleExpressionFunction(
                "=if admin = true then [\"wrongPermission\"] else wrongPermission"),
            "https://mockUrl.com");
    var headers = Map.of("Authorization", "Bearer " + JWT_TOKEN);

    // when
    boolean verificationResult =
        JWTChecker.verify(jwtProperties, headers, jwkProvider, objectMapper);

    // then
    assertFalse(verificationResult);
  }

  @Test
  public void jwtCheckWithOutRoles() {
    // given jwt, check only signature
    JwkProvider jwkProvider = new TestJwkProvider();
    JWTProperties jwtProperties = new JWTProperties(null, null, "https://mockUrl.com");
    var headers = Map.of("Authorization", "Bearer " + JWT_TOKEN);

    // when
    boolean verificationResult =
        JWTChecker.verify(jwtProperties, headers, jwkProvider, objectMapper);

    // then
    assertTrue(verificationResult);
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
      jwkMap.put("kid", "fcaf10be37ad8b7461f8daab6d390c70");
      jwkMap.put("use", "sig");
      jwkMap.put("alg", "ES512");
      jwkMap.put("kty", "EC");
      jwkMap.put("key_ops", List.of("sign"));
      jwkMap.put(
          "x",
          "AKbVN_7jvuvwwC4AwG5-ZswrTqhRJg-TfSfiU6eQ7N13Cr8WpnrgZZu0_4xKRKPaRExABT8-IgqtXItFhLSz5IWO");
      jwkMap.put(
          "y",
          "ATgwQMO8XghJ7gi7XpoUpjzl73B0r2lsEDewljK7pi__yZB-TBa3sixngFVLVpAw9tEYnQbPvCcqZ2PNfpE5ZDs-");
      jwkMap.put("crv", "P-521");

      return Jwk.fromValues(jwkMap);
    }
  }
}
