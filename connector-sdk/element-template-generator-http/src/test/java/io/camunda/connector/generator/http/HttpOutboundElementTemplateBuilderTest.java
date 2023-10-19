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
package io.camunda.connector.generator.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.http.HttpOperation.ConnectorHttpMethod;
import io.camunda.connector.http.base.auth.BasicAuthentication;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HttpOutboundElementTemplateBuilderTest {

  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @Test
  void sampleTest() throws JsonProcessingException {
    var auth = new BasicAuthentication();
    auth.setUsername("john_doe");
    auth.setPassword("C0NN3CT0R5@CAMUNDA");

    var template =
        HttpOutboundElementTemplateBuilder.create()
            .id("testTemplate")
            .name("Test template")
            .description("My test template")
            .documentationRef("https://docs.camunda.io")
            .version(42)
            .servers(
                List.of(
                    new HttpServerData("https://prod.camunda.com", "Production"),
                    new HttpServerData("https://dev.camunda.com", "Development")))
            .authentication(auth)
            .operations(
                HttpOperation.builder()
                    .id("someGetRequest")
                    .label("Some GET request")
                    .method(ConnectorHttpMethod.GET)
                    .pathFeelExpression(
                        """
                            "/examples/" + exampleId
                            """
                            .stripIndent())
                    .properties(
                        StringProperty.builder()
                            .id("exampleId")
                            .label("Example ID")
                            .binding(new ZeebeInput("exampleId")))
                    .build(),
                HttpOperation.builder()
                    .id("somePostRequest")
                    .label("Some POST request")
                    .method(ConnectorHttpMethod.POST)
                    .pathFeelExpression("\"/examples\"")
                    .bodyFeelExpression(
                        """
                            {"id": exampleId, "name": exampleName}
                            """)
                    .properties(
                        StringProperty.builder()
                            .id("exampleId")
                            .label("Example ID")
                            .binding(new ZeebeInput("exampleId")),
                        StringProperty.builder()
                            .id("exampleName")
                            .label("exampleName")
                            .binding(new ZeebeInput("exampleName")))
                    .build())
            .build();

    var result = mapper.writeValueAsString(template);

    // TODO: json check
    System.out.println(result);
  }
}
