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
package io.camunda.connector.generator.dsl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BpmnType {
  TASK("bpmn:Task", false, "Task"),
  SERVICE_TASK("bpmn:ServiceTask", false, "ServiceTask"),
  RECEIVE_TASK("bpmn:ReceiveTask", true, "ReceiveTask"),
  SCRIPT_TASK("bpmn:ScriptTask", false, "ScriptTask"),
  START_EVENT("bpmn:StartEvent", false, "StartEvent"),
  INTERMEDIATE_CATCH_EVENT("bpmn:IntermediateCatchEvent", true, "IntermediateCatchEvent"),
  INTERMEDIATE_THROW_EVENT("bpmn:IntermediateThrowEvent", true, "IntermediateThrowEvent"),
  MESSAGE_START_EVENT("bpmn:StartEvent", true, "MessageStartEvent"),
  END_EVENT("bpmn:EndEvent", false, "EndEvent"),
  MESSAGE_END_EVENT("bpmn:EndEvent", true, "MessageEndEvent"),
  BOUNDARY_EVENT("bpmn:BoundaryEvent", true, "BoundaryEvent");

  private final String name;
  private final boolean isMessage;
  private final String id;

  BpmnType(String name, boolean isMessage, String id) {
    this.name = name;
    this.isMessage = isMessage;
    this.id = id;
  }

  @JsonValue
  public String getName() {
    return name;
  }

  /** Whether the BPMN type is a message event */
  @JsonIgnore
  public boolean isMessage() {
    return isMessage;
  }

  /**
   * Returns the unique ID of the BPMN type. Not to be confused with {@link #getName()}, which is
   * the conventional name of the BPMN type and can be non-unique, e.g. in the case of Start Events
   * vs Message Start Events.
   */
  @JsonIgnore
  public String getId() {
    return id;
  }
}
