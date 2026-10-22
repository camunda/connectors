/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.client.CamundaClient;
import io.camunda.connector.runtime.tenant.PhysicalTenantClientSelector;
import org.mockito.quality.Strictness;

/** Client selectors for tests that do not exercise physical-tenant routing themselves. */
public final class TestPhysicalTenantClientSelectors {

  private TestPhysicalTenantClientSelectors() {}

  /**
   * A selector behaving as a single-cluster runtime whose only client is never reached, for
   * collaborators that keep using their injected single-tenant beans in that case.
   */
  public static PhysicalTenantClientSelector singleTenant() {
    return singleTenant(mock(CamundaClient.class));
  }

  /** A selector behaving as a single-cluster runtime: every job resolves to {@code client}. */
  public static PhysicalTenantClientSelector singleTenant(CamundaClient client) {
    var selector =
        mock(
            PhysicalTenantClientSelector.class,
            withSettings().strictness(Strictness.LENIENT).name("singleTenantClientSelector"));
    when(selector.servesSinglePhysicalTenant()).thenReturn(true);
    when(selector.forJob(any())).thenReturn(client);
    when(selector.forPhysicalTenant(any())).thenReturn(client);
    return selector;
  }
}
