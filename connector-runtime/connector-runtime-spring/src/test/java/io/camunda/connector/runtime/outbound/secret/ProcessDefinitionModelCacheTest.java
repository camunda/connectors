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
package io.camunda.connector.runtime.outbound.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.operate.exception.OperateException;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProcessDefinitionModelCacheTest {

  private static final BpmnModelInstance SIMPLE_MODEL =
      Bpmn.readModelFromStream(
          new java.io.ByteArrayInputStream(
              """
              <?xml version="1.0" encoding="UTF-8"?>
              <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
                <bpmn:process id="proc" isExecutable="true">
                  <bpmn:startEvent id="start" />
                </bpmn:process>
              </bpmn:definitions>
              """
                  .getBytes(java.nio.charset.StandardCharsets.UTF_8)));

  private CamundaOperateClient clientReturning(BpmnModelInstance model) throws OperateException {
    var client = mock(CamundaOperateClient.class);
    when(client.getProcessDefinitionModel(anyLong())).thenReturn(model);
    return client;
  }

  @Test
  void getModel_returnsTheFetchedModel() throws OperateException {
    var client = clientReturning(SIMPLE_MODEL);
    var cache = new ProcessDefinitionModelCache(client, Caffeine.newBuilder().build());

    var model = cache.getModel(42L, Instant.now().plusSeconds(30));

    assertThat(model.getDefinitions().getId()).isEqualTo("defs");
  }

  @Test
  void getModel_fetchesOnlyOncePerProcessDefinitionKeyAcrossRepeatedCalls()
      throws OperateException {
    var client = clientReturning(SIMPLE_MODEL);
    var cache = new ProcessDefinitionModelCache(client, Caffeine.newBuilder().build());
    var deadline = Instant.now().plusSeconds(30);

    cache.getModel(42L, deadline);
    cache.getModel(42L, deadline);

    verify(client, times(1)).getProcessDefinitionModel(42L);
  }

  @Test
  void getModel_noCamundaOperateClient_throwsProcessDefinitionModelUnavailableException() {
    var cache = new ProcessDefinitionModelCache(null, Caffeine.newBuilder().build());

    assertThatThrownBy(() -> cache.getModel(42L, Instant.now().plusSeconds(30)))
        .isInstanceOf(ProcessDefinitionModelUnavailableException.class)
        .hasMessageContaining("No CamundaOperateClient available");
  }

  @Test
  void getModel_deadlineAlreadyElapsed_throwsWithoutAttemptingFetch() throws OperateException {
    var client = mock(CamundaOperateClient.class);
    var cache = new ProcessDefinitionModelCache(client, Caffeine.newBuilder().build());

    assertThatThrownBy(() -> cache.getModel(42L, Instant.now().minusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    verify(client, times(0)).getProcessDefinitionModel(anyLong());
  }

  @Test
  void getModel_operateLookupFails_wrapsTheOperateExceptionExactlyOnce() throws OperateException {
    var client = mock(CamundaOperateClient.class);
    when(client.getProcessDefinitionModel(42L)).thenThrow(new OperateException("404"));
    var cache =
        new ProcessDefinitionModelCache(
            client, Caffeine.newBuilder().build(), Duration.ofMillis(1));

    assertThatThrownBy(() -> cache.getModel(42L, Instant.now().plusSeconds(30)))
        .isInstanceOf(ProcessDefinitionModelLookupException.class)
        .hasCauseInstanceOf(OperateException.class);
  }

  @Test
  void getModel_operateLookupTransientlyFails_retriesAndSucceeds() throws OperateException {
    var client = mock(CamundaOperateClient.class);
    when(client.getProcessDefinitionModel(42L))
        .thenThrow(new OperateException("not found (yet)"))
        .thenThrow(new OperateException("not found (yet)"))
        .thenReturn(SIMPLE_MODEL);
    var cache =
        new ProcessDefinitionModelCache(
            client, Caffeine.newBuilder().build(), Duration.ofMillis(1));

    var model = cache.getModel(42L, Instant.now().plusSeconds(30));

    assertThat(model.getDefinitions().getId()).isEqualTo("defs");
    verify(client, times(3)).getProcessDefinitionModel(42L);
  }
}
