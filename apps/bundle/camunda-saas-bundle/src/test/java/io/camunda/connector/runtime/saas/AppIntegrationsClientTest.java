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
package io.camunda.connector.runtime.saas;

import static io.camunda.connector.runtime.saas.AppIntegrationsClient.API_KEY_HEADER;
import static io.camunda.connector.runtime.saas.AppIntegrationsClient.RegistrationOutcome.DONE;
import static io.camunda.connector.runtime.saas.AppIntegrationsClient.RegistrationOutcome.RETRY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.saas.AppIntegrationsClient.RegistrationOutcome;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppIntegrationsClientTest {

  private static final String BASE_URL = "https://app-integrations.example.com";
  private static final String API_KEY = "secret-key";
  private static final String ORG_ID = "org-1";
  private static final String CLUSTER_ID = "cluster-1";
  private static final String EXPECTED_URI =
      "https://app-integrations.example.com/api/connector/org-1/cluster-1";

  private HttpClient httpClient;

  @BeforeEach
  void setUp() {
    httpClient = mock(HttpClient.class);
  }

  @Test
  void sendsPutWithApiKeyWhenSettingsPresent() throws Exception {
    stubResponse(200);

    RegistrationOutcome outcome = client().registerCluster(ORG_ID, CLUSTER_ID, true);

    HttpRequest request = capturedRequest();
    assertThat(request.method()).isEqualTo("PUT");
    assertThat(request.uri()).hasToString(EXPECTED_URI);
    assertThat(request.headers().firstValue(API_KEY_HEADER)).contains(API_KEY);
    assertThat(outcome).isEqualTo(DONE);
  }

  @Test
  void sendsDeleteWhenSettingsAbsent() throws Exception {
    stubResponse(200);

    client().registerCluster(ORG_ID, CLUSTER_ID, false);

    assertThat(capturedRequest().method()).isEqualTo("DELETE");
  }

  @Test
  void trimsTrailingSlashesFromBaseUrl() throws Exception {
    stubResponse(200);

    new AppIntegrationsClient(BASE_URL + "///", API_KEY, httpClient)
        .registerCluster(ORG_ID, CLUSTER_ID, true);

    assertThat(capturedRequest().uri()).hasToString(EXPECTED_URI);
  }

  @Test
  void successfulResponseIsDone() throws Exception {
    stubResponse(204);
    assertThat(client().registerCluster(ORG_ID, CLUSTER_ID, true)).isEqualTo(DONE);
  }

  @Test
  void clientErrorIsDoneAndNotRetried() throws Exception {
    stubResponse(404);
    assertThat(client().registerCluster(ORG_ID, CLUSTER_ID, true)).isEqualTo(DONE);
  }

  @Test
  void serverErrorIsRetryable() throws Exception {
    stubResponse(503);
    assertThat(client().registerCluster(ORG_ID, CLUSTER_ID, true)).isEqualTo(RETRY);
  }

  @Test
  void ioErrorIsRetryable() throws Exception {
    doThrow(new IOException("boom")).when(httpClient).send(any(HttpRequest.class), any());
    assertThat(client().registerCluster(ORG_ID, CLUSTER_ID, true)).isEqualTo(RETRY);
  }

  @Test
  void malformedBaseUrlIsDoneWithoutSending() {
    RegistrationOutcome outcome =
        new AppIntegrationsClient("http://space in url", API_KEY, httpClient)
            .registerCluster(ORG_ID, CLUSTER_ID, true);

    assertThat(outcome).isEqualTo(DONE);
    verifyNoInteractions(httpClient);
  }

  @Test
  void isConfiguredReflectsBaseUrlAndApiKey() {
    assertThat(new AppIntegrationsClient(BASE_URL, API_KEY, httpClient).isConfigured()).isTrue();
    assertThat(new AppIntegrationsClient("", API_KEY, httpClient).isConfigured()).isFalse();
    assertThat(new AppIntegrationsClient(BASE_URL, "  ", httpClient).isConfigured()).isFalse();
  }

  private AppIntegrationsClient client() {
    return new AppIntegrationsClient(BASE_URL, API_KEY, httpClient);
  }

  private void stubResponse(int status) throws Exception {
    HttpResponse<?> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
  }

  private HttpRequest capturedRequest() throws Exception {
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    return captor.getValue();
  }
}
