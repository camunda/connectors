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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Regression test for a review finding on PR #9012: {@code
 * WebhookExcludingFormContentFilter.shouldNotFilter} used {@code request.getRequestURI()}, which
 * includes the servlet context path -- under a non-root {@code server.servlet.context-path}, a
 * webhook path would no longer start with {@code /inbound/} and the exclusion would silently stop
 * working. Fixed by resolving the path via {@code UrlPathHelper.getPathWithinApplication}, which
 * strips the context path first.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false",
      "camunda.connector.webhook.max-request-body-bytes=8",
      "server.servlet.context-path=/connectors",
    })
class WebhookFormContentFilterExclusionUnderContextPathTest {

  @MockitoBean private CamundaClient camundaClient;

  @MockitoBean private OutboundConnectorFactory outboundConnectorFactory;

  @LocalServerPort private int port;

  @Test
  void shouldReturn404ForOversizedFormUrlEncodedPutUnderContextPath() throws Exception {
    var request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/connectors/inbound/doesNotExist"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .method("PUT", HttpRequest.BodyPublishers.ofString("field=" + "x".repeat(64)))
            .build();

    var response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    // 404, not a hang or a 500 from a fully-buffered body -- proving the exclusion still matches
    // the webhook path once the /connectors context-path prefix is correctly stripped first.
    assertThat(response.statusCode()).isEqualTo(404);
  }
}
