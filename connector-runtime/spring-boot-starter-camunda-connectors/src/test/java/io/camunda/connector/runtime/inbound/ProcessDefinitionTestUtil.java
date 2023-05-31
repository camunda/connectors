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
package io.camunda.connector.runtime.inbound;

import static org.mockito.Mockito.when;

import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import io.camunda.connector.runtime.inbound.importer.ProcessDefinitionInspector;
import io.camunda.connector.runtime.inbound.lifecycle.InboundConnectorManager;
import io.camunda.operate.dto.ProcessDefinition;
import io.camunda.operate.exception.OperateException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Utility to mock process definition deployments and Operate API responses */
public class ProcessDefinitionTestUtil {

  private final ProcessDefinitionInspector inspector;
  private final InboundConnectorManager manager;

  public ProcessDefinitionTestUtil(
      InboundConnectorManager manager, ProcessDefinitionInspector inspector) {
    this.manager = manager;
    this.inspector = inspector;
  }

  public void deployProcessDefinition(
      ProcessDefinition processDefinition, InboundConnectorProperties connector)
      throws OperateException {
    deployProcessDefinition(processDefinition, List.of(connector));
  }

  public void deployProcessDefinition(
      ProcessDefinition processDefinition, List<InboundConnectorProperties> connectors)
      throws OperateException {

    deployProcessDefinition(Map.of(processDefinition, connectors));
  }

  public void deployProcessDefinition(
      Map<ProcessDefinition, List<InboundConnectorProperties>> connectorsByProcDef)
      throws OperateException {

    for (var entry : connectorsByProcDef.entrySet()) {
      when(inspector.findInboundConnectors(entry.getKey())).thenReturn(entry.getValue());
    }
    manager.registerProcessDefinitions(new ArrayList<>(connectorsByProcDef.keySet()));
  }

  static long processDefinitionKey = 0L;

  public static ProcessDefinition processDefinition(String bpmnId, int version) {
    ProcessDefinition pd = new ProcessDefinition();
    pd.setBpmnProcessId(bpmnId);
    pd.setVersion((long) version);
    pd.setKey(++processDefinitionKey);
    return pd;
  }
}
