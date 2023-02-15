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
package io.camunda.connector.impl.inbound.correlation;

import io.camunda.connector.api.inbound.ProcessCorrelationPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Properties of a StartEvent triggered by an Inbound Connector */
public class StartEventCorrelationPoint extends ProcessCorrelationPoint {

  public static final String TYPE_NAME = "START_EVENT";

  private final String bpmnProcessId;
  private final int version;
  private final long processDefinitionKey;

  private static final Logger LOG = LoggerFactory.getLogger(StartEventCorrelationPoint.class);

  public StartEventCorrelationPoint(long processDefinitionKey, String bpmnProcessId, int version) {
    this.bpmnProcessId = bpmnProcessId;
    this.version = version;
    this.processDefinitionKey = processDefinitionKey;
    LOG.debug("Created inbound correlation point: " + this);
  }

  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  public int getVersion() {
    return version;
  }

  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String toString() {
    return "StartEventCorrelationPoint{"
        + "processDefinitionKey="
        + processDefinitionKey
        + ", bpmnProcessId='"
        + bpmnProcessId
        + '\''
        + ", version="
        + version
        + '}';
  }

  @Override
  public int compareTo(ProcessCorrelationPoint o) {
    if (!this.getClass().equals(o.getClass())) {
      return -1;
    }
    StartEventCorrelationPoint other = (StartEventCorrelationPoint) o;
    if (!bpmnProcessId.equals(other.bpmnProcessId)) {
      return bpmnProcessId.compareTo(other.bpmnProcessId);
    }
    return Integer.compare(version, other.version);
  }
}
