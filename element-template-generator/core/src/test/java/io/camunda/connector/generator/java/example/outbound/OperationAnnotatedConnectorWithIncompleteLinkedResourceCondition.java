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
import io.camunda.connector.generator.java.annotation.TemplateLinkedResource;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NestedPropertyCondition;

/** Declares a condition with an {@code equals} but no {@code property}, which is rejected. */
@OutboundConnector(
    name = OperationAnnotatedConnectorWithIncompleteLinkedResourceCondition.NAME,
    type = OperationAnnotatedConnectorWithIncompleteLinkedResourceCondition.TYPE)
@ElementTemplate(
    id = OperationAnnotatedConnectorWithIncompleteLinkedResourceCondition.ID,
    name = OperationAnnotatedConnectorWithIncompleteLinkedResourceCondition.NAME,
    engineVersion = "^8.8",
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "form", label = "Form")})
public class OperationAnnotatedConnectorWithIncompleteLinkedResourceCondition
    implements OutboundConnectorProvider {

  public static final String ID = "op-annotated-with-incomplete-linked-resource-condition-id";
  public static final String TYPE = "op-annotated-with-incomplete-linked-resource-condition-type";
  public static final String NAME =
      "Operation Annotated Connector With Incomplete Linked Resource Condition";

  @TemplateLinkedResource(
      linkName = "formDefinition",
      resourceType = "form",
      group = "form",
      conditions = @NestedPropertyCondition(property = "", equals = "form"))
  record RequestWithIncompleteCondition(
      @TemplateProperty(group = "form", label = "Message") String message) {}

  @Operation(id = "op1", name = "Operation 1")
  @SuppressWarnings("unused")
  public String op1(@Variable RequestWithIncompleteCondition request) {
    return request.toString();
  }
}
