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
package io.camunda.connector.api.inbound;

/**
 * Strategy to handle correlation failures. The Connector runtime provides this strategy to the
 * connector implementation as a hint on how to handle the failure. It's strongly recommended to
 * follow the strategy provided by the runtime.
 *
 * <p>The Connector runtime may provide this information based on:
 *
 * <ul>
 *   <li>the type of the failure
 *   <li>the configuration of the connector provided by the user
 *   <li>the configuration of the Connector runtime
 * </ul>
 */
public sealed interface CorrelationFailureHandlingStrategy {

  /**
   * When the connector implementation receives this strategy from the runtime, it should forward
   * the error to the upstream system. The exact way of how to do this is up to the connector and
   * the upstream system, however, it is expected that the upstream system receives the error and
   * can handle it.
   *
   * <p>For example, the connector that consumes messages from a message queue may redeliver the
   * message to the queue, so the upstream system can retry processing the message. Depending on the
   * value of {@code isRetryable}, the upstream system may retry the operation at a later point in
   * time. A message queue connector may set the corresponding flag in the message metadata to
   * indicate whether the message should be redelivered or not.
   *
   * @param isRetryable whether the error is retryable or not. If the error is retryable, the
   *     upstream system may retry the operation at a later point in time (e.g. by redelivering the
   *     message).
   */
  record ForwardErrorToUpstream(boolean isRetryable) implements CorrelationFailureHandlingStrategy {

    public static final ForwardErrorToUpstream RETRYABLE = new ForwardErrorToUpstream(true);
    public static final ForwardErrorToUpstream NON_RETRYABLE = new ForwardErrorToUpstream(false);
  }

  /**
   * When the connector implementation receives this strategy from the runtime, it should ignore the
   * error and continue processing. This strategy is useful when the error is an expected outcome,
   * and the event can be safely discarded. The exact way of how to do this is up to the connector
   * and the upstream system, however, it is expected that the upstream system doesn't receive the
   * error.
   */
  final class Ignore implements CorrelationFailureHandlingStrategy {

    public static final Ignore INSTANCE = new Ignore();
  }
}
