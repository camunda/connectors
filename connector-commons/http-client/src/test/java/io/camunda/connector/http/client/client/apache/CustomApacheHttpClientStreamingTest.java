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
package io.camunda.connector.http.client.client.apache;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.http.client.mapper.StreamingHttpResponse;
import io.camunda.connector.http.client.model.HttpClientRequest;
import io.camunda.connector.http.client.model.HttpMethod;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

@WireMockTest
class CustomApacheHttpClientStreamingTest {

  private final CustomApacheHttpClient client = new CustomApacheHttpClient();

  private HttpClientRequest request(WireMockRuntimeInfo wm) {
    HttpClientRequest request = new HttpClientRequest();
    request.setMethod(HttpMethod.GET);
    request.setUrl(wm.getHttpBaseUrl() + "/stream");
    return request;
  }

  @Test
  void executeStreaming_exposesStatusReasonHeadersAndBody(WireMockRuntimeInfo wm) throws Exception {
    stubFor(
        get("/stream")
            .willReturn(
                ok("streamed-body").withHeader("Content-Type", "text/plain").withStatus(200)));

    StreamingHttpResponse response = client.executeStreaming(request(wm));

    assertThat(response.status()).isEqualTo(200);
    assertThat(response.headers().get("Content-Type")).contains("text/plain");
    try (InputStream body = response.body()) {
      assertThat(new String(body.readAllBytes(), StandardCharsets.UTF_8))
          .isEqualTo("streamed-body");
    }
  }

  @Test
  void executeStreaming_closingBodyIsIdempotent(WireMockRuntimeInfo wm) throws Exception {
    stubFor(get("/stream").willReturn(ok("body")));

    StreamingHttpResponse response = client.executeStreaming(request(wm));
    InputStream body = response.body();
    body.readAllBytes();

    // A second close must be a no-op.
    assertThatCode(
            () -> {
              body.close();
              body.close();
            })
        .doesNotThrowAnyException();
  }

  @Test
  void executeStreaming_emptyBodyDoesNotThrow(WireMockRuntimeInfo wm) throws Exception {
    stubFor(get("/stream").willReturn(aResponse().withStatus(204)));

    StreamingHttpResponse response = client.executeStreaming(request(wm));

    assertThat(response.status()).isEqualTo(204);
    try (InputStream body = response.body()) {
      assertThat(body.readAllBytes()).isEmpty();
    }
  }
}
