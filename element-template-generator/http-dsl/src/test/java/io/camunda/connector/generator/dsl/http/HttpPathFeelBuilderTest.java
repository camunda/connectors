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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class HttpPathFeelBuilderTest {

  @Test
  void severalConstantPartsPath() {
    // when
    String s = HttpPathFeelBuilder.create().part("/hello").part("/world").build();

    // then
    assertThat(s).isEqualTo("=\"/hello\"+\"/world\"");
  }

  @Test
  void mixedPath() {
    // when
    String s = HttpPathFeelBuilder.create().part("/example/").property("myProp").build();

    // then
    assertThat(s).isEqualTo("=\"/example/\"+myProp");
  }

  @Test
  void duplicatePropertyName() {
    // when
    String s =
        HttpPathFeelBuilder.create()
            .part("/example/")
            .property("myProp")
            .slash()
            .property("myProp")
            .build();

    // then
    assertThat(s).isEqualTo("=\"/example/\"+myProp+\"/\"+myProp");
  }

  @Test
  void invalidPath() {
    var builder = HttpPathFeelBuilder.create().part("doesNotStartWithASlash");
    assertThrows(IllegalArgumentException.class, builder::build);
  }

  @Test
  void feelOperatorCharacters() {
    var builder = HttpPathFeelBuilder.create();
    var ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> builder.part("/documents/").property("document-id"));
    assertThat(ex.getMessage()).contains(HttpPathFeelBuilder.FEEL_OPERATOR_CHARACTERS);
  }
}
