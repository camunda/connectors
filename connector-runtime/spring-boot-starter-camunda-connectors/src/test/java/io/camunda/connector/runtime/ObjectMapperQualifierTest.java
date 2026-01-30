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
import io.camunda.connector.runtime.annotation.OutboundConnectorObjectMapper;
import io.camunda.connector.runtime.app.TestConnectorRuntimeApplication;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "camunda.connector.polling.enabled=false"
    },
    classes = {Jackson2AutoConfiguration.class, TestConnectorRuntimeApplication.class})
public class ObjectMapperQualifierTest {

  @Autowired @ConnectorsObjectMapper private ObjectMapper connectorObjectMapper;

  @Autowired @OutboundConnectorObjectMapper private ObjectMapper outboundConnectorObjectMapper;

  @Autowired private ObjectMapper defaultObjectMapper;

  @Test
  void shouldInjectConnectorObjectMapperWithQualifier() {
    assertThat(connectorObjectMapper).isNotNull();
    assertThat(outboundConnectorObjectMapper).isNotNull();
    assertThat(defaultObjectMapper).isNotNull();

    // All three mappers should be different instances
    assertThat(connectorObjectMapper).isNotSameAs(defaultObjectMapper);
    assertThat(connectorObjectMapper).isNotSameAs(outboundConnectorObjectMapper);
    assertThat(outboundConnectorObjectMapper).isNotSameAs(defaultObjectMapper);
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
  void connectorObjectMapperShouldEvaluateFeelFunctions() throws JsonProcessingException {
    // The default ConnectorsObjectMapper should evaluate FEEL functions (Supplier)
    var json =
        """
        {
         "name": "test",
         "greetingSupplier": "= \\"Hello World\\""
        }""";

    var feelObject = connectorObjectMapper.readValue(json, TestFeelClass.class);
    // FEEL function is evaluated - calling get() returns the evaluated value
    assertThat(feelObject.greetingSupplier.get()).isEqualTo("Hello World");
  }

  @Test
  void outboundConnectorObjectMapperShouldNotEvaluateFeelExpressions()
      throws JsonProcessingException {
    // The OutboundConnectorObjectMapper has FEEL functions DISABLED
    // So @FEEL annotated fields should NOT evaluate FEEL expressions
    var json =
        """
        {
         "name": "= \\"test \\" + \\"User\\" ",
         "greetingSupplier": "= \\"Hello World\\""
        }""";

    var feelObject = outboundConnectorObjectMapper.readValue(json, TestFeelClass.class);
    // @FEEL annotation does NOT work - the FEEL expression is NOT evaluated
    assertThat(feelObject.name).isEqualTo("= \"test \" + \"User\" ");
  }

  @Test
  void outboundConnectorObjectMapperShouldNotEvaluateFeelFunctions()
      throws JsonProcessingException {
    // The OutboundConnectorObjectMapper has FEEL functions DISABLED
    // So Supplier fields should NOT be evaluated as FEEL expressions
    var json =
        """
        {
         "name": "test",
         "greetingSupplier": "= \\"Hello World\\""
        }""";

    var feelObject = outboundConnectorObjectMapper.readValue(json, TestFeelClass.class);
    // FEEL function is NOT evaluated - calling get() returns the evaluated value
    // because the Supplier is still deserialized, but the FEEL wrapper returns the evaluated result
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
