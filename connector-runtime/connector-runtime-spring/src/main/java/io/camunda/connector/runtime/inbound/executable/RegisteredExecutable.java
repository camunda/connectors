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
package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.core.inbound.InboundConnectorManagementContext;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.InvalidInboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;

public sealed interface RegisteredExecutable {

  ExecutableId id();

  record Activated(
      InboundConnectorExecutable<InboundConnectorContext> executable,
      InboundConnectorManagementContext context,
      ExecutableId id)
      implements RegisteredExecutable {}

  record Cancelled(
      InboundConnectorExecutable<InboundConnectorContext> executable,
      InboundConnectorManagementContext context,
      Throwable exceptionThrown,
      ExecutableId id)
      implements RegisteredExecutable {}

  record ConnectorNotRegistered(ValidInboundConnectorDetails data, ExecutableId id)
      implements RegisteredExecutable {}

  record FailedToActivate(InboundConnectorDetails data, String reason, ExecutableId id)
      implements RegisteredExecutable {}

  record InvalidDefinition(InvalidInboundConnectorDetails data, String reason, ExecutableId id)
      implements RegisteredExecutable {}
}
