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
package io.camunda.connector.runtime.inbound.webhook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import java.util.LinkedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WebhookExecutablesTest {
  private RegisteredExecutable.Activated connector1;
  private RegisteredExecutable.Activated connector2;
  private RegisteredExecutable.Activated connector3;
  private InboundConnectorReportingContext context1;
  private InboundConnectorReportingContext context2;
  private InboundConnectorReportingContext context3;
  private static final String CONTEXT = "testContext";
  private static final String CONTEXT2 = "otherContext";

  @BeforeEach
  void setUp() {
    context1 = mock(InboundConnectorReportingContext.class);
    context2 = mock(InboundConnectorReportingContext.class);
    context3 = mock(InboundConnectorReportingContext.class);
    connector1 = new RegisteredExecutable.Activated(null, context1);
    connector2 = new RegisteredExecutable.Activated(null, context2);
    connector3 = new RegisteredExecutable.Activated(null, context3);
    when(context1.getLogs()).thenReturn(new LinkedList<>());
    when(context2.getLogs()).thenReturn(new LinkedList<>());
    when(context3.getLogs()).thenReturn(new LinkedList<>());
  }

  @Test
  void testAddConnectorToQueue() {
    WebhookExecutables executables = new WebhookExecutables(connector1, CONTEXT);
    executables.markAsDownAndAdd(connector2);

    assertEquals(2, executables.getExecutables().size());
    assertTrue(executables.getExecutables().contains(connector1));
    assertTrue(executables.getExecutables().contains(connector2));
  }

  @Test
  void testTryActivateNextConnector() {}

  @Test
  void testHealthStatusUpdates() {}

  @Test
  void testTryActivateNextOnEmptyQueue() {}

  @Test
  void testQueueCleanupAfterActivation() {}

  @Test
  void testMultipleContexts() {}
}
