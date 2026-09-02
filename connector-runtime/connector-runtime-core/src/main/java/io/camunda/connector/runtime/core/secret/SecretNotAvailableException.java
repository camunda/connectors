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
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;

/**
 * A legacy secret reference the filter allowed, for which no configured provider held a value. The
 * reference cannot be substituted, so the input cannot be bound; the model or the secret store has
 * to change, which is why this is a {@link ConnectorInputException} and the job is not retried.
 *
 * <p>A type of its own, rather than a bare {@code ConnectorInputException}, because error masking
 * has to tell this failure apart from every other one: it is the single case where a name the input
 * declares is expected to come back empty on the masking re-read. Everywhere else that would mean a
 * secret has been removed since the connector ran, with its value possibly still in the message.
 */
public class SecretNotAvailableException extends ConnectorInputException {

  public SecretNotAvailableException(Secret secret) {
    super(
        String.format(
            "Secret with name '%s' is not available on path '%s'",
            secret.secretName(), String.join(".", secret.fieldPath())));
  }
}
