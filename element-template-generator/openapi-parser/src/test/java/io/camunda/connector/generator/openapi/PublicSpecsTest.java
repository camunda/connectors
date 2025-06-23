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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.ElementTemplate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PublicSpecsTest {
  private static Stream<Arguments> getTestData() {
    return Stream.of(
        Arguments.of("src/test/resources/publicSpecs/paycom.yaml", 30, 0),
        Arguments.of("src/test/resources/publicSpecs/paypalcheckout.yaml", 8, 0),
        Arguments.of("src/test/resources/publicSpecs/peachpay.yaml", 21, 1),
        Arguments.of("src/test/resources/publicSpecs/stripe.yaml", 44, 9));
  }

  private static final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  @ParameterizedTest
  @MethodSource("getTestData")
  void scanPublicSpecs(
      String source, long expectedSuccessfulOperations, long expectedFailedOperations)
      throws JsonProcessingException {
    var generator = new OpenApiOutboundTemplateGenerator();
    var scanResult = generator.scan(new OpenApiGenerationSource(List.of(source)));

    String resultString = mapper.writeValueAsString(scanResult);
    List<String> lines = Arrays.asList(resultString.split("\\R"));
    long trueCount = lines.stream().filter(line -> line.contains("supported: true")).count();
    long falseCount = lines.stream().filter(line -> line.contains("supported: false")).count();

    assertEquals(trueCount, expectedSuccessfulOperations);
    assertEquals(falseCount, expectedFailedOperations);
  }

  @ParameterizedTest
  @MethodSource("getTestData")
  void generatePublicSpecs(String source, long expectedSuccessfulOperations) {

    var generator = new OpenApiOutboundTemplateGenerator();
    List<ElementTemplate> generateResult =
        generator.generate(new OpenApiGenerationSource(List.of(source)));

    DropdownProperty d =
        (DropdownProperty)
            generateResult.getFirst().properties().stream()
                .filter(item -> Objects.equals(item.getId(), "operationId"))
                .findFirst()
                .orElseThrow();

    assertEquals(expectedSuccessfulOperations, d.getChoices().size());
  }
}
