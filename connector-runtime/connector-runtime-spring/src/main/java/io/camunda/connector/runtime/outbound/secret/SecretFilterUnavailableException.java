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
 * Thrown when the secret filter cannot do its job for a reason of this runtime's own making (no
 * {@code CamundaOperateClient} bean available), never because of anything a client or parser echoed
 * back. Its message is self-authored operator guidance, not derived from external content, so
 * unlike every other failure path here it is safe to surface as-is.
 */
public class SecretFilterUnavailableException extends RuntimeException {
  public SecretFilterUnavailableException(String message) {
    super(message);
  }
}
