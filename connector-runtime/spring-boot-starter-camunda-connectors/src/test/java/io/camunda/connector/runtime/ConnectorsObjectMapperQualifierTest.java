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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Test to verify that the @ConnectorsObjectMapper qualifier ensures the connectors ObjectMapper is
 * not accidentally overridden by custom ObjectMapper beans.
 */
@SpringBootTest(
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false"
    },
    classes = {
      TestConnectorRuntimeApplication.class,
      ConnectorsObjectMapperQualifierTest.CustomObjectMapperConfig.class
    })
public class ConnectorsObjectMapperQualifierTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired @ConnectorsObjectMapper private ObjectMapper connectorsObjectMapper;

  @Test
  void qualifiedConnectorsObjectMapperIsAvailable() {
    // Verify that the connectors ObjectMapper is available with the qualifier
    assertThat(connectorsObjectMapper).isNotNull();

    // Verify that we can get it using the qualifier from the application context
    var qualifiedBean = applicationContext.getBean("connectorsObjectMapper", ObjectMapper.class);
    assertThat(qualifiedBean).isNotNull();
    assertThat(qualifiedBean).isSameAs(connectorsObjectMapper);
  }

  @Test
  void customObjectMapperDoesNotOverrideConnectorsObjectMapper() {
    // Get the custom ObjectMapper bean (unqualified)
    var customMapper = applicationContext.getBean("customObjectMapper", ObjectMapper.class);
    assertThat(customMapper).isNotNull();

    // Get the connectors ObjectMapper bean (qualified)
    var connectorsMapper = applicationContext.getBean("connectorsObjectMapper", ObjectMapper.class);
    assertThat(connectorsMapper).isNotNull();

    // Verify they are different instances - this is the key test:
    // A custom ObjectMapper bean should not override the connectors ObjectMapper
    assertThat(customMapper).isNotSameAs(connectorsMapper);

    // Verify the primary bean is the connectors mapper
    var primaryMapper = applicationContext.getBean(ObjectMapper.class);
    assertThat(primaryMapper).isSameAs(connectorsMapper);
  }

  @TestConfiguration
  @Import(OutboundConnectorsAutoConfiguration.class)
  static class CustomObjectMapperConfig {

    @Bean
    public ObjectMapper customObjectMapper() {
      // Create a custom ObjectMapper with a marker to distinguish it
      var mapper = new ObjectMapper();
      mapper.registerModule(
          new com.fasterxml.jackson.databind.Module() {
            @Override
            public String getModuleName() {
              return "CustomMarker";
            }

            @Override
            public com.fasterxml.jackson.core.Version version() {
              return com.fasterxml.jackson.core.Version.unknownVersion();
            }

            @Override
            public void setupModule(SetupContext context) {
              // No setup needed, this is just a marker
            }
          });
      return mapper;
    }
  }
}
