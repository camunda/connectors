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

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.core.inbound.InboundConnectorReportingContext;
import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WebhookWaitingQueueTest {
  private WebhookWaitingQueue queue;
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
    queue = new WebhookWaitingQueue();
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
    queue.markAsDownAndAdd(CONTEXT, connector1, connector1);
    Queue<?> q = queue.getWaitingConnectors().get(CONTEXT);
    assertNotNull(q);
    assertEquals(1, q.size());
    verify(context1).reportHealth(any(Health.class));
    verify(context1).log(any(Activity.class));
  }

  @Test
  void testActivateNextConnector() {
    queue.markAsDownAndAdd(CONTEXT, connector1, connector1);
    queue.markAsDownAndAdd(CONTEXT, connector2, connector1);
    Optional<RegisteredExecutable.Activated> activated = queue.activateNext(CONTEXT);
    assertTrue(activated.isPresent());
    assertEquals(connector1, activated.get());
    verify(context1).reportHealth(argThat(h -> h != null && h.getStatus() == Health.Status.DOWN));
    verify(context1, atLeastOnce())
        .reportHealth(argThat(h -> h != null && h.getStatus() == Health.Status.UP));
    verify(context1, atLeastOnce()).log(any(Activity.class));
  }

  @Test
  void testQueueLimitExceeded() {
    for (int i = 0; i < 10; i++) {
      RegisteredExecutable.Activated c =
          new RegisteredExecutable.Activated(null, mock(InboundConnectorReportingContext.class));
      queue.markAsDownAndAdd(CONTEXT, c, c);
    }
    RegisteredExecutable.Activated extra =
        new RegisteredExecutable.Activated(null, mock(InboundConnectorReportingContext.class));
    assertThrows(RuntimeException.class, () -> queue.markAsDownAndAdd(CONTEXT, extra, extra));
  }

  @Test
  void testHealthStatusUpdates() {
    queue.markAsDownAndAdd(CONTEXT, connector1, connector1);
    queue.markAsDownAndAdd(CONTEXT, connector2, connector1);
    queue.activateNext(CONTEXT);
    verify(context1, atLeastOnce())
        .reportHealth(argThat(h -> h != null && h.getStatus().equals(Health.Status.DOWN)));
    verify(context2, atLeastOnce())
        .reportHealth(argThat(h -> h != null && h.getStatus().equals(Health.Status.UP)));
  }

  @Test
  void testActivateNextOnEmptyQueue() {
    Optional<RegisteredExecutable.Activated> result = queue.activateNext(CONTEXT);
    assertTrue(result.isEmpty());
  }

  @Test
  void testQueueCleanupAfterActivation() {
    queue.markAsDownAndAdd(CONTEXT, connector1, connector1);
    queue.activateNext(CONTEXT);
    assertNull(queue.getWaitingConnectors().get(CONTEXT));
  }

  @Test
  void testMultipleContexts() {
    queue.markAsDownAndAdd(CONTEXT, connector1, connector1);
    queue.markAsDownAndAdd(CONTEXT2, connector2, connector2);
    assertEquals(1, queue.getWaitingConnectors().get(CONTEXT).size());
    assertEquals(1, queue.getWaitingConnectors().get(CONTEXT2).size());
    queue.activateNext(CONTEXT);
    assertNull(queue.getWaitingConnectors().get(CONTEXT));
    assertNotNull(queue.getWaitingConnectors().get(CONTEXT2));
  }
}
