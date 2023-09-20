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
package io.camunda.connector.test.outbound;

import io.camunda.connector.api.outbound.JobContext;
import java.util.Map;
import java.util.function.Supplier;

public class TestJobContext implements JobContext {

  private final Supplier<Map<String, String>> headers;

  private final Supplier<String> variables;

  private String type;
  private long processInstanceKey;
  private String bpmnProcessId;
  private int processDefinitionVersion;
  private long processDefinitionKey;
  private String elementId;
  private long elementInstanceKey;

  public TestJobContext(Supplier<Map<String, String>> headers, Supplier<String> variables) {
    this.headers = headers;
    this.variables = variables;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return headers.get();
  }

  @Override
  public String getVariables() {
    return variables.get();
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey;
  }

  @Override
  public String getBpmnProcessId() {
    return bpmnProcessId;
  }

  @Override
  public int getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  @Override
  public String getElementId() {
    return elementId;
  }

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }
}
