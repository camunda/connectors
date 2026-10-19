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
package io.camunda.connector.runtime.managedcode;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.error.ConnectorRetryException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;

/**
 * Runs a linked Python or JavaScript script in an isolated provider. The function is served by the
 * connector runtime like any outbound connector: the runtime activates the job, fetches only the
 * script input variable and completes or fails the job.
 */
@OutboundConnector(
    name = ManagedScriptFunction.NAME,
    inputVariables = {ManagedScriptFunction.INPUT_VARIABLE},
    type = ManagedScriptFunction.JOB_TYPE,
    withLease = true)
@ElementTemplate(
    id = "io.camunda.connectors.ManagedScript.v1",
    name = "Managed script",
    version = 1,
    description = "Run a Python or JavaScript script without operating a job worker",
    engineVersion = "^8.10",
    inputDataClass = ManagedScriptRequest.class,
    propertyGroups = {
      @PropertyGroup(id = "script", label = "Script"),
      @PropertyGroup(id = "input", label = "Input")
    })
public class ManagedScriptFunction implements OutboundConnectorFunction {

  static final String NAME = "Managed script";
  static final String JOB_TYPE = "io.camunda:managed-script:1";
  static final String INPUT_VARIABLE = "scriptInput";
  static final String RESOURCE_TYPE = "ManagedScript";

  /** Physical tenant of a client configured without one, as for the worker it replaces. */
  private static final String DEFAULT_PHYSICAL_TENANT = "default";

  private final ManagedScriptJobHandler handler;
  private final PhysicalTenantClientSelector clients;

  ManagedScriptFunction(ManagedScriptJobHandler handler, PhysicalTenantClientSelector clients) {
    this.handler = handler;
    this.clients = clients;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final var job = context.getJobContext();
    final var request = context.bindVariables(ManagedScriptRequest.class);
    final var physicalTenantId =
        job.getPhysicalTenantId() == null ? DEFAULT_PHYSICAL_TENANT : job.getPhysicalTenantId();
    final var outcome =
        handler.handle(clients.forJob(job), physicalTenantId, job, request.scriptInput());
    return switch (outcome) {
      case ManagedScriptJobHandler.Outcome.Completed completed -> completed.variables();
      case ManagedScriptJobHandler.Outcome.Failed failed ->
          throw ConnectorRetryException.builder()
              .errorCode(failed.code())
              .message(failed.message())
              .retries(failed.retries())
              .backoffDuration(failed.backoff().isZero() ? null : failed.backoff())
              .build();
    };
  }
}
