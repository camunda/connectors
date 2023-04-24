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
package io.camunda.connector.runtime.inbound.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.connector.impl.ConnectorUtil;
import io.camunda.connector.impl.inbound.InboundConnectorConfiguration;
import io.camunda.connector.runtime.ConnectorRuntimeApplication;
import io.camunda.connector.runtime.inbound.TestInboundConnector;
import io.camunda.connector.runtime.inbound.webhook.WebhookConnectorExecutable;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = {ConnectorRuntimeApplication.class},
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.webhook.enabled=true",
      "operate.client.enabled=false"
    })
@ExtendWith(MockitoExtension.class)
public class SpringInboundConnectorFactoryTest {

  @Autowired private SpringInboundConnectorFactory factory;

  @Test
  void shouldDiscoverConnectorsAndActivateWebhook() {

    var webhookConfig =
        ConnectorUtil.getRequiredInboundConnectorConfiguration(WebhookConnectorExecutable.class);
    var spiConnectorConfig =
        ConnectorUtil.getRequiredInboundConnectorConfiguration(TestInboundConnector.class);

    // when

    List<InboundConnectorConfiguration> registeredConnectors = factory.getConfigurations();

    // then

    assertEquals(2, registeredConnectors.size());
    assertTrue(registeredConnectors.containsAll(List.of(webhookConfig, spiConnectorConfig)));

    // check that SPI connectors are request-scoped

    var firstInstance = factory.getInstance(spiConnectorConfig.getType());
    var secondInstance = factory.getInstance(spiConnectorConfig.getType());

    assertNotSame(firstInstance, secondInstance);
  }
}
