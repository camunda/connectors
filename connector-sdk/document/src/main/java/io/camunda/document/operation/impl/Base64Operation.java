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
package io.camunda.document.operation.impl;

import io.camunda.document.operation.IntrinsicOperation;
import io.camunda.document.operation.IntrinsicOperationParameter;
import io.camunda.document.operation.IntrinsicOperationResult;
import io.camunda.document.operation.IntrinsicOperationResult.Failure.ExecutionFailure;
import io.camunda.document.operation.IntrinsicOperationResult.Failure.ValidationFailure;
import io.camunda.document.operation.IntrinsicOperationResult.Success;
import java.util.List;

public class Base64Operation implements IntrinsicOperation<String> {

  @Override
  public IntrinsicOperationResult<String> execute(List<? extends IntrinsicOperationParameter> arguments) {
    if (arguments.size() != 1) {
      return new ValidationFailure<>("Base64 operation expects a single document as argument");
    }
    final var maybeDocument = arguments.get(0);
    if (maybeDocument == null || !maybeDocument.isDocumentParameter()) {
      return new ValidationFailure<>("Base64 operation expects a single document as argument");
    }

    try {
      final var base64 = maybeDocument.asDocumentParameter().asBase64();
      return new Success<>(base64);
    } catch (Exception e) {
      return new ExecutionFailure<>("Failed to read document as base64", e);
    }
  }
}
