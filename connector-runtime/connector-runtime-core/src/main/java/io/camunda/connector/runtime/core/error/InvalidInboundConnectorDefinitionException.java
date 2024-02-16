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
package io.camunda.connector.runtime.core.error;

import java.io.Serial;

/**
 * Represents an unchecked exception indicating an invalid definition for an inbound connector. This
 * exception is thrown when the connector's definition does not meet the expected format or
 * standards.
 */
public class InvalidInboundConnectorDefinitionException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified detail message. This constructor is typically
   * used when the error message itself is sufficient to describe the problem encountered.
   *
   * @param message The detailed message that explains the reason for the exception. The detail
   *     message is saved for later retrieval by the {@link #getMessage()} method.
   */
  public InvalidInboundConnectorDefinitionException(final String message) {
    super(message);
  }

  /**
   * Constructs a new exception with the specified detail message and cause. This constructor is
   * used when an underlying cause for the exception is available, providing more context for
   * troubleshooting.
   *
   * @param message The detailed message that explains the reason for the exception.
   * @param cause The cause of the exception (which is saved for later retrieval by the {@link
   *     #getCause()} method). A null value indicates that the cause is nonexistent or unknown.
   */
  public InvalidInboundConnectorDefinitionException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
