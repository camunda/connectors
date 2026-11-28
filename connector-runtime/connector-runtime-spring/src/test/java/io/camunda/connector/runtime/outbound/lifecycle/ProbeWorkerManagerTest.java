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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.probe.ProbeHandler;
import io.camunda.client.api.worker.probe.ProbeWorker;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1;
import io.camunda.client.api.worker.probe.ProbeWorkerBuilderStep1.ProbeWorkerBuilderStep2;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ConfigurationValidationResult;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.runtime.configuration.ConfigurationValidationProbeHandler;
import io.camunda.connector.runtime.core.FeelEvaluationResultMapper;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationRegistry;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import io.camunda.connector.runtime.core.validation.ValidationUtil;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProbeWorkerManagerTest {

  @Configuration(id = "test:a:1", name = "A")
  record ConfigA(String value) {}

  @Configuration(id = "test:b:1", name = "B")
  record ConfigB(String value) {}

  static class ValidatorA implements ConfigurationValidator<ConfigA> {
    @Override
    public ConfigurationValidationResult validate(ConfigA configuration) {
      return ConfigurationValidationResult.success();
    }
  }

  static class ValidatorB implements ConfigurationValidator<ConfigB> {
    @Override
    public ConfigurationValidationResult validate(ConfigB configuration) {
      return ConfigurationValidationResult.success();
    }
  }

  private final ProbeWorkerBuilderStep1 step1 = mock(ProbeWorkerBuilderStep1.class);
  private final ProbeWorkerBuilderStep2 step2 = mock(ProbeWorkerBuilderStep2.class);
  private final ProbeWorker worker = mock(ProbeWorker.class);

  private CamundaClient client() {
    var client = mock(CamundaClient.class);
    when(client.newProbeWorker()).thenReturn(step1);
    when(step1.probeId(anyString(), any())).thenReturn(step2);
    when(step2.probeId(anyString(), any())).thenReturn(step2);
    when(step2.open()).thenReturn(worker);
    return client;
  }

  private static ProbeWorkerManager manager(
      List<ConfigurationValidator<?>> validators, String prefix) {
    var registry = new ConfigurationValidationRegistry(validators);
    var service =
        new ConfigurationValidationService(
            registry,
            ValidationUtil.discoverDefaultValidationProviderImplementation(),
            FeelEvaluationResultMapper.create());
    return new ProbeWorkerManager(registry, service, new SecretProvider() {}, prefix);
  }

  @Test
  void opensOneWorkerWithOneProbeIdPerValidator() {
    var client = client();

    manager(List.of(new ValidatorB(), new ValidatorA()), ProbeWorkerManager.DEFAULT_PROBE_ID_PREFIX)
        .onStart(client, "default");

    verify(client, times(1)).newProbeWorker();
    var handlerA = ArgumentCaptor.forClass(ProbeHandler.class);
    var handlerB = ArgumentCaptor.forClass(ProbeHandler.class);
    verify(step1).probeId(eq("validate-configuration/test:a:1"), handlerA.capture());
    verify(step2).probeId(eq("validate-configuration/test:b:1"), handlerB.capture());
    assertThat(handlerA.getValue()).isInstanceOf(ConfigurationValidationProbeHandler.class);
    assertThat(handlerB.getValue()).isInstanceOf(ConfigurationValidationProbeHandler.class);
    verify(step2, times(1)).open();
  }

  @Test
  void usesTheConfiguredProbeIdPrefix() {
    var client = client();

    manager(List.of(new ValidatorA()), "hybrid-eu/validate-configuration/")
        .onStart(client, "default");

    verify(step1).probeId(eq("hybrid-eu/validate-configuration/test:a:1"), any());
    verify(step2).open();
  }

  @Test
  void closesTheWorkerOnStop() {
    var client = client();
    var manager = manager(List.of(new ValidatorA()), ProbeWorkerManager.DEFAULT_PROBE_ID_PREFIX);
    manager.onStart(client, "default");

    manager.onStop(client, "default");

    verify(worker).close();
  }

  @Test
  void closesOnlyTheWorkerOfTheStoppedClient() {
    var clientA = client();
    var clientB = mock(CamundaClient.class);
    var stepB1 = mock(ProbeWorkerBuilderStep1.class);
    var stepB2 = mock(ProbeWorkerBuilderStep2.class);
    var workerB = mock(ProbeWorker.class);
    when(clientB.newProbeWorker()).thenReturn(stepB1);
    when(stepB1.probeId(anyString(), any())).thenReturn(stepB2);
    when(stepB2.open()).thenReturn(workerB);
    var manager = manager(List.of(new ValidatorA()), ProbeWorkerManager.DEFAULT_PROBE_ID_PREFIX);
    manager.onStart(clientA, "a");
    manager.onStart(clientB, "b");

    manager.onStop(clientB, "b");

    verify(workerB).close();
    verify(worker, never()).close();
  }

  @Test
  void opensNoWorkerWithoutValidators() {
    var client = client();
    var manager = manager(List.of(), ProbeWorkerManager.DEFAULT_PROBE_ID_PREFIX);

    manager.onStart(client, "default");
    manager.onStop(client, "default");

    verify(client, never()).newProbeWorker();
  }

  @Test
  void closesThePreviousWorkerWhenTheSameClientStartsAgain() {
    var client = client();
    var manager = manager(List.of(new ValidatorA()), ProbeWorkerManager.DEFAULT_PROBE_ID_PREFIX);
    var secondWorker = mock(ProbeWorker.class);
    manager.onStart(client, "default");
    when(step2.open()).thenReturn(secondWorker);

    manager.onStart(client, "default");
    manager.onStop(client, "default");

    verify(worker).close();
    verify(secondWorker).close();
  }
}
