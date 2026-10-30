/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

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
 * script input variable, resolves the secrets in it and completes or fails the job.
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

  /**
   * Physical tenant of a job that carries none, as {@code PhysicalTenantClientSelector} keys it. A
   * tenant ID is never blank, so this cannot alias a configured tenant such as {@code default}.
   */
  static final String NO_PHYSICAL_TENANT = "";

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
        job.getPhysicalTenantId() == null ? NO_PHYSICAL_TENANT : job.getPhysicalTenantId();
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
