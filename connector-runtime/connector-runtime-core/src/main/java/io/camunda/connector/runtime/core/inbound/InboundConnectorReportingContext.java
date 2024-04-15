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

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import java.util.List;
import java.util.Queue;

public interface InboundConnectorReportingContext extends InboundConnectorContext {

  /**
   * Provides a Health object to get information about the current status of the Connector with
   * optional details.
   *
   * <p>Use the {@link #reportHealth(Health)} method to set this information
   *
   * @return Health object
   */
  Health getHealth();

  /**
   * Provides a list of ActivityLog objects to get information about the current activities of the
   * Connector with optional details.
   *
   * <p>Use the {@link #log(Activity)} method to add an entry to this list
   *
   * @return Queue containing the activities
   */
  Queue<Activity> getLogs();

  List<InboundConnectorElement> connectorElements();
}
