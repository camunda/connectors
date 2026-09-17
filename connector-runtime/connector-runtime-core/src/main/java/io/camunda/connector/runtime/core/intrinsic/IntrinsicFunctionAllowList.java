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
package io.camunda.connector.runtime.core.intrinsic;

import java.util.List;
import java.util.Set;

/**
 * Determines whether a {@code camunda.function.type} call at a given field path may be dispatched.
 * Mirrors {@code io.camunda.connector.runtime.core.secret.SecretFilter}'s shape and reasoning: only
 * a call the deployed BPMN model literally declares at that exact field (or a path beneath it) may
 * run; anything else arrived as data, not model text. See security-testing-findings#275.
 */
@FunctionalInterface
public interface IntrinsicFunctionAllowList {

  static IntrinsicFunctionAllowList allowAll() {
    return call -> true;
  }

  static IntrinsicFunctionAllowList allowNone() {
    return call -> false;
  }

  /**
   * @param allowed the permitted (functionName, fieldPath) pairs. An empty list denies everything.
   */
  static IntrinsicFunctionAllowList allowOnly(List<AllowedIntrinsicFunction> allowed) {
    var set = Set.copyOf(allowed);
    return call ->
        set.stream()
            .filter(a -> a.functionName().equals(call.functionName()))
            .filter(a -> call.fieldPath().size() >= a.fieldPath().size())
            .anyMatch(a -> call.fieldPath().subList(0, a.fieldPath().size()).equals(a.fieldPath()));
  }

  boolean isAllowed(Call call);

  /** A single {@code camunda.function.type} occurrence found while binding job variables. */
  record Call(String functionName, List<String> fieldPath) {

    public Call(String functionName, List<String> fieldPath) {
      this.functionName = functionName;
      this.fieldPath = List.copyOf(fieldPath);
    }
  }
}
