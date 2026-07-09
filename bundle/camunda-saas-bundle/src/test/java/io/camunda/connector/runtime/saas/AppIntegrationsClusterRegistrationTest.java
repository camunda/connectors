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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class AppIntegrationsClusterRegistrationTest {

  private static final String BASE_URL = "https://app-integrations.example.com";
  private static final String API_KEY = "secret-key";
  private static final String ORG_ID = "org-1";
  private static final String CLUSTER_ID = "cluster-1";
  private static final String EXPECTED_URI =
      "https://app-integrations.example.com/api/connector/org-1/cluster-1";

  private HttpClient httpClient;
  private TaskScheduler scheduler;

  @BeforeEach
  void setUp() {
    httpClient = mock(HttpClient.class);
    scheduler = mock(TaskScheduler.class);
  }

  @Test
  void schedulesReportOnStartupWithoutBlocking() {
    registration(true).reportAvailability();

    verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    verifyNoInteractions(httpClient);
  }

  @Test
  void sendsPutWithApiKeyWhenSettingsPresent() throws Exception {
    stubResponse(200);
    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run();

    HttpRequest request = capturedRequest();
    assertThat(request.method()).isEqualTo("PUT");
    assertThat(request.uri()).hasToString(EXPECTED_URI);
    assertThat(request.headers().firstValue(API_KEY_HEADER)).contains(API_KEY);
  }

  @Test
  void sendsDeleteWhenSettingsAbsent() throws Exception {
    stubResponse(200);
    registration(false).reportAvailability();
    runScheduledTasks(1).get(0).run();

    HttpRequest request = capturedRequest();
    assertThat(request.method()).isEqualTo("DELETE");
    assertThat(request.uri()).hasToString(EXPECTED_URI);
    assertThat(request.headers().firstValue(API_KEY_HEADER)).contains(API_KEY);
  }

  @Test
  void trimsTrailingSlashesFromBaseUrl() throws Exception {
    stubResponse(200);
    new AppIntegrationsClusterRegistration(
            BASE_URL + "///", API_KEY, true, ORG_ID, CLUSTER_ID, httpClient, scheduler)
        .reportAvailability();
    runScheduledTasks(1).get(0).run();

    assertThat(capturedRequest().uri()).hasToString(EXPECTED_URI);
  }

  @Test
  void skipsWhenBaseUrlMissing() {
    new AppIntegrationsClusterRegistration(
            "", API_KEY, true, ORG_ID, CLUSTER_ID, httpClient, scheduler)
        .reportAvailability();
    verifyNoInteractions(scheduler);
    verifyNoInteractions(httpClient);
  }

  @Test
  void skipsWhenApiKeyMissing() {
    new AppIntegrationsClusterRegistration(
            BASE_URL, "  ", true, ORG_ID, CLUSTER_ID, httpClient, scheduler)
        .reportAvailability();
    verifyNoInteractions(scheduler);
    verifyNoInteractions(httpClient);
  }

  @Test
  void skipsWhenOrgIdMissing() {
    new AppIntegrationsClusterRegistration(
            BASE_URL, API_KEY, true, null, CLUSTER_ID, httpClient, scheduler)
        .reportAvailability();
    verifyNoInteractions(scheduler);
    verifyNoInteractions(httpClient);
  }

  @Test
  void skipsWhenClusterIdMissing() {
    new AppIntegrationsClusterRegistration(
            BASE_URL, API_KEY, true, ORG_ID, null, httpClient, scheduler)
        .reportAvailability();
    verifyNoInteractions(scheduler);
    verifyNoInteractions(httpClient);
  }

  @Test
  void skipsWhenBaseUrlIsMalformed() {
    new AppIntegrationsClusterRegistration(
            "http://space in url", API_KEY, true, ORG_ID, CLUSTER_ID, httpClient, scheduler)
        .reportAvailability();
    // A malformed base URL fails fast on the startup thread: nothing is scheduled or sent, and no
    // exception escapes.
    verifyNoInteractions(scheduler);
    verifyNoInteractions(httpClient);
  }

  @Test
  void reschedulesOnTransientIoError() throws Exception {
    doThrow(new IOException("transient")).when(httpClient).send(any(HttpRequest.class), any());

    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run();

    // First attempt failed -> a retry was scheduled.
    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void reschedulesOnServerError() throws Exception {
    stubResponse(503);

    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run();

    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void doesNotRescheduleOnClientError() throws Exception {
    stubResponse(404);

    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run();

    // Only the initial schedule; a 4xx is treated as permanent.
    verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    verify(httpClient, times(1)).send(any(HttpRequest.class), any());
  }

  @Test
  void doesNotRescheduleOnSuccess() throws Exception {
    stubResponse(200);

    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run();

    verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void retriesUntilItSucceeds() throws Exception {
    HttpResponse<?> ok = responseWithStatus(200);
    doThrow(new IOException("transient"))
        .doReturn(ok)
        .when(httpClient)
        .send(any(HttpRequest.class), any());

    registration(true).reportAvailability();
    List<Runnable> tasks = runScheduledTasks(1);
    tasks.get(0).run(); // fails, reschedules
    runScheduledTasks(2).get(1).run(); // succeeds, no further reschedule

    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    verify(httpClient, times(2)).send(any(HttpRequest.class), any());
  }

  private AppIntegrationsClusterRegistration registration(boolean settingsPresent) {
    return new AppIntegrationsClusterRegistration(
        BASE_URL, API_KEY, settingsPresent, ORG_ID, CLUSTER_ID, httpClient, scheduler);
  }

  private void stubResponse(int status) throws Exception {
    doReturn(responseWithStatus(status)).when(httpClient).send(any(HttpRequest.class), any());
  }

  private static HttpResponse<?> responseWithStatus(int status) {
    HttpResponse<?> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    return response;
  }

  /**
   * Captures the tasks scheduled so far (expects exactly {@code expectedCount}) without running.
   */
  private List<Runnable> runScheduledTasks(int expectedCount) {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler, times(expectedCount)).schedule(captor.capture(), any(Instant.class));
    return captor.getAllValues();
  }

  private HttpRequest capturedRequest() throws Exception {
    ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
    verify(httpClient).send(captor.capture(), any());
    return captor.getValue();
  }
}
