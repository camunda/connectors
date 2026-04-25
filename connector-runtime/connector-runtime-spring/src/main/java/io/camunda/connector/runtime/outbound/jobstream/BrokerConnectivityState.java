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
package io.camunda.connector.runtime.outbound.jobstream;

/**
 * Describes the connectivity state of an outbound connector worker to the Zeebe brokers.
 *
 * <ul>
 *   <li>{@code NONE} – No client stream is registered as a consumer in any broker's remote stream.
 *   <li>{@code PARTIALLY_CONNECTED} – Client streams exist on the gateway, but they do not appear
 *       as consumers in <b>every</b> broker's remote stream. This may indicate a transient issue;
 *       restart the gateway if it persists.
 *   <li>{@code ALL_CONNECTED} – All client streams for this job type are registered as consumers on
 *       all broker remote streams.
 * </ul>
 */
public enum BrokerConnectivityState {
  NONE,
  PARTIALLY_CONNECTED,
  ALL_CONNECTED
}
