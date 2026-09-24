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
package io.camunda.connector.runtime.outbound.secret;

/**
 * Thrown when the intrinsic-function allow-list cannot do its job for a reason of this runtime's
 * own making (no {@code CamundaOperateClient} bean available), never because of anything a client
 * or parser echoed back. Unlike {@link SecretFilterUnavailableException}'s equivalent, this can
 * never be treated as "no calls declared" and fail open: an intrinsic-function call is
 * security-testing-findings#275's RCE-class primitive, so an operator who cannot reach the
 * BPMN-fetch endpoint must set {@code camunda.connector.intrinsic-function.allow-list.mode
 * =DISABLED} (refuse every call) rather than get a silent allow-all.
 */
public class IntrinsicFunctionAllowListUnavailableException extends RuntimeException {
  public IntrinsicFunctionAllowListUnavailableException(String message) {
    super(message);
  }
}
