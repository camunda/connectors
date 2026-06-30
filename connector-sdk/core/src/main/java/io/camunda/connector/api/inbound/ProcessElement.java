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
package io.camunda.connector.api.inbound;

import java.util.Map;

public interface ProcessElement {

  String bpmnProcessId();

  default String processName() {
    return null;
  }

  int version();

  long processDefinitionKey();

  String elementId();

  String elementName();

  String elementType();

  String tenantId();

  /**
   * Raw properties of this element as defined in the process model. FEEL expressions are not
   * evaluated and secret placeholders are not resolved.
   *
   * <p>This allows the runtime to resolve element-scoped properties (for example, a webhook
   * response expression) from the specific activated element even when several elements were
   * deduplicated into a single connector executable.
   */
  Map<String, String> properties();

  /**
   * Binds this element's raw properties to a typed object using the runtime's secret-replacement
   * and FEEL-evaluation pipeline.
   *
   * <p>Use this to resolve element-scoped properties (for example, a webhook response expression)
   * from the specific element that matched a request, even when several elements were deduplicated
   * into a single connector executable. Only elements supplied by the runtime support binding; the
   * default implementation throws.
   *
   * <p><b>Warning:</b> this is typically invoked after correlation has already taken effect (past
   * the transaction boundary) — the process instance was created or the message was published. A
   * failure while binding or evaluating these properties cannot undo that, so callers must handle
   * exceptions carefully and must not report the event as unprocessed.
   */
  default <T> T bindProperties(Class<T> cls) {
    throw new UnsupportedOperationException(
        "This process element does not support property binding");
  }
}
