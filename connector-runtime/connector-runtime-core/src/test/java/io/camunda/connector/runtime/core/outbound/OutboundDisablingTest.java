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
package io.camunda.connector.runtime.core.outbound;

import static uk.org.webcompere.systemstubs.SystemStubs.restoreSystemProperties;
import static uk.org.webcompere.systemstubs.SystemStubs.withEnvironmentVariables;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class OutboundDisablingTest {

  @OutboundConnector(
      name = "local_class",
      type = "io.camunda:local",
      inputVariables = {"foo", "bar"})
  private static class AnnotatedLocalFunction implements OutboundConnectorFunction {

    @Override
    public Object execute(OutboundConnectorContext context) throws Exception {
      return null;
    }
  }

  @Test
  public void disablingWorksForDiscoveredConnectors() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables("CONNECTOR_OUTBOUND_DISCOVERY_DISABLED", "true")
              .execute(
                  () -> {
                    OutboundConnectorFactory factory =
                        DiscoveryUtils.getFactory(new AnnotatedLocalFunction());
                    Assertions.assertThrows(
                        RuntimeException.class,
                        () -> factory.getInstance("io.camunda:annotated"),
                        "Outbound connector should not be available when discovery is disabled");
                    Assertions.assertInstanceOf(
                        AnnotatedLocalFunction.class,
                        factory.getInstance("io.camunda:local"),
                        "Manual registration should still be available");
                  });
        });
  }

  @Test
  public void namedDisablingForDiscoveredConnectors() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables("CONNECTOR_OUTBOUND_DISABLED", "io.camunda:annotated")
              .execute(
                  () -> {
                    OutboundConnectorFactory factory =
                        DiscoveryUtils.getFactory(new AnnotatedLocalFunction());
                    Assertions.assertThrows(
                        RuntimeException.class,
                        () -> factory.getInstance("io.camunda:annotated"),
                        "This connector was explicitly disabled by environment variable");

                    Assertions.assertInstanceOf(
                        AnnotatedLocalFunction.class,
                        factory.getInstance("io.camunda:local"),
                        "Manual registration should still be available");
                  });
        });
  }

  @Test
  public void namedDisablingForManuallyRegisteredConnectors() throws Exception {
    restoreSystemProperties(
        () -> {
          withEnvironmentVariables("CONNECTOR_OUTBOUND_DISABLED", "io.camunda:local")
              .execute(
                  () -> {
                    OutboundConnectorFactory factory =
                        DiscoveryUtils.getFactory(new AnnotatedLocalFunction());
                    Assertions.assertInstanceOf(
                        SpiRegisteredFunction.class,
                        factory.getInstance("io.camunda:annotated"),
                        "Discovery should still be available");

                    Assertions.assertThrows(
                        RuntimeException.class,
                        () -> factory.getInstance("io.camunda:local"),
                        "This connector was explicitly disabled by environment variable");
                  });
        });
  }
}
