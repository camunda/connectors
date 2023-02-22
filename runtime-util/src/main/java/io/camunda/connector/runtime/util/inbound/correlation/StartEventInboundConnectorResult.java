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
package io.camunda.connector.runtime.util.inbound.correlation;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;

public class StartEventInboundConnectorResult extends InboundConnectorResult {
  protected ProcessInstanceEvent responseData;

  @Override
  public ProcessInstanceEvent getResponseData() {
    return responseData;
  }

  public StartEventInboundConnectorResult(ProcessInstanceEvent processInstanceEvent) {
    super(
        StartEventCorrelationPoint.TYPE_NAME,
        String.valueOf(processInstanceEvent.getProcessDefinitionKey()),
        processInstanceEvent);
    this.responseData = processInstanceEvent;
  }
}
