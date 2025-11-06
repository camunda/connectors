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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Test to verify that the qualified connectors ObjectMapper is correctly picked up even when a
 * plain unannotated ObjectMapper bean exists. This prevents customers from accidentally breaking
 * their connectors runtime by defining a custom ObjectMapper bean.
 */
@SpringBootTest(
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false"
    },
    classes = {
      TestConnectorRuntimeApplication.class,
      ConnectorsObjectMapperQualifierTest.PlainObjectMapperConfig.class
    })
public class ConnectorsObjectMapperQualifierTest {

  @Autowired private ApplicationContext applicationContext;

  @Autowired
  @Qualifier("connectorsObjectMapper")
  private ObjectMapper connectorsObjectMapper;

  @Test
  void qualifiedConnectorsObjectMapperIsAvailable() {
    // Verify that the connectors ObjectMapper is available with the qualifier
    assertThat(connectorsObjectMapper).isNotNull();

    // Verify we can get it from the application context by name
    var qualifiedBean = applicationContext.getBean("connectorsObjectMapper", ObjectMapper.class);
    assertThat(qualifiedBean).isNotNull();
    assertThat(qualifiedBean).isSameAs(connectorsObjectMapper);
  }

  @Test
  void connectorsObjectMapperAndPlainObjectMapperAreDifferent() {
    // Get the plain ObjectMapper (unannotated)
    var plainMapper = applicationContext.getBean("plainObjectMapper", ObjectMapper.class);

    // Verify they are different instances - the key protection this qualifier provides
    assertThat(connectorsObjectMapper).isNotSameAs(plainMapper);
  }

  @Test
  void connectorsObjectMapperIsMarkedAsPrimary() {
    // When no qualifier is specified, the connectors ObjectMapper should be used
    // because it's marked as @Primary
    var primaryMapper = applicationContext.getBean(ObjectMapper.class);
    assertThat(primaryMapper).isSameAs(connectorsObjectMapper);
  }

  @TestConfiguration
  static class PlainObjectMapperConfig {

    @Bean
    public ObjectMapper plainObjectMapper() {
      // Create a plain ObjectMapper without connector-specific modules
      // This simulates a customer defining their own ObjectMapper bean
      return new ObjectMapper();
    }
  }
}
