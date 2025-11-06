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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false"
    },
    classes = {TestConnectorRuntimeApplication.class})
public class ObjectMapperQualifierTest {

  @Autowired @ConnectorsObjectMapper private ObjectMapper connectorObjectMapper;

  @Autowired private ObjectMapper defaultObjectMapper;

  @Test
  void shouldInjectConnectorObjectMapperWithQualifier() {
    assertThat(connectorObjectMapper).isNotNull();
    assertThat(defaultObjectMapper).isNotNull();

    // The qualified connector mapper should be different from the default/custom mapper
    assertThat(connectorObjectMapper).isNotSameAs(defaultObjectMapper);
  }

  @Test
  void connectorObjectMapperShouldSupportFeelDeserialization() throws JsonProcessingException {
    // Test that the connector ObjectMapper has FEEL support
    var json =
        """
        {
         "name": "= \\"test \\" + \\"User\\" ",
         "greetingSupplier": "= \\"Hello World\\""
        }""";

    var feelObject = connectorObjectMapper.readValue(json, TestFeelClass.class);
    assertThat(feelObject.name).isEqualTo("test User");
    assertThat(feelObject.greetingSupplier.get()).isEqualTo("Hello World");
  }

  @Test
  void customObjectMapperShouldNotSupportFeelDeserialization() throws JsonProcessingException {
    // The custom/default ObjectMapper should NOT support FEEL expressions
    // It will just read the literal string value
    var json =
        """
        {
         "name": "= \\"test \\" + \\"User\\" "
        }""";

    var simpleObject = defaultObjectMapper.readValue(json, SimpleClass.class);
    // Without FEEL module, it reads the literal string including the FEEL expression syntax
    assertThat(simpleObject.name).isEqualTo("= \"test \" + \"User\" ");
  }

  private record TestFeelClass(@FEEL String name, Supplier<String> greetingSupplier) {}

  private record SimpleClass(String name) {}
}
