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
 *
 * <p>{@code POST /configurations/validate} resolves stored secrets to run a validator, and applies
 * no secret allow-list while doing so — out-of-band validation has no process or element scope to
 * derive one from. No resolved value can reach the response (see the message-safety policy on
 * {@code ConfigurationValidationService}), but the route is still expected to be reachable only by
 * trusted callers; the SaaS bundle covers it with the Console JWT {@code SecurityFilterChain}.
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
   * <p>Every evaluator is constructed here from a {@link CamundaClient}, and the ambient {@code
   * FeelExpressionEvaluator} bean is deliberately <b>not</b> consulted. That bean is only
   * {@code @ConditionalOnMissingBean}, so a deployment or test may replace it with a local
   * (embedded-engine) evaluator — which cannot reach {@code camunda.vars.env.*} at all and would
   * fail at request time in a way that is hard to trace back to the substituted bean. Constructing
   * the evaluators here makes that substitution structurally impossible rather than merely
   * discouraged.
   */
  private static Map<String, FeelExpressionEvaluator>
      buildFeelExpressionEvaluatorsByPhysicalTenantId(
          CamundaClientRegistry registry, CamundaClient legacyCamundaClient) {
    return clientNames(registry, legacyCamundaClient).stream()
        .collect(
            toMapByPhysicalTenantId(
                registry,
                legacyCamundaClient,
                name ->
                    FeelExpressionEvaluatorBuilder.camundaClient(
                            resolveClient(registry, name, legacyCamundaClient))
                        .build()));
  }

  @Bean
  public Map<String, FeelExpressionEvaluator> feelExpressionEvaluatorsByPhysicalTenantId(
      @Autowired(required = false) CamundaClientRegistry registry,
      @Autowired(required = false) CamundaClient legacyCamundaClient) {
    return buildFeelExpressionEvaluatorsByPhysicalTenantId(registry, legacyCamundaClient);
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
      @Autowired(required = false) CamundaClient legacyCamundaClient) {
    return new ConfigurationValidationService(
        configurationValidationRegistry,
        buildFeelExpressionEvaluatorsByPhysicalTenantId(registry, legacyCamundaClient),
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
