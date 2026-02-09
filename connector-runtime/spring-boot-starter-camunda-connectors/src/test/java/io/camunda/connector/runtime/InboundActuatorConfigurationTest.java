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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.runtime.inbound.state.ProcessDefinitionCacheEndpoint;
import io.camunda.connector.runtime.inbound.state.ProcessDefinitionInspector;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InboundActuatorConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  EndpointAutoConfiguration.class,
                  WebEndpointAutoConfiguration.class,
                  InboundActuatorConfiguration.class))
          .withPropertyValues("management.endpoints.web.exposure.include=processDefinitionCache")
          .withBean(ProcessDefinitionInspector.class, () -> mock(ProcessDefinitionInspector.class));

  @Test
  void endpointAvailableWhenPropertyEnabled() {
    contextRunner
        .withPropertyValues(
            "camunda.connector.inbound.process-definition-cache.endpoint.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(ProcessDefinitionCacheEndpoint.class));
  }

  @Test
  void endpointNotAvailableWhenPropertyDisabledOrMissing() {
    contextRunner.run(
        context -> assertThat(context).doesNotHaveBean(ProcessDefinitionCacheEndpoint.class));
    contextRunner
        .withPropertyValues(
            "camunda.connector.inbound.process-definition-cache.endpoint.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(ProcessDefinitionCacheEndpoint.class));
  }
}
