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

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

@OutboundConnector(
    name = "TEST_SPRING",
    type = "io.camunda:test-outbound-spring:1",
    inputVariables = {})
public class TestSpringBasedOutboundConnector implements OutboundConnectorFunction {

  private final Environment environment;

  public TestSpringBasedOutboundConnector(@Autowired Environment environment) {
    this.environment = environment;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    return "Hello from Spring: " + Arrays.toString(environment.getDefaultProfiles());
  }
}
