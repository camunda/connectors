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
package io.camunda.connector.e2e.inbound;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple test inbound connector for e2e testing of the inbound connector runtime.
 *
 * <p>This connector does nothing but track activations and deactivations.
 */
@InboundConnector(name = "Test Inbound Connector", type = "io.camunda:test-inbound-connector:1")
public class TestInboundConnector implements InboundConnectorExecutable<InboundConnectorContext> {

  private static final AtomicInteger activationCount = new AtomicInteger(0);
  private static final AtomicInteger deactivationCount = new AtomicInteger(0);

  private InboundConnectorContext context;

  @Override
  public void activate(InboundConnectorContext context) throws Exception {
    this.context = context;
    activationCount.incrementAndGet();
    context.reportHealth(Health.up());
    System.out.println("I am active!! Dedup id: " + context.getDefinition().deduplicationId());
  }

  @Override
  public void deactivate() throws Exception {
    deactivationCount.incrementAndGet();
  }

  public static int getActivationCount() {
    return activationCount.get();
  }

  public static int getDeactivationCount() {
    return deactivationCount.get();
  }

  public static void resetCounters() {
    activationCount.set(0);
    deactivationCount.set(0);
  }
}
