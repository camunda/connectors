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
package io.camunda.document.operation;

import io.camunda.document.Document;

/** Represents a parameter of an operation. */
public sealed interface OperationParameter {

  /** Represents a document parameter that is passed to an operation. */
  record DocumentParameter(Document document) implements OperationParameter {}

  /**
   * Represents a value parameter that is passed to an operation. A value parameter is any value
   * other than a document.
   */
  record ValueParameter(Object object) implements OperationParameter {}

  default boolean isDocumentParameter() {
    return this instanceof DocumentParameter;
  }

  default boolean isValueParameter() {
    return this instanceof ValueParameter;
  }

  default Document asDocumentParameter() {
    return ((DocumentParameter) this).document;
  }

  default Object asValueParameter() {
    return ((ValueParameter) this).object;
  }
}
