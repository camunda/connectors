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

import io.camunda.connector.runtime.util.util.response.PublishMessageResponseDummy;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep2;
import io.camunda.zeebe.client.api.command.PublishMessageCommandStep1.PublishMessageCommandStep3;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.camunda.zeebe.client.impl.ZeebeClientFutureImpl;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;

public class PublishMessageCommandDummy
    implements PublishMessageCommandStep1, PublishMessageCommandStep2, PublishMessageCommandStep3 {

  @Override
  public PublishMessageCommandStep2 messageName(String messageName) {
    return this;
  }

  @Override
  public PublishMessageCommandStep3 correlationKey(String correlationKey) {
    return this;
  }

  @Override
  public PublishMessageCommandStep3 messageId(String messageId) {
    return this;
  }

  @Override
  public PublishMessageCommandStep3 timeToLive(Duration timeToLive) {
    return this;
  }

  @Override
  public PublishMessageCommandStep3 variables(InputStream variables) {
    return this;
  }

  @Override
  public PublishMessageCommandStep3 variables(String variables) {
    return this;
  }

  @Override
  public PublishMessageCommandStep3 variables(Map<String, Object> variables) {
    return this;
  }

  @Override
  public PublishMessageCommandStep3 variables(Object variables) {
    return this;
  }

  @Override
  public FinalCommandStep<PublishMessageResponse> requestTimeout(Duration requestTimeout) {
    return this;
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  @Override
  public ZeebeFuture<PublishMessageResponse> send() {
    ZeebeClientFutureImpl future = new ZeebeClientFutureImpl<>();
    future.complete(new PublishMessageResponseDummy());
    return future;
  }
}
