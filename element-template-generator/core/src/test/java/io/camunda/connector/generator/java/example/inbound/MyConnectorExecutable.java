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
package io.camunda.connector.generator.java.example.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ExtensionProperty;

public class MyConnectorExecutable implements InboundConnectorExecutable<InboundConnectorContext> {

  public static final String ID = "my-inbound-connector-template";
  public static final String NAME = "My Inbound Connector Template";

  @Override
  public void activate(InboundConnectorContext context) throws Exception {}

  @Override
  public void deactivate() {}

  @InboundConnector(name = "my-inbound-connector", type = "my-inbound-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorExecutable.ID,
      name = MyConnectorExecutable.NAME,
      inputDataClass = MyConnectorProperties.class,
      icon = "my-connector-icon.png")
  public static class MinimallyAnnotated extends MyConnectorExecutable {}

  @InboundConnector(name = "my-inbound-connector", type = "my-inbound-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorExecutable.ID,
      name = MyConnectorExecutable.NAME,
      inputDataClass = MyConnectorProperties.class,
      extensionProperties = {
        @ExtensionProperty(name = "myExtensionProperty1", value = "value1"),
        @ExtensionProperty(name = "myExtensionProperty2", value = "value2"),
      },
      icon = "my-connector-icon.png")
  public static class MinimallyAnnotatedWithExtensionProperties extends MyConnectorExecutable {}

  @InboundConnector(name = "my-inbound-connector", type = "my-inbound-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorExecutable.ID,
      name = MyConnectorExecutable.NAME,
      inputDataClass = MyConnectorProperties.class,
      icon = "my-connector-icon.png",
      defaultResultVariable = "myResultVariable")
  public static class MinimallyAnnotatedWithResultVariable extends MyConnectorExecutable {}

  @InboundConnector(name = "my-inbound-connector", type = "my-inbound-connector-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = MyConnectorExecutable.ID,
      name = MyConnectorExecutable.NAME,
      inputDataClass = MyConnectorProperties.class,
      icon = "my-connector-icon.png",
      defaultResultExpression = "={ myResponse: request }")
  public static class MinimallyAnnotatedWithResultExpression extends MyConnectorExecutable {}
}
