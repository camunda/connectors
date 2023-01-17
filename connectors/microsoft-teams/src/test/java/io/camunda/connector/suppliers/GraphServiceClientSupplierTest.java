/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.connector.BaseTest;
import io.camunda.connector.model.authentication.RefreshTokenAuthentication;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GraphServiceClientSupplierTest extends BaseTest {

  private GraphServiceClientSupplier supplier;
  @Mock private OkHttpClient okHttpClient;
  @Mock private Call call;
  private RefreshTokenAuthentication authentication;
  private Response response;
  private Request request;

  @BeforeEach
  public void init() {
    supplier = new GraphServiceClientSupplier(okHttpClient);
    authentication = new RefreshTokenAuthentication();
    authentication.setToken(ActualValue.Authentication.REFRESH_TOKEN);
    authentication.setClientSecret(ActualValue.Authentication.CLIENT_SECRET);
    authentication.setClientId(ActualValue.Authentication.CLIENT_ID);
    authentication.setTenantId(ActualValue.Authentication.TENANT_ID);
    request = new Request.Builder().url("https://url.com").build();
    when(okHttpClient.newCall(any(Request.class))).thenReturn(call);
  }

  @Test
  public void buildAndGetGraphServiceClient_shouldThrowExceptionWhenResponseIsNotSuccess()
      throws IOException {
    // Given
    response =
        new Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(401)
            .message("bad request")
            .body(
                ResponseBody.create(
                    "{\n"
                        + "  \"access_token\": \""
                        + ActualValue.Authentication.BEARER_TOKEN
                        + "\",\n"
                        + "}",
                    MediaType.get("application/json; charset=utf-8")))
            .build();
    when(call.execute()).thenReturn(response);

    // When and Then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> supplier.buildAndGetGraphServiceClient(authentication),
            "RuntimeException was expected");
    assertThat(thrown.getMessage()).contains("bad request");
  }

  @Test
  public void buildAndGetGraphServiceClient_shouldThrowExceptionWhenResponseBodyIsWrong()
      throws IOException {
    // Given
    response =
        new Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_2)
            .code(200)
            .message("")
            .body(
                ResponseBody.create(
                    "bad json response", MediaType.get("application/json; charset=utf-8")))
            .build();
    when(call.execute()).thenReturn(response);

    // When and Then
    RuntimeException thrown =
        assertThrows(
            RuntimeException.class,
            () -> supplier.buildAndGetGraphServiceClient(authentication),
            "RuntimeException was expected");
    assertThat(thrown.getMessage()).contains("Error while parse refresh token response");
  }
}
