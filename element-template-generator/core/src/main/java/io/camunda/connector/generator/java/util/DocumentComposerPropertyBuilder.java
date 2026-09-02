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
package io.camunda.connector.generator.java.util;

import io.camunda.connector.generator.dsl.HiddenProperty;
import java.util.function.Function;
import java.util.function.UnaryOperator;

// A nested Zeebe input target is a separate variable from its bare leaf name, so this re-renders.
final class DocumentComposerPropertyBuilder extends HiddenProperty.HiddenPropertyBuilder {

  /** Renders the FEEL expression, given a function that qualifies a helper's leaf name. */
  private final Function<UnaryOperator<String>, String> expressionRenderer;

  // Nesting the helpers already carry from the annotation's own binding path; empty at the root.
  private String helperPath;

  DocumentComposerPropertyBuilder(
      String helperPath, Function<UnaryOperator<String>, String> expressionRenderer) {
    this.helperPath = helperPath;
    this.expressionRenderer = expressionRenderer;
    render();
  }

  // Applies one more level of nesting, outermost last, so `b` then `a` yields `a.b`.
  void addHelperPathPrefix(String path) {
    helperPath = helperPath.isEmpty() ? path : path + "." + helperPath;
    render();
  }

  private void render() {
    UnaryOperator<String> qualify = leaf -> helperPath.isEmpty() ? leaf : helperPath + "." + leaf;
    value("=" + expressionRenderer.apply(qualify));
  }
}
