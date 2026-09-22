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
 * See security-testing-findings#275.
 *
 * <p>A grant is <em>not</em> inherited by a descendant path: an intrinsic-function call's declared
 * path is the exact path its literal occupies once bound — the owning {@code zeebe:input}'s own
 * target, plus however deep that literal is nested inside the input's FEEL object/array literal. A
 * prefix grant would authorize a same-named call arriving from an entirely different,
 * attacker-sourced {@code zeebe:input} whose target merely happens to extend a declared one.
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
   * @param allowed the permitted (functionName, fieldPath) pairs, matched exactly — not by prefix.
   *     An empty list denies everything.
   */
  static IntrinsicFunctionAllowList allowOnly(List<AllowedIntrinsicFunction> allowed) {
    var set = Set.copyOf(allowed);
    return call ->
        set.contains(new AllowedIntrinsicFunction(call.functionName(), call.fieldPath()));
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
