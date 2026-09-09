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
package io.camunda.connector.runtime.core.secret;

/**
 * Thrown when the allow-list of secret names an element may resolve could not be read, so no secret
 * value ever reached the input. The lookup reads the process definition from secondary storage,
 * which a just-deployed definition has yet to reach, so this failure is worth another attempt after
 * a backoff rather than immediately.
 *
 * <p>It lives here rather than beside the filter factory that throws it because the runtime that
 * has to recognise it on the failure path is in this module.
 *
 * <p>Its message is written by this runtime and names only the element and the type of the lookup
 * failure, so it is publishable where an arbitrary provider's message is not.
 */
public class SecretAllowListUnavailableException extends RuntimeException
    implements SecretFailureDiagnostic {

  public SecretAllowListUnavailableException(String message) {
    super(message);
  }

  @Override
  public String publishableMessage() {
    return getMessage();
  }
}
