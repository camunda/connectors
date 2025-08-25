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
package io.camunda.connector.runtime.core.testutil.command;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.impl.CamundaClientFutureImpl;
import io.camunda.connector.runtime.core.testutil.response.ProcessInstanceEventDummy;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

public class CreateCommandDummy
    implements CreateProcessInstanceCommandStep1,
        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep2,
        CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3 {

  public CreateProcessInstanceCommandStep2 bpmnProcessId(String bpmnProcessId) {
    return this;
  }

  public CreateProcessInstanceCommandStep3 processDefinitionKey(long processDefinitionKey) {
    return this;
  }

  public CreateProcessInstanceCommandStep3 version(int version) {
    return this;
  }

  public CreateProcessInstanceCommandStep3 latestVersion() {
    return this;
  }

  public CreateProcessInstanceCommandStep3 variables(InputStream variables) {
    return this;
  }

  public CreateProcessInstanceCommandStep3 variables(String variables) {
    return this;
  }

  public CreateProcessInstanceCommandStep3 variables(Map<String, Object> variables) {
    return this;
  }

  public CreateProcessInstanceCommandStep3 variables(Object variables) {
    return this;
  }

  public CreateProcessInstanceCommandStep3 startBeforeElement(String elementId) {
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 terminateAfterElement(String elementId) {
    return this;
  }

  public CreateProcessInstanceWithResultCommandStep1 withResult() {
    throw new UnsupportedOperationException(
        "This method is not supported in the dummy implementation.");
  }

  @Override
  public CreateProcessInstanceCommandStep3 tags(String... tags) {
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 tags(Iterable<String> tags) {
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 tags(Set<String> tags) {
    return this;
  }

  public FinalCommandStep<ProcessInstanceEvent> requestTimeout(Duration requestTimeout) {
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 variable(String key, Object value) {
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep3 tenantId(String tenantId) {
    return this;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public CamundaFuture<ProcessInstanceEvent> send() {
    CamundaClientFutureImpl future = new CamundaClientFutureImpl<>();
    future.complete(new ProcessInstanceEventDummy());
    return future;
  }

  @Override
  public CreateProcessInstanceCommandStep1 useRest() {
    return this;
  }

  @Override
  public CreateProcessInstanceCommandStep1 useGrpc() {
    return this;
  }
}
