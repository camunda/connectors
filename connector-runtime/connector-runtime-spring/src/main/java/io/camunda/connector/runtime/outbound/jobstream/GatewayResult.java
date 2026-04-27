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
 * Represents the outcome of querying the gateway job-stream actuator endpoint.
 *
 * <ul>
 *   <li>{@link Success} – the gateway was reachable and returned stream data.
 *   <li>{@link Failure.Unreachable} – the gateway was configured but could not be reached.
 *   <li>{@link Failure.Unknown} – no gateway monitoring URL is configured.
 * </ul>
 */
public sealed interface GatewayResult permits GatewayResult.Success, GatewayResult.Failure {

  record Success(JobStreamsResponse streams) implements GatewayResult {}

  sealed interface Failure extends GatewayResult
      permits GatewayResult.Failure.Unreachable, GatewayResult.Failure.Unknown {

    GatewayConnectivityState gatewayState();

    /** Gateway was configured but could not be reached (network error, timeout, etc.). */
    record Unreachable() implements Failure {
      @Override
      public GatewayConnectivityState gatewayState() {
        return GatewayConnectivityState.UNREACHABLE;
      }
    }

    /** No gateway monitoring URL is configured — connectivity is simply not known. */
    record Unknown() implements Failure {
      @Override
      public GatewayConnectivityState gatewayState() {
        return GatewayConnectivityState.UNKNOWN;
      }
    }
  }
}
