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
 * Thrown by {@link ProcessDefinitionModelCache} when it cannot look up a process definition's model
 * for a reason of this runtime's own making (no {@code CamundaOperateClient} bean available), never
 * because of anything a client or parser echoed back. Each consumer ({@link
 * ProcessDefinitionSecretKeyCache}, the intrinsic-function allow-list cache) catches this and
 * raises its own, more specific unavailability exception naming the property that would let it fail
 * open safely instead.
 */
public class ProcessDefinitionModelUnavailableException extends RuntimeException {
  public ProcessDefinitionModelUnavailableException(String message) {
    super(message);
  }
}
