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

import io.camunda.connector.runtime.app.EnvVarContextInitializer;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import io.camunda.connector.runtime.app.TestInboundConnector;
import io.camunda.connector.runtime.app.TestSpringBasedInboundConnector;
import io.camunda.connector.runtime.core.discovery.EnvironmentVariablesAdapter;
import io.camunda.connector.runtime.core.inbound.InboundConnectorFactory;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * @see EnvVarContextInitializer
 */
@SpringBootTest(
    properties = {
      "camunda.connector.test_class=io.camunda.connector.runtime.inbound.InboundNamedDisablingTest",
    },
    classes = {TestConnectorRuntimeApplication.class, TestSpringBasedInboundConnector.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class InboundNamedDisablingTest {
  @Autowired InboundConnectorFactory registry;

  // Invoked via reflection
  public static void envSetup() {
    EnvironmentVariablesAdapter.addHardwiredEnvironmentVariable(
        "CONNECTOR_INBOUND_DISABLED", "io.camunda:test-inbound-spring:1");
  }

  @AfterAll
  public static void cleanup() {
    EnvironmentVariablesAdapter.clearHardwiredEnvironmentVariable();
  }

  @Test
  public void ensureIsDisabled() {
    Assertions.assertThrows(
        NoSuchElementException.class,
        () -> registry.getInstance("io.camunda:test-inbound-spring:1"),
        "This connector was excplicitly disabled by environment variable");
    Assertions.assertInstanceOf(
        TestInboundConnector.class,
        registry.getInstance("io.camunda:test-inbound:1"),
        "Spring based registration should still be available");
  }
}
