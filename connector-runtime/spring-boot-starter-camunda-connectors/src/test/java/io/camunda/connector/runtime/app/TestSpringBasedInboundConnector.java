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
package io.camunda.connector.runtime.app;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.core.env.Environment;

@InboundConnector(name = "TEST_INBOUND_SPRING", type = "io.camunda:test-inbound-spring:1")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class TestSpringBasedInboundConnector implements InboundConnectorExecutable {

  private InboundConnectorContext context;

  private final Environment environment;

  public TestSpringBasedInboundConnector(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void activate(InboundConnectorContext context) {
    this.context = context;
  }

  @Override
  public void deactivate() {}

  public InboundConnectorContext getProvidedContext() {
    if (context == null) {
      throw new IllegalStateException(
          "Connector has not been activated yet. No context available.");
    }
    return context;
  }
}
