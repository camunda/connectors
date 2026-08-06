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
package io.camunda.connector.runtime.secret;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.secret.SecretContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Covers how the scope flags compose the environment variable name that a secret is looked up
 * under. The pre-existing tenant/process-definition combinations are asserted too, since they were
 * previously built by dedicated methods and are now assembled from a segment list.
 */
class EnvironmentSecretProviderNameCompositionTest {

  private static final SecretContext CONTEXT =
      new SecretContext("my-tenant", "my-process", "engine-1");

  private final MockEnvironment environment = new MockEnvironment();

  private String resolve(
      String variableName,
      boolean physicalTenantAware,
      boolean tenantAware,
      boolean processDefinitionAware,
      SecretContext context) {
    environment.setProperty(variableName, "the-value");
    var provider =
        new EnvironmentSecretProvider(
            environment, "SECRET_", physicalTenantAware, tenantAware, processDefinitionAware);
    return provider.getSecret("FOO", context);
  }

  @Test
  void unscoped_looksUpThePlainPrefixedName() {
    assertThat(resolve("SECRET_FOO", false, false, false, CONTEXT)).isEqualTo("the-value");
  }

  @Test
  void unscoped_resolvesWithoutADependencyOnTheContext() {
    // SecretProviderAggregator is called with a null context in places; an unscoped provider must
    // not dereference it
    assertThat(resolve("SECRET_FOO", false, false, false, null)).isEqualTo("the-value");
  }

  @Test
  void tenantAware_prependsTheTenant() {
    assertThat(resolve("SECRET_my-tenant_FOO", false, true, false, CONTEXT)).isEqualTo("the-value");
  }

  @Test
  void processDefinitionAware_prependsTheProcessDefinition() {
    assertThat(resolve("SECRET_my-process_FOO", false, false, true, CONTEXT))
        .isEqualTo("the-value");
  }

  @Test
  void tenantAndProcessDefinitionAware_prependsBothInOrder() {
    assertThat(resolve("SECRET_my-tenant_my-process_FOO", false, true, true, CONTEXT))
        .isEqualTo("the-value");
  }

  @Test
  void physicalTenantAware_prependsThePhysicalTenant() {
    assertThat(resolve("SECRET_engine-1_FOO", true, false, false, CONTEXT)).isEqualTo("the-value");
  }

  @Test
  void physicalTenantAware_orderedOutermostWhenCombinedWithTheOtherScopes() {
    assertThat(resolve("SECRET_engine-1_my-tenant_my-process_FOO", true, true, true, CONTEXT))
        .isEqualTo("the-value");
  }

  @Test
  void physicalTenantAware_fallsBackToDefaultWhenThePhysicalTenantIsUnknown() {
    // against a cluster that predates multi-engine support. Resolving the unscoped SECRET_FOO
    // instead would let a misconfigured multi-engine deployment share one secret across engines.
    var noPhysicalTenant = new SecretContext("my-tenant", "my-process", null);

    assertThat(resolve("SECRET_default_FOO", true, false, false, noPhysicalTenant))
        .isEqualTo("the-value");
  }

  @Test
  void physicalTenantAware_doesNotFallBackToTheUnscopedName() {
    assertThat(resolve("SECRET_FOO", true, false, false, CONTEXT)).isNull();
  }

  @Test
  void physicalTenantAware_doesNotResolveASecretScopedToAnotherEngine() {
    assertThat(resolve("SECRET_engine-2_FOO", true, false, false, CONTEXT)).isNull();
  }
}
