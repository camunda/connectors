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

import static io.camunda.connector.runtime.saas.AppIntegrationsClient.RegistrationOutcome.DONE;
import static io.camunda.connector.runtime.saas.AppIntegrationsClient.RegistrationOutcome.RETRY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

class AppIntegrationsClusterRegistrationTest {

  private static final String ORG_ID = "org-1";
  private static final String CLUSTER_ID = "cluster-1";

  private AppIntegrationsClient client;
  private TaskScheduler scheduler;

  @BeforeEach
  void setUp() {
    client = mock(AppIntegrationsClient.class);
    scheduler = mock(TaskScheduler.class);
    when(client.isConfigured()).thenReturn(true);
  }

  @Test
  void schedulesReportOnStartupWithoutCallingClient() {
    registration(true).reportAvailability();

    verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    verify(client, never()).registerCluster(any(), any(), anyBoolean());
  }

  @Test
  void skipsWhenClientNotConfigured() {
    when(client.isConfigured()).thenReturn(false);

    registration(true).reportAvailability();

    verifyNoInteractions(scheduler);
  }

  @Test
  void skipsWhenOrgIdMissing() {
    new AppIntegrationsClusterRegistration(client, scheduler, true, null, CLUSTER_ID)
        .reportAvailability();
    verifyNoInteractions(scheduler);
  }

  @Test
  void skipsWhenClusterIdMissing() {
    new AppIntegrationsClusterRegistration(client, scheduler, true, ORG_ID, "  ")
        .reportAvailability();
    verifyNoInteractions(scheduler);
  }

  @Test
  void passesSettingsPresentToClient() {
    when(client.registerCluster(any(), any(), anyBoolean())).thenReturn(DONE);

    registration(false).reportAvailability();
    runScheduledTasks(1).get(0).run();

    verify(client).registerCluster(ORG_ID, CLUSTER_ID, false);
  }

  @Test
  void reschedulesWhenClientReportsRetry() {
    when(client.registerCluster(eq(ORG_ID), eq(CLUSTER_ID), anyBoolean())).thenReturn(RETRY);

    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run();

    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void doesNotRescheduleWhenClientReportsDone() {
    when(client.registerCluster(eq(ORG_ID), eq(CLUSTER_ID), anyBoolean())).thenReturn(DONE);

    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run();

    verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
  }

  @Test
  void retriesUntilDone() {
    when(client.registerCluster(eq(ORG_ID), eq(CLUSTER_ID), anyBoolean()))
        .thenReturn(RETRY)
        .thenReturn(DONE);

    registration(true).reportAvailability();
    runScheduledTasks(1).get(0).run(); // RETRY -> reschedules
    runScheduledTasks(2).get(1).run(); // DONE -> no further reschedule

    verify(scheduler, times(2)).schedule(any(Runnable.class), any(Instant.class));
    verify(client, times(2)).registerCluster(ORG_ID, CLUSTER_ID, true);
  }

  private AppIntegrationsClusterRegistration registration(boolean settingsPresent) {
    return new AppIntegrationsClusterRegistration(
        client, scheduler, settingsPresent, ORG_ID, CLUSTER_ID);
  }

  /** Captures the tasks scheduled so far (expects exactly {@code expectedCount}). */
  private List<Runnable> runScheduledTasks(int expectedCount) {
    ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler, times(expectedCount)).schedule(captor.capture(), any(Instant.class));
    return captor.getAllValues();
  }
}
