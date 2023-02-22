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
package io.camunda.connector.runtime.util.util.command;

import io.camunda.connector.runtime.util.util.response.ProcessInstanceEventDummy;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

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

  public CreateProcessInstanceWithResultCommandStep1 withResult() {
    return null;
  }

  public FinalCommandStep<ProcessInstanceEvent> requestTimeout(Duration requestTimeout) {
    return null;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public ZeebeFuture<ProcessInstanceEvent> send() {
    ZeebeClientFutureImpl future = new ZeebeClientFutureImpl<>();
    future.complete(new ProcessInstanceEventDummy());
    return future;
  }
}
