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
package io.camunda.connector.e2e.agenticai;

import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.process.test.api.CamundaAssert;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections4.MapUtils;
import org.awaitility.Awaitility;

public final class TestUtil {

  private TestUtil() {}

  public static void postWithDelay(String webhookUrl, String payload, long delayInMillis) {
    postWithDelay(webhookUrl, payload, Map.of(), delayInMillis);
  }

  public static void postWithDelay(
      String url, String payload, Map<String, String> headers, long delayInMillis) {
    CompletableFuture.delayedExecutor(delayInMillis, TimeUnit.MILLISECONDS)
        .execute(
            () -> {
              try (var client = HttpClient.newHttpClient()) {
                HttpRequest.Builder builder =
                    HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload));
                if (MapUtils.isNotEmpty(headers)) {
                  headers.forEach(builder::header);
                }
                var request = builder.build();
                client.send(request, HttpResponse.BodyHandlers.discarding());
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  public static void waitForElementActivation(ZeebeTest zeebeTest, String elementId) {
    Awaitility.with()
        .pollInSameThread()
        .await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                CamundaAssert.assertThat(zeebeTest.getProcessInstanceEvent())
                    .hasActiveElement(elementId, 1));
  }
}
