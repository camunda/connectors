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
package io.camunda.connector.http.client.client.apache.proxy;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.camunda.connector.http.client.client.apache.proxy.ProxyAwareHttpClient.ProxyContext;
import io.camunda.connector.http.client.client.apache.proxy.ProxyAwareHttpClient.TimeoutConfiguration;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@WireMockTest
class ProxyAwareHttpClientUserAgentTest {

  private static final String HTTP_AGENT = "http.agent";

  private String originalHttpAgent;

  @BeforeEach
  void saveProperty() {
    originalHttpAgent = System.getProperty(HTTP_AGENT);
    System.clearProperty(HTTP_AGENT);
  }

  @AfterEach
  void restoreProperty() {
    if (originalHttpAgent == null) {
      System.clearProperty(HTTP_AGENT);
    } else {
      System.setProperty(HTTP_AGENT, originalHttpAgent);
    }
  }

  @Test
  void shouldSendApacheDefaultUserAgent_whenHttpAgentIsUnset(WireMockRuntimeInfo wm)
      throws Exception {
    wm.getWireMock().register(get("/ua").willReturn(ok()));

    execute(new BasicClassicHttpRequest("GET", wm.getHttpBaseUrl() + "/ua"));

    wm.getWireMock()
        .verifyThat(
            getRequestedFor(urlEqualTo("/ua"))
                .withHeader(HttpHeaders.USER_AGENT, matching("Apache-HttpClient/5\\..*")));
    assertThat(ProxyAwareHttpClient.userAgent()).startsWith("Apache-HttpClient/");
  }

  @Test
  void shouldSendHttpAgentSystemProperty_whenSet(WireMockRuntimeInfo wm) throws Exception {
    System.setProperty(HTTP_AGENT, "my-custom-agent/1.0");
    wm.getWireMock().register(get("/ua").willReturn(ok()));

    execute(new BasicClassicHttpRequest("GET", wm.getHttpBaseUrl() + "/ua"));

    wm.getWireMock()
        .verifyThat(
            getRequestedFor(urlEqualTo("/ua"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo("my-custom-agent/1.0")));
    assertThat(ProxyAwareHttpClient.userAgent()).isEqualTo("my-custom-agent/1.0");
  }

  @Test
  void shouldPreferRequestLevelUserAgentHeader(WireMockRuntimeInfo wm) throws Exception {
    System.setProperty(HTTP_AGENT, "my-custom-agent/1.0");
    wm.getWireMock().register(get("/ua").willReturn(ok()));
    var request = new BasicClassicHttpRequest("GET", wm.getHttpBaseUrl() + "/ua");
    request.setHeader(HttpHeaders.USER_AGENT, "caller-agent/2.0");

    execute(request);

    wm.getWireMock()
        .verifyThat(
            getRequestedFor(urlEqualTo("/ua"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo("caller-agent/2.0")));
  }

  private static void execute(BasicClassicHttpRequest request) throws Exception {
    try (var client =
        new ProxyAwareHttpClient(
            new TimeoutConfiguration(10, 10), new ProxyContext("http", "localhost"), true)) {
      client.execute(
          request,
          response -> {
            EntityUtils.consume(response.getEntity());
            return null;
          });
    }
  }
}
