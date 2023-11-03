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
package io.camunda.connector.generator.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ExampleTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void generate() throws JsonProcessingException {
    // given
    var parser = new OpenAPIV3Parser();
    // read resource from classpath
    var openApi = parser.read("web-modeler-rest-api.json");
    var generator = new OpenApiOutboundTemplateGenerator();

    // when
    var template = generator.generate(new OpenApiGenerationSource(openApi, Set.of()), null);

    // then
    System.out.println(mapper.writeValueAsString(template));
  }

  @Test
  void scan() {
    var parser = new OpenAPIV3Parser();
    var openApi = parser.read("web-modeler-rest-api.json");
    var generator = new OpenApiOutboundTemplateGenerator();

    var scanResult = generator.scan(new OpenApiGenerationSource(openApi, Set.of()));
    System.out.println(scanResult);
  }
}
