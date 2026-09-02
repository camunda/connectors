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

import io.camunda.connector.runtime.saas.AppIntegrationsClient.RegistrationOutcome;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Reports this SaaS cluster's App Integrations connector availability when the runtime starts.
 *
 * <p>On {@link ApplicationReadyEvent} it schedules a one-time task (off the startup thread) that
 * delegates the actual HTTP call to {@link AppIntegrationsClient}. When the client reports a
 * transient failure the task reschedules itself with a capped exponential backoff, so the runtime
 * keeps trying until the report succeeds (or fails permanently). Nothing here can prevent the
 * runtime from starting.
 *
 * <p>The report registers the cluster when the App Integrations connector is provisioned for it and
 * deregisters it otherwise. "Settings present" is detected by the presence of the connector's
 * per-cluster OAuth client credentials.
 */
@Component
@Profile("!test")
public class AppIntegrationsClusterRegistration {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AppIntegrationsClusterRegistration.class);
  private static final Duration INITIAL_RETRY_DELAY = Duration.ofSeconds(5);
  private static final Duration MAX_RETRY_DELAY = Duration.ofMinutes(5);

  private final AppIntegrationsClient client;
  private final TaskScheduler taskScheduler;
  private final boolean settingsPresent;
  private final String organizationId;
  private final String clusterId;
  private final Duration initialRetryDelay;
  private final Duration maxRetryDelay;

  @Autowired
  public AppIntegrationsClusterRegistration(
      AppIntegrationsClient client,
      TaskScheduler taskScheduler,
      @Value("${APP_INTEGRATIONS_OAUTH_CLIENT_ID:}") String oauthClientId,
      @Value("${APP_INTEGRATIONS_OAUTH_CLIENT_SECRET:}") String oauthClientSecret,
      @Value("${camunda.connector.cloud.organization.id:}") String organizationId,
      @Value("${camunda.client.cloud.clusterId:}") String clusterId) {
    this(
        client,
        taskScheduler,
        // The App Integrations connector is provisioned for this cluster when its per-cluster OAuth
        // client credentials are present.
        isPresent(oauthClientId) && isPresent(oauthClientSecret),
        organizationId,
        clusterId,
        INITIAL_RETRY_DELAY,
        MAX_RETRY_DELAY);
  }

  AppIntegrationsClusterRegistration(
      AppIntegrationsClient client,
      TaskScheduler taskScheduler,
      boolean settingsPresent,
      String organizationId,
      String clusterId) {
    // Convenience for tests: short retry delays so the backoff numbers stay small.
    this(
        client,
        taskScheduler,
        settingsPresent,
        organizationId,
        clusterId,
        Duration.ofMillis(1),
        Duration.ofMillis(10));
  }

  AppIntegrationsClusterRegistration(
      AppIntegrationsClient client,
      TaskScheduler taskScheduler,
      boolean settingsPresent,
      String organizationId,
      String clusterId,
      Duration initialRetryDelay,
      Duration maxRetryDelay) {
    this.client = client;
    this.taskScheduler = taskScheduler;
    this.settingsPresent = settingsPresent;
    this.organizationId = organizationId;
    this.clusterId = clusterId;
    this.initialRetryDelay = initialRetryDelay;
    this.maxRetryDelay = maxRetryDelay;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void reportAvailability() {
    if (!client.isConfigured() || !isPresent(organizationId) || !isPresent(clusterId)) {
      LOGGER.info(
          "Skipping App Integrations cluster registration: endpoint configuration, organization id "
              + "or cluster id is not available");
      return;
    }
    taskScheduler.schedule(() -> attempt(initialRetryDelay), Instant.now());
  }

  private void attempt(Duration retryDelay) {
    if (client.registerCluster(organizationId, clusterId, settingsPresent)
        == RegistrationOutcome.RETRY) {
      var doubled = retryDelay.multipliedBy(2);
      var nextDelay = doubled.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : doubled;
      taskScheduler.schedule(() -> attempt(nextDelay), Instant.now().plus(retryDelay));
    }
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
