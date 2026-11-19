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
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.postman.utils.ObjectMapperProvider;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PostmanCollectionsGeneratorDryRunExampleTest {

  private static final String LOCAL_COLLECTION = "src/test/resources/operate-api-saas-bearer.json";
  private static final String COLLECTION_URL_PATH = "/operate-api.postman_collection.json";

  // Serves the collection over HTTP from the local machine, so the "fetch from a URL" cases do not
  // depend on the internet (they failed with "Connection reset" on CI).
  private static HttpServer collectionServer;

  @BeforeAll
  static void startCollectionServer() throws IOException {
    collectionServer =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    collectionServer.createContext(
        COLLECTION_URL_PATH,
        exchange -> {
          byte[] body = Files.readAllBytes(Path.of(LOCAL_COLLECTION));
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          try (var responseBody = exchange.getResponseBody()) {
            responseBody.write(body);
          }
        });
    collectionServer.start();
  }

  @AfterAll
  static void stopCollectionServer() {
    if (collectionServer != null) {
      collectionServer.stop(0);
    }
  }

  private static String collectionUrl() {
    return "http://"
        + collectionServer.getAddress().getHostString()
        + ":"
        + collectionServer.getAddress().getPort()
        + COLLECTION_URL_PATH;
  }

  @ParameterizedTest
  @MethodSource("commandLineArguments")
  void generate(List<String> args) throws JsonProcessingException {
    var source = new PostmanCollectionsGenerationSource(args);
    var gen = new PostmanCollectionOutboundTemplateGenerator();
    var templates =
        gen.generate(
            source,
            new GeneratorConfiguration(ConnectorMode.NORMAL, null, null, 1L, null, Map.of()));
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
    String postmanCollectionsJsonContent = getJsonContent();
    String postmanCollectionsYamlContent = getYamlContent();

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

        // Test case 3: fetch from a URL
        Arguments.of(List.of(collectionUrl())),

        // Test case 4: from a URL with operations
        Arguments.of(
            List.of(
                collectionUrl(),
                "/Process instances/Search for process instances",
                "/Process instances/Get process instance by key")),

        // Test case 5: from raw json content
        Arguments.of(List.of(postmanCollectionsJsonContent)),

        // Test case 6: from raw yaml content
        Arguments.of(List.of(postmanCollectionsYamlContent)));
  }

  private static String getJsonContent() {
    try (var openApiJsonContent =
        new FileInputStream("src/test/resources/operate-api-saas-bearer.json")) {
      return ObjectMapperProvider.getInstance()
          .readValue(openApiJsonContent, JsonNode.class)
          .toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static String getYamlContent() {
    try (var openApiYamlContent = new FileInputStream("src/test/resources/example.yaml")) {
      byte[] b = new byte[openApiYamlContent.available()];
      if (openApiYamlContent.read(b) == -1) {
        throw new RuntimeException("Failed to read yaml file!");
      }
      return new String(b);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
