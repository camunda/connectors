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

import static io.camunda.connector.runtime.saas.AppIntegrationsClusterRegistration.API_KEY_HEADER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppIntegrationsClusterRegistrationTest {

  private static final String BASE_URL = "https://app-integrations.example.com";
  private static final String API_KEY = "secret-key";
  private static final String ORG_ID = "org-1";
  private static final String CLUSTER_ID = "cluster-1";
  private static final String EXPECTED_URI =
      "https://app-integrations.example.com/api/connector/org-1/cluster-1";

  private HttpClient httpClient;

  @BeforeEach
  void setUp() throws Exception {
    httpClient = mock(HttpClient.class);
    HttpResponse<?> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
  }

  @Test
  void sendsPutWithApiKeyWhenSettingsPresent() throws Exception {
    registration(true).reportAvailability();

    HttpRequest request = capturedRequest();
    assertThat(request.method()).isEqualTo("PUT");
    assertThat(request.uri()).hasToString(EXPECTED_URI);
    assertThat(request.headers().firstValue(API_KEY_HEADER)).contains(API_KEY);
  }

  @Test
  void sendsDeleteWhenSettingsAbsent() throws Exception {
    registration(false).reportAvailability();

    HttpRequest request = capturedRequest();
    assertThat(request.method()).isEqualTo("DELETE");
    assertThat(request.uri()).hasToString(EXPECTED_URI);
    assertThat(request.headers().firstValue(API_KEY_HEADER)).contains(API_KEY);
  }

  @Test
  void skipsWhenBaseUrlMissing() {
    new AppIntegrationsClusterRegistration("", API_KEY, true, ORG_ID, CLUSTER_ID, httpClient)
        .reportAvailability();
    verifyNoInteractions(httpClient);
  }

  @Test
  void skipsWhenApiKeyMissing() {
    new AppIntegrationsClusterRegistration(BASE_URL, "  ", true, ORG_ID, CLUSTER_ID, httpClient)
        .reportAvailability();
    verifyNoInteractions(httpClient);
  }

  @Test
  void skipsWhenOrgIdMissing() {
    new AppIntegrationsClusterRegistration(BASE_URL, API_KEY, true, null, CLUSTER_ID, httpClient)
        .reportAvailability();
    verifyNoInteractions(httpClient);
  }

  @Test
  void skipsWhenClusterIdMissing() {
    new AppIntegrationsClusterRegistration(BASE_URL, API_KEY, true, ORG_ID, null, httpClient)
        .reportAvailability();
    verifyNoInteractions(httpClient);
  }

  @Test
  void trimsTrailingSlashesFromBaseUrl() throws Exception {
    new AppIntegrationsClusterRegistration(
            BASE_URL + "///", API_KEY, true, ORG_ID, CLUSTER_ID, httpClient)
        .reportAvailability();
    assertThat(capturedRequest().uri()).hasToString(EXPECTED_URI);
  }

  @Test
  void doesNotThrowWhenHttpCallFails() throws Exception {
    doThrow(new IOException("boom")).when(httpClient).send(any(HttpRequest.class), any());
    assertThatCode(() -> registration(true).reportAvailability()).doesNotThrowAnyException();
  }

  private AppIntegrationsClusterRegistration registration(boolean settingsPresent) {
    return new AppIntegrationsClusterRegistration(
        BASE_URL, API_KEY, settingsPresent, ORG_ID, CLUSTER_ID, httpClient);
  }

  private HttpRequest capturedRequest() throws Exception {
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    return captor.getValue();
  }
}
