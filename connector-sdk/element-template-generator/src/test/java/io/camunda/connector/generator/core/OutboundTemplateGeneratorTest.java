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
package io.camunda.connector.generator.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.core.example.MyConnectorFunction;
import io.camunda.connector.generator.core.example.MyConnectorInput;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class OutboundTemplateGeneratorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void outboundTemplateGenerator_integrationTest() throws Exception {
    // given
    var generator = new OutboundElementTemplateGenerator();

    // when
    var template = generator.generate(MyConnectorFunction.class, MyConnectorInput.class);
    var jsonString = objectMapper.writeValueAsString(template);

    // then
    // TODO: this test uses a simplified version of the template, because some features like
    //  constraints are not yet implemented. Once implemented, the full template should be used.
    var path =
        Path.of(ClassLoader.getSystemResource("test-element-template-simplified.json").toURI());
    var referenceJsonString = Files.readString(path);
    JSONAssert.assertEquals(referenceJsonString, jsonString, true);
  }
}
