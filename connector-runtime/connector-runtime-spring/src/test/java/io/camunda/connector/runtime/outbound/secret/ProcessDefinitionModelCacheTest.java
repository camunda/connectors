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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;

class ProcessDefinitionModelCacheTest {

  private static final String SIMPLE_PROCESS_XML =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="proc" isExecutable="true">
          <bpmn:startEvent id="start" />
        </bpmn:process>
      </bpmn:definitions>
      """;

  private CamundaClient clientReturning(String xml) {
    var client = mock(CamundaClient.class);
    var request = mock(ProcessDefinitionGetXmlRequest.class);
    when(client.newProcessDefinitionGetXmlRequest(anyLong())).thenReturn(request);
    when(request.execute()).thenReturn(xml);
    return client;
  }

  @Test
  void parsesTheFetchedXmlIntoAModelInstance() {
    var client = clientReturning(SIMPLE_PROCESS_XML);
    var cache =
        new ProcessDefinitionModelCache(
            "tenant-a", client, new ConcurrentMapCache("models"), Duration.ofMillis(1));

    var model = cache.getModel(42L, Instant.now().plusSeconds(30));

    assertThat(model.getDefinitions().getId()).isEqualTo("defs");
  }

  @Test
  void parsesNonAsciiElementNamesWithoutCorruptionRegardlessOfThePlatformDefaultCharset() {
    // Regression test: encoding the fetched XML String back to bytes with the JVM's platform
    // default charset (rather than the UTF-8 the XML declaration itself names) would corrupt any
    // non-ASCII character on a JVM whose default isn't UTF-8.
    var xmlWithNonAsciiName =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                          id="defs" targetNamespace="http://bpmn.io/schema/bpmn">
          <bpmn:process id="proc" name="Bewerbungsprüfung — 日本語" isExecutable="true">
            <bpmn:startEvent id="start" />
          </bpmn:process>
        </bpmn:definitions>
        """;
    var client = clientReturning(xmlWithNonAsciiName);
    var cache =
        new ProcessDefinitionModelCache(
            "tenant-a", client, new ConcurrentMapCache("models"), Duration.ofMillis(1));

    var model = cache.getModel(42L, Instant.now().plusSeconds(30));

    assertThat(model.getModelElementsByType(io.camunda.zeebe.model.bpmn.instance.Process.class))
        .extracting(io.camunda.zeebe.model.bpmn.instance.Process::getName)
        .containsExactly("Bewerbungsprüfung — 日本語");
  }

  @Test
  void fetchesOnlyOncePerProcessDefinitionKeyAcrossRepeatedCalls() {
    var client = clientReturning(SIMPLE_PROCESS_XML);
    var cache =
        new ProcessDefinitionModelCache(
            "tenant-a", client, new ConcurrentMapCache("models"), Duration.ofMillis(1));
    var deadline = Instant.now().plusSeconds(30);

    cache.getModel(42L, deadline);
    cache.getModel(42L, deadline);

    verify(client, times(1)).newProcessDefinitionGetXmlRequest(42L);
  }

  @Test
  void throwsWhenTheDeadlineHasAlreadyElapsed() {
    var client = clientReturning(SIMPLE_PROCESS_XML);
    var cache =
        new ProcessDefinitionModelCache(
            "tenant-a", client, new ConcurrentMapCache("models"), Duration.ofMillis(1));

    // A real Cache (unlike the mocks used elsewhere) wraps a loader's exception in its own
    // ValueRetrievalException, so the deadline check's IllegalStateException may arrive directly
    // or as that wrapper's cause depending on the Cache implementation.
    assertThatThrownBy(() -> cache.getModel(42L, Instant.now().minusSeconds(1)))
        .satisfiesAnyOf(
            e -> assertThat(e).isInstanceOf(IllegalStateException.class),
            e -> assertThat(e).hasCauseInstanceOf(IllegalStateException.class));
  }
}
