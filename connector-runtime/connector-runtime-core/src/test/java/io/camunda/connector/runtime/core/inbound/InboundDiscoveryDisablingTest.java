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
package io.camunda.connector.runtime.core.inbound;

import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class InboundDiscoveryDisablingTest {

  @Test
  public void discoveryDisablingWorks() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables("CONNECTOR_INBOUND_DISCOVERY_DISABLED", "true")
              .execute(
                  () -> {
                    InboundConnectorFactory registry = new DefaultInboundConnectorFactory();
                    Assertions.assertThrows(
                        NoSuchElementException.class,
                        () -> registry.getInstance("io.camunda:annotated"),
                        "Inbound connector should not be available when discovery is disabled");
                    registry.registerConfiguration(
                        new InboundConnectorConfiguration(
                            "Test Inbound Connector",
                            "io.camunda:test-inbound:1",
                            NotAnnotatedExecutable.class,
                            List.of()));
                    Assertions.assertInstanceOf(
                        NotAnnotatedExecutable.class,
                        registry.getInstance("io.camunda:test-inbound:1"),
                        "Manual registration should still be available");
                  });
        });
  }

  @Test
  public void namedDisablingForDiscoveredConnectors() throws Exception {

    restoreSystemProperties(
        () -> {
          withEnvironmentVariables("CONNECTOR_INBOUND_DISABLED", "io.camunda:annotated")
              .execute(
                  () -> {
                    InboundConnectorFactory registry = new DefaultInboundConnectorFactory();
                    Assertions.assertThrows(
                        NoSuchElementException.class,
                        () -> registry.getInstance("io.camunda:annotated"),
                        "Named inbound connector should not be available when disabled");
                    registry.registerConfiguration(
                        new InboundConnectorConfiguration(
                            "Test Inbound Connector",
                            "io.camunda:test-inbound:1",
                            NotAnnotatedExecutable.class,
                            List.of()));
                    Assertions.assertInstanceOf(
                        NotAnnotatedExecutable.class,
                        registry.getInstance("io.camunda:test-inbound:1"),
                        "Other connectors should still be available");
                  });
        });
  }
}
