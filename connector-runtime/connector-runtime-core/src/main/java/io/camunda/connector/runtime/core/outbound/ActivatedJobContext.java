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
package io.camunda.connector.runtime.core.outbound;

import io.camunda.connector.api.outbound.JobContext;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import java.util.Map;
import java.util.function.Supplier;

public class ActivatedJobContext implements JobContext {

  private final ActivatedJob activatedJob;
  private final Supplier<String> variables;

  public ActivatedJobContext(ActivatedJob activatedJob, Supplier<String> variables) {
    this.activatedJob = activatedJob;
    this.variables = variables;
  }

  @Override
  public Map<String, String> getCustomHeaders() {
    return activatedJob.getCustomHeaders();
  }

  @Override
  public String getVariables() {
    return variables.get();
  }

  @Override
  public String getType() {
    return activatedJob.getType();
  }

  @Override
  public long getProcessInstanceKey() {
    return activatedJob.getProcessInstanceKey();
  }

  @Override
  public String getBpmnProcessId() {
    return activatedJob.getBpmnProcessId();
  }

  @Override
  public int getProcessDefinitionVersion() {
    return activatedJob.getProcessDefinitionVersion();
  }

  @Override
  public long getProcessDefinitionKey() {
    return activatedJob.getProcessDefinitionKey();
  }

  @Override
  public String getElementId() {
    return activatedJob.getElementId();
  }

  @Override
  public long getElementInstanceKey() {
    return activatedJob.getElementInstanceKey();
  }

  @Override
  public String getTenantId() {
    return activatedJob.getTenantId();
  }
}
