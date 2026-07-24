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
package io.camunda.connector.runtime.inbound;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.core.inbound.correlation.InboundCorrelationHandler;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Per-physical-tenant {@link InboundCorrelationHandler} beans, split out of {@link
 * InboundConnectorRuntimeConfiguration} since they form a self-contained group.
 */
@Configuration
public class InboundCorrelationConfiguration {

  @Value("${camunda.connector.inbound.message.ttl:PT1H}")
  private Duration messageTtl;

  /**
   * Plain (non-{@code @Bean}), {@code public static} so it can also be called directly from {@link
   * InboundConnectorRuntimeConfiguration#springInboundConnectorContextFactory}, without either
   * declaring a {@code Map<String, InboundCorrelationHandler>}-typed {@code @Bean} parameter — see
   * {@link PhysicalTenantIds} for why that matters.
   */
  public static Map<String, InboundCorrelationHandler> buildCorrelationHandlersByPhysicalTenantId(
      CamundaClientRegistry registry,
      CamundaClient legacyCamundaClient,
      ObjectMapper objectMapper,
      Duration messageTtl,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    return registry.clientNames().stream()
        .collect(
            PhysicalTenantIds.toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    new MeteredInboundCorrelationHandler(
                        PhysicalTenantIds.resolveClient(registry, name, legacyCamundaClient),
                        objectMapper,
                        messageTtl,
                        connectorsInboundMetrics)));
  }

  @Bean
  public Map<String, InboundCorrelationHandler> correlationHandlersByPhysicalTenantId(
      final CamundaClientRegistry registry,
      @Autowired(required = false) final CamundaClient legacyCamundaClient,
      @ConnectorsObjectMapper final ObjectMapper objectMapper,
      final ConnectorsInboundMetrics connectorsInboundMetrics) {
    return buildCorrelationHandlersByPhysicalTenantId(
        registry, legacyCamundaClient, objectMapper, messageTtl, connectorsInboundMetrics);
  }

  /**
   * Backward-compatible scalar bean for existing single-physical-tenant call sites that
   * {@code @Autowired} {@link InboundCorrelationHandler} directly rather than the
   * per-physical-tenant map. {@code @Lazy} so it is only resolved (and only then required to be
   * unambiguous) if something actually injects it — a genuine multi-physical-tenant context that
   * never does so is unaffected.
   */
  @Bean
  @Lazy
  public InboundCorrelationHandler inboundCorrelationHandler(
      CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @ConnectorsObjectMapper ObjectMapper objectMapper,
      ConnectorsInboundMetrics connectorsInboundMetrics) {
    return PhysicalTenantIds.onlyValue(
        buildCorrelationHandlersByPhysicalTenantId(
            registry, legacyCamundaClient, objectMapper, messageTtl, connectorsInboundMetrics),
        InboundCorrelationHandler.class);
  }
}
