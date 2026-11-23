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

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

/**
 * The Helm chart configures OIDC through camunda.client.auth.client-id / client-secret without
 * setting camunda.client.auth.method, relying on the camunda-spring-boot-starter's environment
 * post-processor to detect it. If that post-processor is not picked up by Spring Boot, the client
 * silently falls back to no credentials (https://github.com/camunda/connectors/issues/10745).
 */
class CamundaClientAuthMethodDetectionTest {

  @Test
  void implicitOidcAuthMethodIsDetectedFromClientCredentials() {
    try (var context =
        new SpringApplicationBuilder(EmptyConfiguration.class)
            .web(WebApplicationType.NONE)
            .properties(
                "camunda.client.auth.client-id=connectors",
                "camunda.client.auth.client-secret=secret")
            .run()) {

      assertThat(context.getEnvironment().getProperty("camunda.client.auth.method"))
          .as(
              "camunda.client.auth.method should be detected as oidc by the starter's"
                  + " EnvironmentPostProcessor; null means Spring Boot did not run it")
          .isEqualTo("oidc");
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class EmptyConfiguration {}
}
