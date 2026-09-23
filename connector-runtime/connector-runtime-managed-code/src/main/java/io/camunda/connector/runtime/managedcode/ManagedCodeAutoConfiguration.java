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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.spring.bean.CamundaClientRegistry;
import io.camunda.client.spring.configuration.CamundaAutoConfiguration;
import io.camunda.connector.runtime.tenant.PhysicalTenantClients;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration
@AutoConfigureBefore(CamundaAutoConfiguration.class)
@ConditionalOnProperty(
    prefix = "camunda.connector.managed-code",
    name = "enabled",
    havingValue = "true")
@EnableConfigurationProperties(ManagedCodeProperties.class)
@EnableScheduling
public class ManagedCodeAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ManagedCodeDeploymentProvider.class)
  @ConditionalOnProperty(
      prefix = "camunda.connector.managed-code",
      name = "provider",
      havingValue = "fake")
  public FakeManagedCodeDeploymentProvider fakeManagedCodeDeploymentProvider() {
    return new FakeManagedCodeDeploymentProvider();
  }

  @Bean
  @ConditionalOnMissingBean(ManagedScriptControlPlane.class)
  public RestManagedScriptControlPlane managedScriptControlPlane(
      ManagedCodeProperties properties, ObjectProvider<ObjectMapper> objectMapperProvider) {
    return new RestManagedScriptControlPlane(
        properties.provider(), objectMapperProvider.getIfAvailable(ObjectMapper::new));
  }

  @Bean(destroyMethod = "shutdown")
  @ConditionalOnMissingBean
  public ManagedCodeReconciler managedCodeReconciler(
      ManagedScriptControlPlane controlPlane,
      ManagedCodeDeploymentProvider deploymentProvider,
      ManagedCodeProperties properties,
      @Autowired(required = false) CamundaClientRegistry registry,
      ObjectProvider<CamundaClient> camundaClientProvider) {
    if (properties.provider().isBlank()) {
      throw new IllegalStateException(
          "camunda.connector.managed-code.provider must be configured when managed code is enabled");
    }
    if (!properties.provider().equals(deploymentProvider.provider())) {
      throw new IllegalStateException(
          "Configured managed-code provider '%s' does not match provider bean '%s'"
              .formatted(properties.provider(), deploymentProvider.provider()));
    }
    var legacyClient = PhysicalTenantClients.legacyClient(camundaClientProvider);
    var clientNames = PhysicalTenantClients.clientNames(registry, legacyClient);
    Map<String, String> physicalTenantIdByClientName =
        clientNames.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    name -> name,
                    name ->
                        PhysicalTenantClients.resolvePhysicalTenantId(
                            registry, name, legacyClient)));
    Map<String, CamundaClient> clientsByPhysicalTenantId =
        clientNames.stream()
            .collect(
                PhysicalTenantClients.toMapByPhysicalTenantId(
                    registry,
                    legacyClient,
                    name -> PhysicalTenantClients.resolveClient(registry, name, legacyClient)));
    return new ManagedCodeReconciler(
        controlPlane,
        deploymentProvider,
        properties,
        physicalTenantIdByClientName,
        clientsByPhysicalTenantId);
  }
}
