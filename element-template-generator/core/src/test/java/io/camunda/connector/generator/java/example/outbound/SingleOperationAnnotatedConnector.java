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
package io.camunda.connector.generator.java.example.outbound;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;

@OutboundConnector(
    name = SingleOperationAnnotatedConnector.NAME,
    type = SingleOperationAnnotatedConnector.TYPE)
@ElementTemplate(
    engineVersion = "^8.8",
    id = SingleOperationAnnotatedConnector.ID,
    name = SingleOperationAnnotatedConnector.NAME)
public class SingleOperationAnnotatedConnector implements OutboundConnectorProvider {

  public static final String ID = "operation-annotated-connector-id";
  public static final String TYPE = "operation-annotated-connector-type";
  public static final String NAME = "Operation Annotated Connector";

  record OperationRequest(
      @TemplateProperty(id = "p1") String param1,
      @TemplateProperty(condition = @PropertyCondition(property = "p1", equals = "myValue"))
          String param2) {}

  @Operation(name = "Operation 1", id = "operation-1")
  public String singleOperation(@Variable OperationRequest request) {
    return "Operation executed: " + request;
  }
}
