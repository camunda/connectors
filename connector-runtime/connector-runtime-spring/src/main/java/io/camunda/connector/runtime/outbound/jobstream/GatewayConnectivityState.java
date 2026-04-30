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
 * Describes the connectivity state of an outbound connector worker as observed via the Zeebe
 * gateway job-streams actuator endpoint.
 *
 * <ul>
 *   <li>{@code UNKNOWN} – No {@code GatewayJobStreamClient} bean is configured; stream state cannot
 *       be determined.
 *   <li>{@code UNREACHABLE} – A client is configured but the gateway monitoring endpoint threw an
 *       exception or returned a non-200 response.
 *   <li>{@code NONE} – The gateway is reachable but no client stream exists for this job type. The
 *       worker is not visible to the gateway and should be restarted.
 *   <li>{@code CONNECTED} – A client stream for this job type exists on the gateway. See {@link
 *       BrokerConnectivityState} for the downstream broker connection detail.
 * </ul>
 */
public enum GatewayConnectivityState {
  UNKNOWN,
  UNREACHABLE,
  NOT_CONNECTED,
  CONNECTED
}
