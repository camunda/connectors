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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.instances.service.ForwardingInstanceForwardingRouter;
import io.camunda.connector.runtime.instances.service.InstanceForwardingRouter;
import io.camunda.connector.runtime.instances.service.LocalInstanceForwardingRouter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InstanceForwardingConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(InstanceForwardingConfiguration.class)
          .withBean(ObjectMapper.class, ObjectMapper::new);

  @Test
  void shouldCreateLocalRouter_whenHeadlessServiceUrlIsNotConfigured() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(InstanceForwardingRouter.class);
          assertThat(context.getBean(InstanceForwardingRouter.class))
              .isInstanceOf(LocalInstanceForwardingRouter.class);
        });
  }

  @Test
  void shouldCreateForwardingRouter_whenHeadlessServiceUrlIsConfigured() {
    contextRunner
        .withPropertyValues("camunda.connector.headless.serviceurl=http://connectors-headless")
        .run(
            context -> {
              assertThat(context).hasSingleBean(InstanceForwardingRouter.class);
              assertThat(context.getBean(InstanceForwardingRouter.class))
                  .isInstanceOf(ForwardingInstanceForwardingRouter.class);
            });
  }
}
