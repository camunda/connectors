/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.box;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.box.sdkgen.box.developertokenauth.BoxDeveloperTokenAuth;
import com.box.sdkgen.box.errors.BoxAPIError;
import com.box.sdkgen.box.errors.RequestInfo;
import com.box.sdkgen.box.errors.ResponseInfo;
import com.box.sdkgen.client.BoxClient;
import com.box.sdkgen.networking.network.NetworkSession;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.error.ConnectorException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class BoxDownloadErrorTest {

  private static BoxAPIError error(String bodyJson) {
    return error(404, bodyJson);
  }

  private static BoxAPIError error(int status, String bodyJson) {
    var response = new ResponseInfo(status, Map.of());
    if (bodyJson != null) {
      try {
        response.body = new ObjectMapper().readTree(bodyJson);
      } catch (JsonProcessingException e) {
        throw new IllegalArgumentException(e);
      }
      response.code = "not_found";
    }
    return new BoxAPIError(
        "Status " + status + "; Request ID: r1",
        new RequestInfo("GET", "https://api.box.com", Map.of(), Map.of()),
        response);
  }

  @Test
  void surfacesBoxDiagnosticsWhenTheBinaryDownloadErrorHasNoBody() {
    var calls = new AtomicInteger();
    var client =
        new BoxClient.Builder(new BoxDeveloperTokenAuth("token"))
            .networkSession(
                new NetworkSession()
                    .withNetworkClient(
                        options -> {
                          if (calls.getAndIncrement() == 0) {
                            throw error(null);
                          }
                          throw error("{\"code\":\"not_found\",\"message\":\"Not Found\"}");
                        }))
            .build();

    assertThatThrownBy(() -> BoxUtil.download("123", client))
        .satisfies(
            e -> {
              var translated = BoxErrors.translate((RuntimeException) e);
              assertThat(translated).isInstanceOf(ConnectorException.class);
              assertThat(translated.getMessage()).contains("HTTP 404").contains("Not Found");
            });
    assertThat(calls).hasValue(2);
  }

  @Test
  void keepsTheOriginalDownloadErrorWhenTheDiagnosticLookupFailsDifferently() {
    var calls = new AtomicInteger();
    var client =
        new BoxClient.Builder(new BoxDeveloperTokenAuth("token"))
            .networkSession(
                new NetworkSession()
                    .withNetworkClient(
                        options -> {
                          if (calls.getAndIncrement() == 0) {
                            throw error(403, null);
                          }
                          throw error(429, "{\"code\":\"rate_limit_exceeded\"}");
                        }))
            .build();

    assertThatThrownBy(() -> BoxUtil.download("123", client))
        .satisfies(
            e -> {
              var translated = (RuntimeException) e.getCause();
              assertThat(translated).isInstanceOf(BoxAPIError.class);
              assertThat(((BoxAPIError) translated).getResponseInfo().getStatusCode())
                  .isEqualTo(403);
            });
    assertThat(calls).hasValue(2);
  }
}
