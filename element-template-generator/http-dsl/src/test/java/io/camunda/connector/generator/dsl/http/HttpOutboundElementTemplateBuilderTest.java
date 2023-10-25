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
package io.camunda.connector.generator.dsl.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import io.camunda.connector.generator.dsl.http.HttpAuthentication.BasicAuth;
import io.camunda.connector.generator.dsl.http.HttpOperationProperty.Target;
import io.camunda.connector.http.base.model.HttpMethod;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;

public class HttpOutboundElementTemplateBuilderTest {

  private final ObjectMapper mapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

  @Test
  void sampleTest() throws IOException, JSONException, URISyntaxException {

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
            .authentication(List.of(BasicAuth.INSTANCE))
            .operations(
                HttpOperation.builder()
                    .id("someGetRequest")
                    .label("Some GET request")
                    .method(HttpMethod.GET)
                    .pathFeelExpression(
                        HttpPathFeelBuilder.create()
                            .part("/examples/")
                            .property("exampleId")
                            .build())
                    .properties(
                        HttpOperationProperty.createStringProperty(
                            "exampleId", Target.PATH, "Example ID", true, "42"))
                    .build(),
                HttpOperation.builder()
                    .id("somePostRequest")
                    .label("Some POST request")
                    .method(HttpMethod.POST)
                    .pathFeelExpression("\"/examples\"")
                    .bodyExample(
                        """
                            ={"id": exampleId, "name": exampleName}
                            """)
                    .build())
            .build();

    // when
    var result = mapper.writeValueAsString(template);

    // then
    var path =
        Path.of(ClassLoader.getSystemResource("http-outbound-test-element-template.json").toURI());
    var referenceJsonString = Files.readString(path);
    JSONAssert.assertEquals(referenceJsonString, result, true);
  }
}
