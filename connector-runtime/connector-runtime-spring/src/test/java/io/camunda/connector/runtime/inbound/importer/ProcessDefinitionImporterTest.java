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
package io.camunda.connector.runtime.inbound.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.inbound.state.ProcessImportResult;
import io.camunda.connector.runtime.inbound.state.ProcessStateStore;
import io.camunda.zeebe.client.api.search.response.ProcessDefinition;
import io.camunda.zeebe.client.impl.search.response.ProcessDefinitionImpl;
import io.camunda.zeebe.client.protocol.rest.ProcessDefinitionItem;
import io.camunda.zeebe.spring.client.metrics.DefaultNoopMetricsRecorder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class ProcessDefinitionImporterTest {

  private ProcessDefinitionImporter importer;
  private ProcessStateStore stateStore;
  private ProcessDefinitionSearch search;

  @BeforeEach
  public void init() {
    stateStore = mock(ProcessStateStore.class);
    search = mock(ProcessDefinitionSearch.class);
    importer = new ProcessDefinitionImporter(stateStore, search, new DefaultNoopMetricsRecorder());
  }

  @Test
  void shouldUpdateStateStore() {
    // given
    List<ProcessDefinition> first = List.of(getProcessDefinition("process1", 1, 1, "tenant1"));
    List<ProcessDefinition> second =
        List.of(
            getProcessDefinition("process1", 1, 1, "tenant1"),
            getProcessDefinition("process2", 1, 2, "tenant1"));

    when(search.query()).thenReturn(first).thenReturn(second);

    // when
    importer.scheduleImport();
    importer.scheduleImport();

    // then
    ArgumentCaptor<ProcessImportResult> captor = ArgumentCaptor.forClass(ProcessImportResult.class);
    verify(stateStore, times(2)).update(captor.capture());
    List<ProcessImportResult> allValues = captor.getAllValues();
    assertEquals(1, allValues.get(0).processDefinitionVersions().size());
    assertEquals(2, allValues.get(1).processDefinitionVersions().size());
  }

  private ProcessDefinition getProcessDefinition(
      String bpmnProcessId, int version, long key, String tenantId) {
    var pdi = new ProcessDefinitionItem();
    pdi.setProcessDefinitionId(bpmnProcessId);
    pdi.setVersion(version);
    pdi.setProcessDefinitionKey(key);
    pdi.setTenantId(tenantId);
    return new ProcessDefinitionImpl(pdi);
  }
}
