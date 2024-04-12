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
package io.camunda.connector.generator.postman;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.postman.utils.ObjectMapperProvider;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PostmanCollectionsGeneratorDryRunExampleTest {

  @ParameterizedTest
  @MethodSource("commandLineArguments")
  void generate(List<String> args) throws JsonProcessingException {
    var source = new PostmanCollectionsGenerationSource(args);
    var gen = new PostmanCollectionOutboundTemplateGenerator();
    var templates =
        gen.generate(
            source,
            new GeneratorConfiguration(ConnectorMode.NORMAL, null, null, 1, null, Map.of()));
    var resultString =
        ObjectMapperProvider.getInstance()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(templates);

    // Tip: consider printing output to file for quick test
    System.out.println(resultString);

    Assertions.assertThat(resultString).isNotNull();
  }

  @ParameterizedTest
  @MethodSource("commandLineArguments")
  void scan(List<String> args) {
    var source = new PostmanCollectionsGenerationSource(args);
    var gen = new PostmanCollectionOutboundTemplateGenerator();
    var scanResult = gen.scan(source);

    System.out.println(scanResult);
    Assertions.assertThat(scanResult).isNotNull();
  }

  private static Stream<Arguments> commandLineArguments() {
    return Stream.of(
        // Test case 1: generate all methods
        Arguments.of(List.of("src/test/resources/operate-api-saas-bearer.json")),

        // Test case 2: generate specific methods
        Arguments.of(
            List.of(
                "src/test/resources/postman-books.json",
                "/1. Sending requests & inspecting responses/books",
                "/1. Sending requests & inspecting responses/book",
                "/1. Sending requests & inspecting responses/add book")),

        // Test case 3: fetch from internet
        Arguments.of(
            List.of(
                "https://raw.githubusercontent.com/camunda-community-hub/camunda-8-api-postman-collection/main/Operate%20Public%20API%20-%20SaaS.postman_collection.json")),

        // Test case 4: from internet with operations
        Arguments.of(
            List.of(
                "https://raw.githubusercontent.com/camunda-community-hub/camunda-8-api-postman-collection/main/Operate%20Public%20API%20-%20SaaS.postman_collection.json",
                "/Process instances/Search for process instances",
                "/Process instances/Get process instance by key")));
  }
}
