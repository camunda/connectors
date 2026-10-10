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

import java.util.Objects;

/** Immutable ready-state and artifact data needed to execute one managed-script job. */
public record ManagedScriptInvocation(
    Status status,
    String provider,
    String providerDeploymentId,
    String language,
    String runtime,
    long resourceKey,
    String resourceName,
    byte[] artifact) {

  public ManagedScriptInvocation {
    Objects.requireNonNull(status, "status");
    provider = Objects.requireNonNull(provider, "provider");
    providerDeploymentId = Objects.requireNonNullElse(providerDeploymentId, "");
    language = Objects.requireNonNull(language, "language");
    runtime = Objects.requireNonNull(runtime, "runtime");
    resourceName = Objects.requireNonNull(resourceName, "resourceName");
    artifact = Objects.requireNonNull(artifact, "artifact").clone();
  }

  @Override
  public byte[] artifact() {
    return artifact.clone();
  }

  public enum Status {
    PENDING,
    BUILDING,
    DEPLOYING,
    READY,
    FAILED
  }
}
