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

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.annotation.Header;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;

@OutboundConnector(name = OperationAnnotatedConnector.NAME, type = OperationAnnotatedConnector.TYPE)
@ElementTemplate(
    engineVersion = "^8.8",
    id = OperationAnnotatedConnector.ID,
    name = OperationAnnotatedConnector.NAME,
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "customGroup", label = "Custom Group")})
public class OperationAnnotatedConnector implements OutboundConnectorProvider {

  public static final String ID = "operation-annotated-connector-id";
  public static final String TYPE = "operation-annotated-connector-type";
  public static final String NAME = "Operation Annotated Connector";

  record Operation1Request(
      @TemplateProperty(
              id = "p1",
              group = "customGroup",
              label = "p1 Label",
              description = "p1 Description",
              tooltip = "p1 tooltip")
          String param1,
      @TemplateProperty(condition = @PropertyCondition(property = "p1", equals = "myValue"))
          String param2) {}

  @Operation(name = "Operation 1", id = "operation-1")
  public String operation1(@Variable Operation1Request request) {
    return "Operation 1 executed: " + request;
  }

  @Operation(name = "Operation 2", id = "operation-2")
  public String operation2(OutboundConnectorContext context) {
    return "Operation 2 executed";
  }

  record Operation3Request(@TemplateProperty(id = "p1") String param1, @FEEL String param2) {}

  @Operation(name = "Operation 3", id = "operation-3")
  public String operation3(
      @Variable("mydata") Operation3Request request,
      @Header("test-header")
          @TemplateProperty(
              id = "myHeader",
              label = "My Header",
              defaultValue = "my-default-value",
              feel = FeelMode.optional)
          String myHeader) {
    return "Operation 3 executed: " + request;
  }
}
