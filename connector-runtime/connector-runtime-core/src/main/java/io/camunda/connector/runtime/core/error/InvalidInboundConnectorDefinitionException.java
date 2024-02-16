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
 * Represents an unchecked exception indicating an invalid definition for an inbound connector.
 * Enhanced to include process definition context for precise diagnostics.
 */
public class InvalidInboundConnectorDefinitionException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String processDefinitionName;
  private final Long processDefinitionKey;
  private final Long processDefinitionVersion;

  /**
   * Constructor for exception without process definition context.
   *
   * @param message Detailed message about the invalid connector definition.
   */
  public InvalidInboundConnectorDefinitionException(String message) {
    this(message, null, null, null, null);
  }

  /**
   * Constructor for exception with cause but without process definition context.
   *
   * @param message Detailed message about the invalid connector definition.
   * @param cause The cause of this exception.
   */
  public InvalidInboundConnectorDefinitionException(String message, Throwable cause) {
    this(message, null, null, null, cause);
  }

  /**
   * Constructor for exception with detailed process definition context.
   *
   * @param message Detailed message about the invalid connector definition.
   * @param processDefinitionName Name of the process definition, nullable.
   * @param processDefinitionKey Key of the process definition, nullable.
   * @param processDefinitionVersion Version of the process definition, nullable.
   */
  public InvalidInboundConnectorDefinitionException(
      String message,
      String processDefinitionName,
      Long processDefinitionKey,
      Long processDefinitionVersion) {
    this(message, processDefinitionName, processDefinitionKey, processDefinitionVersion, null);
  }

  /**
   * Constructor to handle all initializations.
   *
   * @param message Detailed message about the invalid connector definition.
   * @param processDefinitionName Name of the process definition, nullable.
   * @param processDefinitionKey Key of the process definition, nullable.
   * @param processDefinitionVersion Version of the process definition, nullable.
   * @param cause The cause of this exception, nullable.
   */
  public InvalidInboundConnectorDefinitionException(
      String message,
      String processDefinitionName,
      Long processDefinitionKey,
      Long processDefinitionVersion,
      Throwable cause) {
    super(message, cause);
    this.processDefinitionName = processDefinitionName;
    this.processDefinitionKey = processDefinitionKey;
    this.processDefinitionVersion = processDefinitionVersion;
  }

  /**
   * Builds a detailed exception message.
   *
   * @param message Initial message about the invalid definition.
   * @param processDefinitionName Name of the process definition, may be null.
   * @param processDefinitionKey Key of the process definition, may be null.
   * @param processDefinitionVersion Version of the process definition, may be null.
   * @return Constructed detailed message.
   */
  private static String buildMessage(
      String message,
      String processDefinitionName,
      Long processDefinitionKey,
      Long processDefinitionVersion) {
    StringBuilder sb = new StringBuilder(message);
    if (processDefinitionName != null)
      sb.append(" [Process Definition Name: ").append(processDefinitionName).append("]");
    if (processDefinitionKey != null) sb.append(", Key: ").append(processDefinitionKey);
    if (processDefinitionVersion != null) sb.append(", Version: ").append(processDefinitionVersion);
    return sb.toString();
  }

  public String getProcessDefinitionName() {
    return processDefinitionName;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public Long getProcessDefinitionVersion() {
    return processDefinitionVersion;
  }

  @Override
  public String toString() {
    return String.format(
        "InvalidInboundConnectorDefinitionException{message='%s', processDefinitionName='%s', processDefinitionKey='%s', processDefinitionVersion='%s'}",
        getMessage(), processDefinitionName, processDefinitionKey, processDefinitionVersion);
  }
}
