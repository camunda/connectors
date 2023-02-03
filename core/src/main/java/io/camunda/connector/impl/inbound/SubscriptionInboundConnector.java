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
package io.camunda.connector.impl.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.inbound.InboundConnectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for all inbound connectors that implement a subscription model. Wraps the {@link
 * InboundConnectorExecutable} interface into a more concrete form, providing the intuitive {@link
 * #subscribe()} and {@link #onEvent(Object)} methods.
 */
public abstract class SubscriptionInboundConnector implements InboundConnectorExecutable {

  private static final Logger LOG = LoggerFactory.getLogger(SubscriptionInboundConnector.class);

  protected InboundConnectorProperties properties = null;
  protected InboundConnectorContext context = null;

  @Override
  public final void activate(InboundConnectorProperties properties, InboundConnectorContext context)
      throws Exception {

    this.properties = properties;
    this.context = context;

    // TODO: can we cast properties to a specific type provided by user?
    // e.g. cast InboundConnectorProperties to RabbitMqInboundProperties after resolving secrets
    context.replaceSecrets(properties);

    LOG.debug("Activating inbound subscription " + properties.getType());
    subscribe();
  }

  /**
   * This will be called by the runtime to initialize the Connector. Implement this method with your
   * custom subscription logic.
   *
   * <p>Make sure that the custom subscription logic calls {@link #onEvent(Object)} whenever an
   * event occurs.
   *
   * @throws Exception Subscription may throw any exception
   */
  public abstract void subscribe() throws Exception;

  /**
   * Calls the upstream Connector runtime to trigger the actual process event that corresponds to
   * this inbound connector instance.
   *
   * @param event Variables to be passed alongside with the inbound event
   */
  protected final void onEvent(Object event) {
    context.correlate(properties.getCorrelationPoint(), event);
  }
}
