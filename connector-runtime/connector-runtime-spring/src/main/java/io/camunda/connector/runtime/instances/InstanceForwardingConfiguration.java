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
package io.camunda.connector.runtime.instances;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.instances.service.DefaultInstanceForwardingService;
import io.camunda.connector.runtime.instances.service.InboundInstancesService;
import io.camunda.connector.runtime.instances.service.InstanceForwardingRouter;
import io.camunda.connector.runtime.instances.service.InstanceForwardingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InstanceForwardingConfiguration {

  @Value("${server.port:8080}")
  private int appPort;

  @Value("${camunda.connector.headless.serviceurl:}")
  private String headlessServiceUrl;

  @Value("${camunda.connector.hostname:${HOSTNAME:localhost}}")
  private String hostname;

  @Bean
  @ConditionalOnProperty(name = "camunda.connector.headless.serviceurl")
  @ConditionalOnMissingBean
  public InstanceForwardingService instanceForwardingService(ObjectMapper objectMapper) {
    return new DefaultInstanceForwardingService(
        appPort, headlessServiceUrl, hostname, objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public InstanceForwardingRouter instanceForwardingRouter(
      @Autowired(required = false) InstanceForwardingService instanceForwardingService) {
    return new InstanceForwardingRouter(instanceForwardingService);
  }

  @Bean
  @ConditionalOnMissingBean
  public InboundInstancesService inboundInstancesService(
      InboundExecutableRegistry inboundExecutableRegistry) {
    return new InboundInstancesService(inboundExecutableRegistry);
  }
}
