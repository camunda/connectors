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

import com.google.common.collect.EvictingQueue;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import java.util.function.Consumer;

/**
 * Factory interface for creating {@link InboundConnectorContext} instances.
 *
 * <p>The factory is responsible for generating context instances tailored for specific inbound
 * connectors. The type of context (standard or intermediate) is determined based on the nature of
 * the provided InboundConnectorExecutable.
 */
public interface InboundConnectorContextFactory {

  /**
   * Creates an appropriate {@link InboundConnectorContext} instance based on the provided
   * parameters.
   *
   * <p>This method will produce a context that aligns with the nature of the given connector data
   * and its execution requirements. The context is also influenced by the type of the executable
   * connector provided.
   *
   * @param connectorDetails The specific inbound connector data which gives details about the
   *     connector and its related properties.
   * @param cancellationCallback Callback that gets invoked during connector execution errors or
   *     cancellations.
   * @param executableClass Class representation of the executable connector in use.
   * @return A newly created {@link InboundConnectorContext} instance, tailored to the provided
   *     parameters.
   */
  <T extends InboundConnectorExecutable<?>> InboundConnectorContext createContext(
      final InboundConnectorDetails connectorDetails,
      final Consumer<Throwable> cancellationCallback,
      final Class<T> executableClass,
      final EvictingQueue queue);
}
