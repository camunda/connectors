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

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateLinkedResource;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

@OutboundConnector(name = ClassBasedConnectorWithLinkedResource.NAME, type = "class-based-lr-type")
@ElementTemplate(
    id = ClassBasedConnectorWithLinkedResource.ID,
    name = ClassBasedConnectorWithLinkedResource.NAME,
    engineVersion = "^8.8",
    inputDataClass = ClassBasedConnectorWithLinkedResource.Request.class,
    propertyGroups = {@ElementTemplate.PropertyGroup(id = "form", label = "Form")})
public class ClassBasedConnectorWithLinkedResource implements OutboundConnectorFunction {

  public static final String ID = "class-based-lr-id";
  public static final String NAME = "Class Based Connector With Linked Resource";

  @TemplateLinkedResource(
      linkName = "formDefinition",
      resourceType = "form",
      group = "form",
      resourceIdLabel = "Form ID",
      resourceIdDescription = "Select a form to render as an adaptive card",
      bindingTypeLabel = "Form binding")
  public record Request(@TemplateProperty(group = "form", label = "Message") String message) {}

  @Override
  public Object execute(OutboundConnectorContext context) {
    return null;
  }
}
