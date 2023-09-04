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
package io.camunda.connector.generator.core.example;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.annotation.ElementTemplate;
import io.camunda.connector.generator.annotation.ElementTemplate.PropertyGroup;

public abstract class MyConnectorFunction implements OutboundConnectorFunction {

  public static final String ID = "my-connector-template";
  public static final String NAME = "My Connector Template";
  public static final int VERSION = 1;
  public static final String DESCRIPTION = "My Connector Template Description";
  public static final String DOCUMENTATION_REF =
      "https://docs.camunda.org/manual/latest/reference/connect/";

  @Override
  public Object execute(OutboundConnectorContext context) {
    return null;
  }

  @OutboundConnector(
      name = "my-connector",
      type = "my-connector-type",
      inputVariables = {})
  @ElementTemplate(
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      version = MyConnectorFunction.VERSION,
      description = MyConnectorFunction.DESCRIPTION,
      documentationRef = MyConnectorFunction.DOCUMENTATION_REF,
      inputDataClass = MyConnectorInput.class,
      propertyGroups = {
        @PropertyGroup(id = "group2", label = "Group Two"),
        @PropertyGroup(id = "group1", label = "Group One")
      })
  public static class FullyAnnotated extends MyConnectorFunction {}

  @OutboundConnector(
      name = "my-connector",
      type = "my-connector-type",
      inputVariables = {})
  @ElementTemplate(
      id = MyConnectorFunction.ID,
      name = MyConnectorFunction.NAME,
      inputDataClass = MyConnectorInput.class)
  public static class MinimallyAnnotated extends MyConnectorFunction {}
}
