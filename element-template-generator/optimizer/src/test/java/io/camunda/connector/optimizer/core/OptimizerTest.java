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
package io.camunda.connector.optimizer.core;

import static io.camunda.connector.optimizer.TestTemplates.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.optimizer.pass.MergeByIdentityPass;
import io.camunda.connector.optimizer.pass.ReorderPass;
import io.camunda.connector.optimizer.pass.TotalizePass;
import java.util.List;
import org.junit.jupiter.api.Test;

class OptimizerTest {

  @Test
  void defaultPipelineCollapsesMultiOperationTemplate() {
    // Three identical hidden properties, each conditioned on a different operation. After merge
    // the three collapse into one oneOf; after totalize the oneOf covers every choice so the
    // condition is dropped entirely.
    ElementTemplate input =
        template(
            dropdownProperty(
                "operationId", "search", choice("search"), choice("feed"), choice("autocomplete")),
            hiddenProperty(
                "search_locale",
                "en-US",
                zeebeInput("locale"),
                equalsCondition("operationId", "search")),
            hiddenProperty(
                "feed_locale",
                "en-US",
                zeebeInput("locale"),
                equalsCondition("operationId", "feed")),
            hiddenProperty(
                "autocomplete_locale",
                "en-US",
                zeebeInput("locale"),
                equalsCondition("operationId", "autocomplete")));

    ElementTemplate optimized = Optimizer.defaultPipeline().optimize(input);

    // operationId + one merged & totalized locale property
    assertThat(optimized.properties()).hasSize(2);
    var locale =
        optimized.properties().stream()
            .filter(p -> !"operationId".equals(p.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(locale.getCondition()).isNull();
  }

  @Test
  void defaultPipelineExceptSkipsRequestedPasses() {
    ElementTemplate input =
        template(
            dropdownProperty("op", "a", choice("a"), choice("b")),
            hiddenProperty("a_x", "v", zeebeInput("x"), equalsCondition("op", "a")),
            hiddenProperty("b_x", "v", zeebeInput("x"), equalsCondition("op", "b")));

    // Skip totalize → merge should still happen, but the condition should remain.
    ElementTemplate optimized =
        Optimizer.defaultPipelineExcept(List.of(TotalizePass.ID)).optimize(input);

    var merged =
        optimized.properties().stream()
            .filter(p -> !"op".equals(p.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(merged.getCondition()).isNotNull();
  }

  @Test
  void defaultPassesEnumeratesEveryPassInOrder() {
    assertThat(Optimizer.defaultPasses().keySet())
        .containsExactly("merge-by-identity", "totalize", "strength-reduce", "reorder");
  }

  @Test
  void skippingEveryPassYieldsEmptyPipeline() {
    var optimizer =
        Optimizer.defaultPipelineExcept(
            List.of(MergeByIdentityPass.ID, TotalizePass.ID, "strength-reduce", ReorderPass.ID));
    assertThat(optimizer.passes()).isEmpty();
  }

  @Test
  void defaultPipelineExceptRejectsUnknownPassIds() {
    assertThatThrownBy(
            () -> Optimizer.defaultPipelineExcept(List.of("merg-by-identity", "totalize")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("merg-by-identity")
        .hasMessageContaining("merge-by-identity"); // the known list is listed for the user
  }
}
