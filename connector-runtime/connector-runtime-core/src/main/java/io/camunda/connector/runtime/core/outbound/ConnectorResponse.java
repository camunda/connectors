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

/**
 * Base interface for connector responses that need lifecycle callbacks. When returned from {@link
 * io.camunda.connector.api.outbound.OutboundConnectorFunction#execute}, the runtime will unwrap
 * {@link #responseValue()} for result expression evaluation and invoke callbacks on completion.
 */
public interface ConnectorResponse {

  /** The raw response value used for result expression evaluation. */
  Object responseValue();

  /** Called when the Zeebe complete command succeeds. */
  default void onCompletionSuccess(Object response) {}

  /** Called when the Zeebe complete command fails. */
  default void onCompletionError(Throwable throwable) {}
}
