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

@OutboundConnector(
    name = OperationAnnotatedConnectorWithLinkedResource.NAME,
    type = OperationAnnotatedConnectorWithLinkedResource.TYPE)
@ElementTemplate(
    id = OperationAnnotatedConnectorWithLinkedResource.ID,
    name = OperationAnnotatedConnectorWithLinkedResource.NAME,
    engineVersion = "^8.8",
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "form", label = "Form")})
public class OperationAnnotatedConnectorWithLinkedResource implements OutboundConnectorProvider {

  public static final String ID = "op-annotated-with-linked-resource-id";
  public static final String TYPE = "op-annotated-with-linked-resource-type";
  public static final String NAME = "Operation Annotated Connector With Linked Resource";

  @TemplateLinkedResource(
      linkName = "formDefinition",
      resourceType = "form",
      group = "form",
      resourceIdLabel = "Form ID",
      resourceIdDescription = "Select a form to render as an adaptive card",
      bindingTypeLabel = "Form binding")
  record RequestWithLinkedResource(
      @TemplateProperty(group = "form", label = "Message") String message) {}

  @TemplateLinkedResource(
      linkName = "attachmentA",
      resourceType = "form",
      group = "form",
      resourceIdLabel = "Attachment A ID",
      bindingTypeLabel = "Attachment A binding")
  @TemplateLinkedResource(
      linkName = "attachmentB",
      resourceType = "file",
      group = "form",
      resourceIdLabel = "Attachment B ID",
      bindingTypeLabel = "Attachment B binding")
  record RequestWithMultipleLinkedResources(
      @TemplateProperty(group = "form", label = "Subject") String subject) {}

  @TemplateLinkedResource(linkName = "resource", resourceType = "form", group = "form")
  record RequestWithDefaultLabels(
      @TemplateProperty(group = "form", label = "Field") String field) {}

  @Operation(id = "op1", name = "Operation 1")
  public String op1(@Variable RequestWithLinkedResource request) {
    return null;
  }

  @Operation(id = "op2", name = "Operation 2")
  public String op2(@Variable RequestWithMultipleLinkedResources request) {
    return null;
  }

  @Operation(id = "op3", name = "Operation 3")
  public String op3(@Variable RequestWithDefaultLabels request) {
    return null;
  }
}
