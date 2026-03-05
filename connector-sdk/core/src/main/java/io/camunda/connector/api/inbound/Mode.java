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
 * Defines how an inbound connector interacts with the process engine when triggering a process.
 *
 * <p>This enum controls whether the connector waits for the process engine to complete the
 * operation (process instance creation or message correlation) and return a result, or publishes
 * the event and returns immediately without waiting.
 */
public enum Mode {

  /**
   * The connector waits for the process engine to complete the operation synchronously before
   * returning.
   *
   * <p>In this mode, the process engine either creates a new process instance or correlates a
   * message, and the connector receives the result (e.g., process instance data or correlation
   * outcome) before handing back control to the caller.
   *
   * <p>Use this mode when the caller requires a result from the process engine before continuing.
   */
  Sync,

  /**
   * The connector publishes the event and returns immediately, without waiting for the process
   * engine to create a process instance or correlate the message.
   *
   * <p>This is the default mode. Use this when fire-and-forget semantics are sufficient and no
   * result from the process engine is required by the caller.
   */
  Async
}
