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

package io.camunda.connector.runtime.core.error;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Container class for BPMN error data. This is used to indicate when a BPMN error should be thrown.
 */
public class BpmnError {

  private String code;
  private String message;

  private Map<String, Object> variables = Collections.emptyMap();

  public BpmnError() {}

  public BpmnError(String errorCode, String errorMessage) {
    this.code = errorCode;
    this.message = errorMessage;
  }

  public BpmnError(String errorCode, String errorMessage, Map<String, Object> errorVariables) {
    this.code = errorCode;
    this.message = errorMessage;
    this.variables = errorVariables;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Map<String, Object> getVariables() {
    return variables;
  }

  public void setVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  public boolean hasCode() {
    return code != null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    BpmnError bpmnError = (BpmnError) o;
    return Objects.equals(code, bpmnError.code)
        && Objects.equals(message, bpmnError.message)
        && Objects.equals(variables, bpmnError.variables);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, variables);
  }

  @Override
  public String toString() {
    return "BpmnError{"
        + "code='"
        + code
        + '\''
        + ", message='"
        + message
        + '\''
        + ", variables="
        + variables
        + '}';
  }
}
