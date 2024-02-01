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
package io.camunda.connector.runtime;

import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionImporter;
import io.camunda.zeebe.client.ZeebeClient;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.availability.ReadinessStateHealthIndicator;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;

public class ConnectorsReadinessIndicator extends ReadinessStateHealthIndicator {
  private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final AtomicBoolean ready = new AtomicBoolean(false);
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final ProcessDefinitionImporter processDefinitionImporter;
  private final ZeebeClient zeebeClient;

  public ConnectorsReadinessIndicator(
      ApplicationAvailability availability,
      ZeebeClient zeebeClient,
      @Nullable ProcessDefinitionImporter processDefinitionImporter) {
    super(availability);
    this.zeebeClient = zeebeClient;
    this.processDefinitionImporter = processDefinitionImporter;
  }

  @Override
  protected AvailabilityState getState(ApplicationAvailability applicationAvailability) {
    return ready.get() ? ReadinessState.ACCEPTING_TRAFFIC : ReadinessState.REFUSING_TRAFFIC;
  }

  @PostConstruct
  private void checkReadiness() {
    if (zeebeReady() && operateImporterReadyOrDisabled()) {
      LOG.debug("Application ready");
      ready.set(true);
      executor.shutdown();
      return;
    }
    LOG.debug("Application not ready yet");
    executor.schedule(this::checkReadiness, 1, TimeUnit.SECONDS);
  }

  private boolean operateImporterReadyOrDisabled() {
    if (processDefinitionImporter == null) {
      LOG.debug("Running in outbound only mode, not checking for Operate readiness");
      return true;
    }
    return processDefinitionImporter.isReady();
  }

  private boolean zeebeReady() {
    try {
      zeebeClient.newTopologyRequest().send().join();
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
