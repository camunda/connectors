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
package io.camunda.connector.runtime.configuration;

import static io.camunda.connector.runtime.tenant.PhysicalTenantClients.clientNames;
import static io.camunda.connector.runtime.tenant.PhysicalTenantClients.resolveClient;
import static io.camunda.connector.runtime.tenant.PhysicalTenantClients.toMapByPhysicalTenantId;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.connector.api.validation.ConfigurationValidator;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.FeelExpressionEvaluatorBuilder;
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationRegistry;
import io.camunda.connector.runtime.core.configuration.ConfigurationValidationService;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Wires configuration (credential) validation. This is <b>direction-agnostic</b>: the same
 * configuration types are consumed by both inbound and outbound connectors, so it is imported by
 * the neutral top-level runtime auto-configuration rather than the outbound-specific one — an
 * inbound-only runtime exposes it too.
 */
@Configuration
@Import(ConfigurationValidationRestController.class)
public class ConfigurationValidationConfiguration {

  @Bean
  public ConfigurationValidationRegistry configurationValidationRegistry() {
    return new ConfigurationValidationRegistry(discoverConfigurationValidators());
  }

  /**
   * A stored configuration lives on exactly one orchestration cluster, so its {@code credentialRef}
   * must be evaluated against that cluster's variables. Builds one cluster-backed evaluator per
   * configured physical tenant, mirroring the per-physical-tenant maps the outbound runtime builds
   * for document stores and secret caches.
   *
   * <p>A directly-supplied {@code FeelExpressionEvaluator} bean (the {@code @Primary} one from
   * {@code ConnectorsAutoConfiguration}, or a test/user override) is used as-is while at most one
   * client is configured, so existing single-engine deployments and overrides keep working
   * unchanged. With several clients the override is ignored: applying one engine's evaluator to
   * every physical tenant would silently resolve every configuration against that same engine —
   * exactly the bug this map exists to prevent.
   */
  private static Map<String, FeelExpressionEvaluator>
      buildFeelExpressionEvaluatorsByPhysicalTenantId(
          CamundaClientRegistry registry,
          CamundaClient legacyCamundaClient,
          FeelExpressionEvaluator injectedFeelExpressionEvaluator) {
    var names = clientNames(registry, legacyCamundaClient);
    boolean useOverride = injectedFeelExpressionEvaluator != null && names.size() <= 1;
    return names.stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    useOverride
                        ? injectedFeelExpressionEvaluator
                        : FeelExpressionEvaluatorBuilder.camundaClient(
                                resolveClient(registry, name, legacyCamundaClient))
                            .build()));
  }

  @Bean
  public Map<String, FeelExpressionEvaluator> feelExpressionEvaluatorsByPhysicalTenantId(
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) FeelExpressionEvaluator injectedFeelExpressionEvaluator) {
    return buildFeelExpressionEvaluatorsByPhysicalTenantId(
        registry, legacyCamundaClient, injectedFeelExpressionEvaluator);
  }

  /**
   * Builds the per-physical-tenant evaluator map via the plain (non-{@code @Bean}) {@code build*}
   * helper rather than declaring a {@code Map<String, FeelExpressionEvaluator>}-typed parameter or
   * calling the sibling {@code @Bean} method: Spring special-cases any {@code Map<String, X>}-typed
   * parameter by collecting all beans of type {@code X} by name, and {@code @Configuration} CGLIB
   * proxying re-resolves a {@code @Bean} method's parameters from the container even when it is
   * called directly in code. Either path would silently yield a single-entry map keyed by the
   * scalar {@code FeelExpressionEvaluator} bean's name instead of the real per-tenant map.
   */
  @Bean
  public ConfigurationValidationService configurationValidationService(
      ConfigurationValidationRegistry configurationValidationRegistry,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      @OutboundConnectorObjectMapper ObjectMapper objectMapper,
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient,
      @Autowired(required = false) FeelExpressionEvaluator injectedFeelExpressionEvaluator) {
    return new ConfigurationValidationService(
        configurationValidationRegistry,
        buildFeelExpressionEvaluatorsByPhysicalTenantId(
            registry, legacyCamundaClient, injectedFeelExpressionEvaluator),
        secretProviderAggregator,
        validationProvider,
        objectMapper);
  }

  /**
   * Discovers {@code ConfigurationValidator} implementations via the SPI {@link ServiceLoader},
   * mirroring how connectors themselves are discovered ({@code SPIConnectorDiscovery}). This is
   * package-independent: a third-party connector's validator (e.g. under {@code com.acme}) is found
   * as long as it is declared in {@code META-INF/services}, whereas a fixed base-package scan would
   * silently miss it and always answer {@code UNSUPPORTED}.
   */
  @SuppressWarnings("rawtypes")
  private static List<ConfigurationValidator<?>> discoverConfigurationValidators() {
    List<ConfigurationValidator<?>> validators = new ArrayList<>();
    for (ConfigurationValidator validator : ServiceLoader.load(ConfigurationValidator.class)) {
      validators.add(validator);
    }
    return validators;
  }
}
