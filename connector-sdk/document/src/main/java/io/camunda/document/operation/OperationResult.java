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

public sealed interface OperationResult<T> {

  record Success<T>(T result) implements OperationResult<T> {}

  sealed interface Failure<T> extends OperationResult<T> {
    String errorMessage();

    Throwable cause();

    record ValidationFailure<T>(String errorMessage) implements Failure<T> {
      @Override
      public Throwable cause() {
        return null;
      }
    }

    record ExecutionFailure<T>(String errorMessage, Throwable cause) implements Failure<T> {

      public ExecutionFailure(String errorMessage) {
        this(errorMessage, null);
      }

      public ExecutionFailure(Throwable cause) {
        this(null, cause);
      }
    }
  }

  default boolean isSuccess() {
    return this instanceof Success;
  }

  default boolean isFailure() {
    return this instanceof Failure;
  }
}
