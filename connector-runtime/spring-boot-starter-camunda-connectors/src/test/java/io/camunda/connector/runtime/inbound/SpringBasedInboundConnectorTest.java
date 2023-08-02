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
package io.camunda.connector.runtime.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.app.TestSpringBasedInboundConnector;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {TestConnectorRuntimeApplication.class, TestSpringBasedInboundConnector.class})
public class SpringBasedInboundConnectorTest {

  @Autowired InboundConnectorFactory inboundConnectorFactory;

  @Autowired TestSpringBasedInboundConnector connector;

  @Test
  void springBasedConnectorPresent() {
    var connectorConfig =
        inboundConnectorFactory.getConfigurations().stream()
            .filter(c -> c.name().equals("TEST_INBOUND_SPRING"))
            .findFirst()
            .get();
    assertThat(connectorConfig).isNotNull();
    var connectorFromFactory = inboundConnectorFactory.getInstance(connectorConfig.type());
    // Same type
    assertThat(connectorFromFactory).isExactlyInstanceOf(connector.getClass());
    // Different instance
    assertThat(connectorFromFactory).isNotEqualTo(connector);
  }
}
