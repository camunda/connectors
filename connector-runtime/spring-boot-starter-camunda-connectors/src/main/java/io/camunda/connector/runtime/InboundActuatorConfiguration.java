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
package io.camunda.connector.runtime;

import io.camunda.connector.runtime.inbound.state.ProcessDefinitionCacheEndpoint;
import io.camunda.connector.runtime.inbound.state.ProcessDefinitionInspector;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({Endpoint.class, ProcessDefinitionInspector.class})
@ConditionalOnProperty(
    value = "camunda.connector.inbound.process-definition-cache.endpoint.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class InboundActuatorConfiguration {

  @Bean
  @ConditionalOnAvailableEndpoint(endpoint = ProcessDefinitionCacheEndpoint.class)
  public ProcessDefinitionCacheEndpoint processDefinitionCacheEndpoint(
      ProcessDefinitionInspector processDefinitionInspector) {
    return new ProcessDefinitionCacheEndpoint(processDefinitionInspector);
  }
}
