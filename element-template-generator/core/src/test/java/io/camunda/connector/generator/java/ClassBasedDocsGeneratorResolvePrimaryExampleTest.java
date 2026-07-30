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
package io.camunda.connector.generator.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.java.annotation.DataExample;
import org.junit.jupiter.api.Test;

public class ClassBasedDocsGeneratorResolvePrimaryExampleTest {

  public static class NoExamples {}

  public static class SingleDefaultExample {
    @DataExample
    public static String example() {
      return "single";
    }
  }

  public static class OnlyExplicitIds {
    @DataExample(id = "first")
    public static String first() {
      return "first-value";
    }

    @DataExample(id = "second")
    public static String second() {
      return "second-value";
    }
  }

  public static class DefaultAmongExplicitIds {
    @DataExample(id = "explicit")
    public static String explicit() {
      return "explicit-value";
    }

    @DataExample
    public static String canonical() {
      return "canonical-value";
    }
  }

  @Test
  void noExamples_returnsEmpty() {
    assertThat(ClassBasedDocsGenerator.resolvePrimaryExampleData(NoExamples.class)).isEmpty();
  }

  @Test
  void singleDefaultExample_isPicked() {
    var result = ClassBasedDocsGenerator.resolvePrimaryExampleData(SingleDefaultExample.class);
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(DataExample.DEFAULT_ID);
  }

  @Test
  void noDefaultIdExample_fallsBackToFirstDeclared() {
    var result = ClassBasedDocsGenerator.resolvePrimaryExampleData(OnlyExplicitIds.class);
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo("first");
  }

  @Test
  void defaultIdExample_preferredOverExplicitIds() {
    var result = ClassBasedDocsGenerator.resolvePrimaryExampleData(DefaultAmongExplicitIds.class);
    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo(DataExample.DEFAULT_ID);
  }
}
