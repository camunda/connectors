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

import io.camunda.connector.api.error.ConnectorInputException;

/**
 * A lookup the runtime refused before asking anything: legacy resolution is switched off, or the
 * name has no reference form. The model has to change either way, which is why this is a {@link
 * ConnectorInputException} — the runtime fails such a job without retrying it.
 *
 * <p>The message says what to change, and is written here rather than taken from a provider, so it
 * survives being reported where a provider's own message could not be. See {@link
 * SecretFailureDiagnostic}.
 */
public class SecretLookupRefusedException extends ConnectorInputException
    implements SecretFailureDiagnostic {

  public SecretLookupRefusedException(String message) {
    super(message);
  }

  @Override
  public String publishableMessage() {
    return getMessage();
  }
}
