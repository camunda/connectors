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
  TASK("bpmn:Task", false),
  SERVICE_TASK("bpmn:ServiceTask", false),
  RECEIVE_TASK("bpmn:ReceiveTask", true),
  SCRIPT_TASK("bpmn:ScriptTask", false),
  START_EVENT("bpmn:StartEvent", false),
  INTERMEDIATE_CATCH_EVENT("bpmn:IntermediateCatchEvent", true),
  INTERMEDIATE_THROW_EVENT("bpmn:IntermediateThrowEvent", true),
  MESSAGE_START_EVENT("bpmn:MessageStartEvent", true),
  END_EVENT("bpmn:EndEvent", false),
  MESSAGE_END_EVENT("bpmn:MessageEndEvent", true),
  BOUNDARY_EVENT("bpmn:BoundaryEvent", true);

  private final String name;
  private final boolean isMessage;

  BpmnType(String name, boolean isMessage) {
    this.name = name;
    this.isMessage = isMessage;
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

  /** Returns the short name of the BPMN type, i.e. without the "bpmn:" namespace prefix */
  @JsonIgnore
  public String getShortName() {
    return name.substring(name.indexOf(":") + 1);
  }

  public static BpmnType fromName(String name) {
    for (BpmnType type : values()) {
      if (type.getName().equals(name)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown BPMN type: " + name);
  }
}
