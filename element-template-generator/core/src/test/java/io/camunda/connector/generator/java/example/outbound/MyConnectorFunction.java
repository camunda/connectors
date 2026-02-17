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

import static io.camunda.connector.generator.java.example.outbound.MyConnectorFunction.AllPropertiesInPredefinedGroups.*;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.generator.java.annotation.TemplateProperty;

public abstract class MyConnectorFunction implements OutboundConnectorFunction {

  public static final String ID = "my-connector-template";
  public static final String NAME = "My Connector";
  public static final int VERSION = 1;
  public static final String DESCRIPTION = "My Connector Description";
  public static final String DOCUMENTATION_REF =
      "https://docs.camunda.org/manual/latest/reference/connect/";

  @Override
  public Object execute(OutboundConnectorContext context) {
    return null;
  }

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      elementTypes = {
        @ConnectorElementType(appliesTo = BpmnType.SERVICE_TASK, elementType = BpmnType.SCRIPT_TASK)
      },
      name = MyConnectorFunction.NAME,
      version = MyConnectorFunction.VERSION,
      description = MyConnectorFunction.DESCRIPTION,
      icon = "my-connector-icon.svg",
      documentationRef = MyConnectorFunction.DOCUMENTATION_REF,
      inputDataClass = MyConnectorInput.class,
      outputDataClass = MyConnectorOutput.class,
      propertyGroups = {
        @PropertyGroup(id = "group2", label = "Group Two"),
        @PropertyGroup(
            id = "group1",
            label = "Group One",
            openByDefault = false,
            tooltip = "Group One Tooltip")
      })
  public static class FullyAnnotated extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class)
  public static class MinimallyAnnotated extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = PredefinedGroupInput.class,
      propertyGroups = {
        @PropertyGroup(id = "predefinedGroup1", label = "Predefined Group One"),
        @PropertyGroup(id = "predefinedGroup2", label = "Predefined Group Two"),
        @PropertyGroup(id = "predefinedGroup3", label = "Predefined Group Three")
      })
  public static class AllPropertiesInPredefinedGroups extends MyConnectorFunction {

    record PredefinedGroupInput(
        @TemplateProperty(group = "predefinedGroup1") String property1,
        @TemplateProperty(group = "predefinedGroup2") String property2,
        @TemplateProperty(group = "predefinedGroup3") String property3) {}
  }

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class,
      defaultResultVariable = "myResultVariable")
  public static class MinimallyAnnotatedWithResultVariable extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class,
      defaultResultExpression = "={ myResponse: response }")
  public static class MinimallyAnnotatedWithResultExpression extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class,
      extensionProperties = {
        @ElementTemplate.ExtensionProperty(name = "myExtensionProperty1", value = "value1"),
        @ElementTemplate.ExtensionProperty(
            name = "myExtensionProperty2",
            value = "value2",
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "booleanProperty",
                    equalsBoolean = TemplateProperty.EqualsBoolean.FALSE)),
      })
  public static class MinimallyAnnotatedWithExtensionProperties extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class,
      icon = "my-connector-icon.svg")
  public static class MinimallyAnnotatedWithSvgIcon extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class,
      icon = "my-connector-icon.png")
  public static class MinimallyAnnotatedWithPngIcon extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = DuplicatePropertyConnectorInput.class,
      icon = "my-connector-icon.png")
  public static class WithDuplicatePropertyIds extends MyConnectorFunction {}

  @OutboundConnector(name = "my-connector", type = "my-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class,
      elementTypes = {
        @ConnectorElementType(appliesTo = BpmnType.TASK, elementType = BpmnType.SERVICE_TASK),
        @ConnectorElementType(appliesTo = BpmnType.TASK, elementType = BpmnType.SCRIPT_TASK),
        @ConnectorElementType(appliesTo = BpmnType.TASK, elementType = BpmnType.SEND_TASK),
        @ConnectorElementType(
            appliesTo = BpmnType.END_EVENT,
            elementType = BpmnType.MESSAGE_END_EVENT),
        @ConnectorElementType(
            appliesTo = BpmnType.INTERMEDIATE_THROW_EVENT,
            elementType = BpmnType.INTERMEDIATE_THROW_EVENT,
            templateNameOverride = "My custom name for intermediate event",
            templateIdOverride = "my-custom-id-for-intermediate-event")
      },
      icon = "my-connector-icon.png")
  public static class WithMultipleElementTypes extends MyConnectorFunction {}
}
