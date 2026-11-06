/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript;

import io.camunda.connector.managedscript.api.ExecutorReference;
import io.camunda.connector.managedscript.api.ManagedCodeProvider;
import java.util.List;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * {@code GET /actuator/managedcode}: the executors this runtime instance may call, the executors of
 * its own wrapper (ADR-0008). Controller, or the operator, deletes the functions under the prefix
 * that no instance reports. The names are computed, not listed, so the endpoint makes no call to
 * the platform. A failure answers with an error status, never with an empty list, which would mean
 * that every function under the prefix can go.
 *
 * <p>An actuator endpoint, so that it is served on the management port of each instance, which the
 * caller reaches without going through the load balancer, and secured like the other actuator
 * endpoints. It changes nothing and holds no secret: the names follow from the prefix and the
 * wrapper digest, which is published in the API module.
 */
@Endpoint(id = ManagedCodeEndpoint.ID)
public class ManagedCodeEndpoint {

  static final String ID = "managedcode";

  private final ManagedCodeProvider provider;
  private final String deploymentNamePrefix;

  public ManagedCodeEndpoint(ManagedCodeProvider provider, String deploymentNamePrefix) {
    this.provider = provider;
    this.deploymentNamePrefix = deploymentNamePrefix;
  }

  @ReadOperation
  public Executors executors() {
    return new Executors(
        provider.name(),
        deploymentNamePrefix,
        provider.executors().stream().map(Executor::of).toList());
  }

  /**
   * @param provider the value of {@code camunda.connector.managed-code.provider}
   * @param deploymentNamePrefix the prefix of every executor name of the cluster
   * @param executors one per language
   */
  public record Executors(String provider, String deploymentNamePrefix, List<Executor> executors) {}

  /**
   * @param language {@code python} or {@code javascript}
   * @param name the name of the function
   * @param logGroup the log group to delete with the function; {@code null} when the platform has
   *     none
   */
  public record Executor(String language, String name, String logGroup) {

    static Executor of(ExecutorReference reference) {
      return new Executor(reference.language().language(), reference.name(), reference.logGroup());
    }
  }
}
