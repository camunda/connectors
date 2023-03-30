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
package io.camunda.connector.impl.inbound.result;

import java.util.Objects;

public class ProcessInstance {

  private final long processInstanceKey;
  private final String bpmnProcessId;
  private final long processDefinitionKey;
  private final int version;

  public ProcessInstance(
      long processInstanceKey, String bpmnProcessId, long processDefinitionKey, int version) {
    this.processInstanceKey = processInstanceKey;
    this.bpmnProcessId = bpmnProcessId;
    this.processDefinitionKey = processDefinitionKey;
    this.version = version;
  }

  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public int getVersion() {
    return version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProcessInstance that = (ProcessInstance) o;
    return processInstanceKey == that.processInstanceKey
        && processDefinitionKey == that.processDefinitionKey
        && version == that.version
        && Objects.equals(bpmnProcessId, that.bpmnProcessId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processInstanceKey, bpmnProcessId, processDefinitionKey, version);
  }

  @Override
  public String toString() {
    return "ProcessInstance{"
        + "processInstanceKey="
        + processInstanceKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", processDefinitionKey="
        + processDefinitionKey
        + ", version="
        + version
        + '}';
  }
}
