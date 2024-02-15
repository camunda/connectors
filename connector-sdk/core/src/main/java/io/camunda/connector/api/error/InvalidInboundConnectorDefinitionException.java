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
package io.camunda.connector.api.error;

import java.io.Serial;

/**
 * Unchecked exception indicating an invalid definition for an inbound connector. This exception is
 * thrown when a connector definition does not meet the required criteria.
 */
public class InvalidInboundConnectorDefinitionException extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  /**
   * Constructs a new exception with the specified detail message. The message should provide a
   * clear and concise explanation as to why the connector definition is invalid, aiding in
   * diagnostics and troubleshooting.
   *
   * @param message - the detail message pertaining to the invalid connector definition
   */
  public InvalidInboundConnectorDefinitionException(String message) {
    super(message);
  }
}
