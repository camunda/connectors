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

import io.camunda.connector.api.annotation.Header;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

@OutboundConnector(name = "Connector with primitive types", type = "primitive-types-connector")
@ElementTemplate(id = "primitive-types-connector-id", name = "Connector with primitive types")
public class OperationAnnotatedConnectorWithPrimitiveTypes implements OutboundConnectorProvider {

  @Operation(name = "Add two integers", id = "add")
  public int addWithVariable(@Variable("a") int a, @Variable("b") int b) {
    return a + b;
  }

  @Operation(
      name = "Add two integers with variable and template property",
      id = "addWithVariableAndTemplateProperty")
  public int addWithVariableAndTemplateProperty(
      @Variable @TemplateProperty(id = "a") int a, @Variable @TemplateProperty(id = "b") int b) {
    return a + b;
  }

  @Operation(name = "Add two integers with headers", id = "addWithHeader")
  public int addWithHeader(@Header("a") int a, @Header("b") int b) {
    return a + b;
  }

  @Operation(
      name = "Add two integers with headers and template properties",
      id = "addWithHeaderAndTemplateProperty")
  public int addWithHeaderAndTemplateProperty(
      @Header @TemplateProperty(id = "a") int a, @Header @TemplateProperty(id = "b") int b) {
    return a + b;
  }
}
