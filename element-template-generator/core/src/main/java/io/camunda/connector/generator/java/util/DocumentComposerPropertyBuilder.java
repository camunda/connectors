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

/**
 * Builder for the hidden document composer property, whose FEEL expression references the helper
 * sub-fields generated alongside it.
 *
 * <p>Those helpers are bound with input mappings, and a mapping target containing a dot creates a
 * <em>nested</em> local variable: target {@code input.doc_documentSource} produces {@code input:
 * {doc_documentSource: ...}}, which a later expression can only read as {@code
 * input.doc_documentSource} — the bare leaf name resolves to null. Since the nesting path is only
 * known once the enclosing record prefixes this property, the expression is regenerated whenever a
 * prefix is applied rather than baked in at construction time.
 */
final class DocumentComposerPropertyBuilder extends HiddenProperty.HiddenPropertyBuilder {

  /** Renders the FEEL expression, given a function that qualifies a helper's leaf name. */
  private final Function<UnaryOperator<String>, String> expressionRenderer;

  private String helperPath;

  /**
   * @param helperPath nesting the helpers already carry from the annotation's own binding path
   *     (e.g. {@code action} for a binding of {@code action.document}); empty when they sit at the
   *     root
   */
  DocumentComposerPropertyBuilder(
      String helperPath, Function<UnaryOperator<String>, String> expressionRenderer) {
    this.helperPath = helperPath;
    this.expressionRenderer = expressionRenderer;
    render();
  }

  /**
   * Applies one more level of nesting, outermost last, so {@code b} then {@code a} yields helper
   * references qualified with {@code a.b}.
   */
  void addHelperPathPrefix(String path) {
    helperPath = helperPath.isEmpty() ? path : path + "." + helperPath;
    render();
  }

  private void render() {
    UnaryOperator<String> qualify = leaf -> helperPath.isEmpty() ? leaf : helperPath + "." + leaf;
    value("=" + expressionRenderer.apply(qualify));
  }
}
