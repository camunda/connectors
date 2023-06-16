package io.camunda.connector.inbound.signature;

import static org.junit.Assert.assertTrue;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.inbound.authorization.JWTChecker;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.utils.ObjectMapperSupplier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class JWTCheckerTest {

  private final ObjectMapper objectMapper;

  private static final String JWT_TOKEN =
      "eyJ0eXAiOiJhdCtqd3QiLCJhbGciOiJSUzI1NiIsImtpZCI6ImM2ZjgzODZkMzFiOThiNzdkODNiYmEzNWE0NTdhZWY0In0.eyJpc3MiOiJodHRwczovL2lkcC5sb2NhbCIsImF1ZCI6ImFwaTEiLCJzdWIiOiI1YmU4NjM1OTA3M2M0MzRiYWQyZGEzOTMyMjIyZGFiZSIsImNsaWVudF9pZCI6Im15X2NsaWVudF9hcHAiLCJleHAiOjE3ODY4MjI2MTYsImlhdCI6MTY4NjgxOTAxNiwianRpIjoiMTE0ZjhjODRjNTM3MDNhYzIxMjBkMzAyNjExZTM1OGMiLCJyb2xlcyI6WyJhZG1pbiIsInN1cGVyYWRtaW4iXSwiYWRtaW4iOnRydWV9.KsjyrTJdpJnnji3c57wkc6REMl-501n2Nn98xd_2wZSGwpzHtf1ocsouudJ7hm-4W1dLUHJTLYJAO9thzWtH1Yomyq029ffz5CU8B7gtcrqg9OP_QuVCOcb9KPzjA_Lc5s4SELzDrJoedR90W-nL_7BYPvhrhu9dZcH3NbcaeU_531Yqc-YhVByBX_f6MwnpXYJECNGIx9F70SHrEI58paa8KLCvDu5Kcps480YsYHKCo9k5LoSmcDBGG_-n0riWfei0wGCFcHdhdI6ag08-C109oh7Po-PQ7GVTkEJ4pFmQ7dxBxsq_X39jh8w_9XynqbTaQhbwfNZ5u0SLWEp-n2yzxYFMLONI0VtSxw4zUfMUMJFW4iZvduxe_Ui4Jlj4ZmVxa60l7Wb3k4fi6C5-3hXOvb1XngFElSdFvIC2WGlaIfDfb82Bzq41PJc8Fqm3VRVWN7y5gpADT_Y9PYvZWP98AmogEMR_-l7gCr5ICDRlDpoNcCv3vVbJ6rTLvkAC";

  /* *
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

  public JWTCheckerTest() {
    this.objectMapper = ObjectMapperSupplier.getMapperInstance();
  }

  @Test
  public void jwtCheckTest() throws Exception {
    // given
    WebhookProcessingPayload payload = new TestWebhookProcessingPayload();
    ProcessCorrelationPoint correlationPoint =
        new StartEventCorrelationPoint(1l, "bpmnProcessId", 1);
    Map<String, String> properties = new HashMap<>();
    properties.put("inbound.type", "webhook");
    properties.put("inbound.context", "context");
    properties.put("inbound.jwkUrl", "jwkUrl");
    properties.put("inbound.jwtRolePath", "if admin = true then [\"admin\"] else roles");
    properties.put("inbound.requiredPermissions", "admin");
    InboundConnectorProperties inboundProperties =
        new InboundConnectorProperties(
            correlationPoint, properties, "bpmnProcessId", 1, 1l, "elementId");
    WebhookConnectorProperties webhookConnectorProperties =
        new WebhookConnectorProperties(inboundProperties);
    JwkProvider jwkProvider = new TestJwkProvider();

    // when
    boolean verificationResult =
        JWTChecker.verify(payload, webhookConnectorProperties, jwkProvider, objectMapper);

    // then
    assertTrue(verificationResult);
  }

  // TODO : test token expired

  // TODO: test not enough permission

  class TestJwkProvider implements JwkProvider {

    @Override
    public Jwk get(String keyId) {
      Map<String, Object> jwkMap = new HashMap<>();
      jwkMap.put("kid", "c6f8386d31b98b77d83bba35a457aef4");
      jwkMap.put("use", "sig");
      jwkMap.put("alg", "RS256");
      jwkMap.put("kty", "RSA");
      jwkMap.put("key_ops", Arrays.asList("sign"));
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

  class TestWebhookProcessingPayload implements WebhookProcessingPayload {

    @Override
    public String method() {
      // omitted intentionally
      return null;
    }

    @Override
    public Map<String, String> headers() {
      Map<String, String> headers = new HashMap<>();
      headers.put("Authorization", "Bearer " + JWT_TOKEN);
      return headers;
    }

    @Override
    public Map<String, String> params() {
      // omitted intentionally
      return null;
    }

    @Override
    public byte[] rawBody() {
      // omitted intentionally
      return new byte[0];
    }
  }
}
