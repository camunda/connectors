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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.inbound.ElementTemplateDetails;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextImpl;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementWithRuntimeData;
import io.camunda.connector.runtime.core.inbound.ProcessInstanceClient;
import io.camunda.connector.runtime.core.inbound.correlation.MessageCorrelationPoint.StandaloneMessageCorrelationPoint;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.inbound.state.ProcessDefinitionInspector;
import io.camunda.connector.runtime.metrics.ConnectorsInboundMetrics;
import io.camunda.connector.runtime.outbound.job.ConfigurableSecretFilterFactory.SecretFilterMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

class InboundConnectorRuntimeConfigurationTest {

  private final InboundConnectorRuntimeConfiguration configuration =
      new InboundConnectorRuntimeConfiguration();

  private InboundConnectorContextImpl contextForSecretFilterMode(SecretFilterMode mode) {
    var client = mock(CamundaClient.class, RETURNS_DEEP_STUBS);
    when(client.getConfiguration().getPhysicalTenantId()).thenReturn("tenant");
    var registry = mock(CamundaClientRegistry.class);
    when(registry.clientNames()).thenReturn(Set.of("engine-a"));
    when(registry.get("engine-a")).thenReturn(client);
    var factory =
        configuration.springInboundConnectorContextFactory(
            ConnectorsObjectMapperSupplier.getCopy(),
            mock(ConnectorsInboundMetrics.class),
            new SecretProviderAggregator(List.of(alwaysResolvingProvider())),
            mock(ValidationProvider.class),
            Map.of("tenant", mock(ProcessInstanceClient.class)),
            mock(DocumentFactory.class),
            registry,
            null,
            mode);
    return (InboundConnectorContextImpl)
        factory.createContext(
            detailsDeclaringNoSecret(), e -> {}, TestExecutable.class, entry -> {});
  }

  private static ValidInboundConnectorDetails detailsDeclaringNoSecret() {
    var properties = Map.of("inbound.type", "io.camunda:connector:1");
    var element =
        new InboundConnectorElement(
            properties,
            new StandaloneMessageCorrelationPoint("", "", null, null),
            new ProcessElementWithRuntimeData(
                "process",
                null,
                null,
                0,
                0,
                "id",
                null,
                null,
                "<default>",
                "tenant",
                new ElementTemplateDetails("Test", "1", "icon"),
                properties));
    return (ValidInboundConnectorDetails)
        InboundConnectorDetails.of(element.deduplicationId(List.of()), List.of(element));
  }

  private static final ObjectMapper PROBE_MAPPER = new ObjectMapper();

  private static String resolveUndeclared(InboundConnectorContextImpl context) {
    var probe = PROBE_MAPPER.createObjectNode().put("value", "secrets.UNDECLARED");
    return context
        .getSecretHandler()
        .replaceSecrets(probe, new SecretContext("t", "p"))
        .get("value")
        .asText();
  }

  private static SecretProvider alwaysResolvingProvider() {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return "resolved";
      }
    };
  }

  @Test
  void springInboundConnectorContextFactory_filtersSecretsUnlessModeIsDisabled() {
    assertEquals(
        "secrets.UNDECLARED",
        resolveUndeclared(contextForSecretFilterMode(SecretFilterMode.STRICT)));
    assertEquals(
        "secrets.UNDECLARED", resolveUndeclared(contextForSecretFilterMode(SecretFilterMode.LAX)));
    assertEquals(
        "resolved", resolveUndeclared(contextForSecretFilterMode(SecretFilterMode.DISABLED)));
  }

  static class TestExecutable implements InboundConnectorExecutable<InboundConnectorContext> {
    @Override
    public void activate(InboundConnectorContext context) {}

    @Override
    public void deactivate() {}
  }

  @Test
  void processDefinitionCacheManager_whenEnabled_returnsCaffeineCacheManager() {
    var cacheManager = configuration.processDefinitionCacheManager(true, 1000);

    assertInstanceOf(CaffeineCacheManager.class, cacheManager);
  }

  @Test
  void processDefinitionCacheManager_whenDisabled_returnsNoOpCacheManager() {
    var cacheManager = configuration.processDefinitionCacheManager(false, 1000);

    assertInstanceOf(NoOpCacheManager.class, cacheManager);
  }

  @Test
  void processDefinitionCacheManager_whenDisabled_cacheNeverStoresValues() throws Exception {
    var cacheManager = configuration.processDefinitionCacheManager(false, 1000);
    Cache cache = cacheManager.getCache(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME);

    var callCount = new AtomicInteger(0);
    cache.get("key", callCount::incrementAndGet);
    cache.get("key", callCount::incrementAndGet);

    assertEquals(2, callCount.get(), "NoOp cache must call loader on every get");
  }
}
