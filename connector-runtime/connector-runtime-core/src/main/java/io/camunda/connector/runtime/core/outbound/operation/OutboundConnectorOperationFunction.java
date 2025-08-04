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
package io.camunda.connector.runtime.core.outbound.operation;

import static io.camunda.connector.runtime.core.Keywords.OPERATION_ID_KEYWORD;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorOperationFunction implements OutboundConnectorFunction {

  private static final Logger log =
      LoggerFactory.getLogger(OutboundConnectorOperationFunction.class);

  private final ConnectorOperations connectorOperations;

  public OutboundConnectorOperationFunction(ConnectorOperations connectorOperations) {
    this.connectorOperations = connectorOperations;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    String operationId = context.getJobContext().getCustomHeaders().get(OPERATION_ID_KEYWORD);
    if (operationId == null) {
      throw new ConnectorInputException(
          "Operation ID is missing in the job context custom headers.");
    }
    OperationInvoker operationInvoker = connectorOperations.operations().get(operationId);
    if (operationInvoker == null) {
      throw new ConnectorInputException("Operation not found: " + operationId);
    }
    try {
      return operationInvoker.invoke(connectorOperations.connector(), context);
    } catch (Exception e) {
      log.debug("Failed to invoke operation: {}", operationId, e);
      throw e;
    }
  }
}
