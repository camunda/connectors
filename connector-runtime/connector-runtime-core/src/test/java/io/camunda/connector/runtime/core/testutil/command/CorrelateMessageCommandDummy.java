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
import io.camunda.client.api.command.CorrelateMessageCommandStep1;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep2;
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.impl.CamundaClientFutureImpl;
import io.camunda.connector.runtime.core.testutil.response.CorrelateMessageResponseDummy;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public class CorrelateMessageCommandDummy
    implements CorrelateMessageCommandStep1,
        CorrelateMessageCommandStep2,
        CorrelateMessageCommandStep3 {

  @Override
  public CorrelateMessageCommandStep2 messageName(String messageName) {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 correlationKey(String correlationKey) {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 withoutCorrelationKey() {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 variables(InputStream variables) {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 variables(String variables) {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 variables(Map<String, Object> variables) {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 variables(Object variables) {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 variable(String key, Object value) {
    return this;
  }

  @Override
  public CorrelateMessageCommandStep3 tenantId(String tenantId) {
    return this;
  }

  @Override
  public FinalCommandStep<CorrelateMessageResponse> requestTimeout(Duration requestTimeout) {
    return this;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public CamundaFuture<CorrelateMessageResponse> send() {
    CamundaClientFutureImpl future = new CamundaClientFutureImpl<>();
    future.complete(new CorrelateMessageResponseDummy());
    return future;
  }
}
