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
 * Request-level test for the multipart size-limit gap flagged in review on PR #9012: {@code
 * DispatcherServlet.checkMultipart()} parses multipart bodies (and enforces {@code
 * spring.servlet.multipart.max-file-size}/{@code max-request-size}) before the handler runs, so
 * this needs a real embedded server — a {@code MockHttpServletRequest} doesn't enforce
 * container-level multipart size limits at all, and this repo's {@code MockMvc}-based webhook tests
 * call the controller method directly, bypassing {@code checkMultipart()} entirely.
 */
@SpringBootTest(
    classes = TestConnectorRuntimeApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.enabled=false",
      "spring.servlet.multipart.max-file-size=1KB",
      "spring.servlet.multipart.max-request-size=1KB",
    })
class WebhookMultipartSizeLimitTest {

  @MockitoBean private CamundaClient camundaClient;

  @MockitoBean private OutboundConnectorFactory outboundConnectorFactory;

  @LocalServerPort private int port;

  @Test
  void shouldReturn413WithEmptyBodyWhenMultipartFileExceedsConfiguredLimit() throws Exception {
    // The size check happens in checkMultipart(), before routing, so no webhook needs to be
    // registered for this: an unregistered path proves the same point as a registered one.
    String boundary = "test-boundary-268";
    String oversizedFileContent = "x".repeat(2000); // > the 1KB max-file-size configured above
    String body =
        "--"
            + boundary
            + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"payload.bin\"\r\n"
            + "Content-Type: application/octet-stream\r\n\r\n"
            + oversizedFileContent
            + "\r\n--"
            + boundary
            + "--\r\n";

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + port + "/inbound/doesNotExist"))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body()).isEmpty();
  }
}
