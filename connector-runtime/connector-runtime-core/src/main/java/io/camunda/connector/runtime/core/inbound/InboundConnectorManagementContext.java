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

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import java.util.List;
import java.util.Map;

public interface InboundConnectorManagementContext extends InboundConnectorContext {

  /**
   * Provides a Health object to get information about the current status of the Connector with
   * optional details.
   *
   * <p>Use the {@link #reportHealth(Health)} method to set this information
   *
   * @return Health object
   */
  Health getHealth();

  List<InboundConnectorElement> connectorElements();

  /**
   * Binds the given raw element properties to an instance of {@code cls}, applying secret
   * replacement, validation and FEEL evaluation/deserialization the same way as {@link
   * #bindProperties(Class)}.
   *
   * <p>Unlike {@link #bindProperties(Class)}, which always binds the properties of the (first)
   * element this context was created from, this overload binds arbitrary element properties. This
   * is needed when several elements are deduplicated into a single executable but a request must be
   * answered using the properties of the element that was actually activated (e.g. the webhook
   * response expression).
   *
   * @param cls the target type to bind to
   * @param rawProperties the unwrapped, raw properties of the element (FEEL not evaluated, secret
   *     placeholders not resolved), e.g. {@link
   *     io.camunda.connector.api.inbound.ProcessElement#properties()}
   */
  default <T> T bindProperties(Class<T> cls, Map<String, String> rawProperties) {
    throw new UnsupportedOperationException(
        "Binding arbitrary element properties is not supported by this context implementation");
  }

  Long getActivationTimestamp();

  /** Updates the connector details */
  void updateConnectorDetails(ValidInboundConnectorDetails connectorDetails);
}
