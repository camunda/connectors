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
package io.camunda.connector.runtime.inbound.executable.lifecycle;

import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableEvent.ProcessStateChanged;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;

/**
 * Identifies a process for the purpose of serializing lifecycle work. Every mutation that touches
 * executables for the same {@code (tenantId, bpmnProcessId)} pair must run on the same lane, keyed
 * by this record.
 */
public record LaneKey(String tenantId, String bpmnProcessId) {

  public static LaneKey of(ProcessStateChanged event) {
    return new LaneKey(event.tenantId(), event.bpmnProcessId());
  }

  public static LaneKey of(InboundConnectorElement element) {
    return new LaneKey(element.tenantId(), element.element().bpmnProcessId());
  }

  public static LaneKey of(RegisteredExecutable executable) {
    var elements =
        switch (executable) {
          case RegisteredExecutable.Activated a -> a.context().connectorElements();
          case RegisteredExecutable.ConnectorNotRegistered n -> n.data().connectorElements();
          case RegisteredExecutable.FailedToActivate f -> f.data().connectorElements();
          case RegisteredExecutable.InvalidDefinition i -> i.data().connectorElements();
        };
    if (elements.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot derive ProcessKey from executable with no elements: " + executable.id());
    }
    return LaneKey.of(elements.getFirst());
  }
}
