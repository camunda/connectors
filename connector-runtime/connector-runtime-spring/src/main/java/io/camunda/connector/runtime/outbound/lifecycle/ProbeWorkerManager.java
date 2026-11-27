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
package io.camunda.connector.runtime.outbound.lifecycle;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep2;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.runtime.configuration.ConfigurationValidationProbeHandler;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationRegistry;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opens one probe worker per client that answers configuration validation probes, with one probe ID
 * per registered {@code ConfigurationValidator}: {@code <prefix><configuration ID>}, by default
 * {@code validate-configuration/<configuration ID>}.
 */
public class ProbeWorkerManager implements CamundaClientLifecycleAware {

  public static final String DEFAULT_PROBE_ID_PREFIX = "validate-configuration/";

  private static final Logger LOG = LoggerFactory.getLogger(ProbeWorkerManager.class);

  private final ConfigurationValidationRegistry registry;
  private final ConfigurationValidationService validationService;
  private final SecretProvider secretProvider;
  private final String probeIdPrefix;
  private final Map<CamundaClient, ProbeWorker> workersByClient = new ConcurrentHashMap<>();

  public ProbeWorkerManager(
      ConfigurationValidationRegistry registry,
      ConfigurationValidationService validationService,
      SecretProvider secretProvider,
      String probeIdPrefix) {
    this.registry = registry;
    this.validationService = validationService;
    this.secretProvider = secretProvider;
    this.probeIdPrefix = probeIdPrefix;
  }

  @Override
  public void onStart(CamundaClient client) {
    onStart(client, "default");
  }

  @Override
  public void onStop(CamundaClient client) {
    onStop(client, "default");
  }

  @Override
  public void onStart(CamundaClient client, String clientName) {
    var configurationIds = registry.configurationIds();
    if (configurationIds.isEmpty()) {
      LOG.debug("No configuration validator registered; no probe worker opened");
      return;
    }
    ProbeWorkerBuilderStep2 builder = null;
    var probeIds = new ArrayList<String>();
    for (String configurationId : configurationIds) {
      var probeId = probeIdPrefix + configurationId;
      var handler =
          new ConfigurationValidationProbeHandler(
              configurationId, validationService, secretProvider);
      builder =
          builder == null
              ? client.newProbeWorker().probeId(probeId, handler)
              : builder.probeId(probeId, handler);
      probeIds.add(probeId);
    }
    var previous = workersByClient.put(client, builder.open());
    if (previous != null) {
      previous.close();
    }
    LOG.info("Opened a probe worker on client '{}' for probe IDs {}", clientName, probeIds);
  }

  @Override
  public void onStop(CamundaClient client, String clientName) {
    var worker = workersByClient.remove(client);
    if (worker != null) {
      worker.close();
    }
  }
}
