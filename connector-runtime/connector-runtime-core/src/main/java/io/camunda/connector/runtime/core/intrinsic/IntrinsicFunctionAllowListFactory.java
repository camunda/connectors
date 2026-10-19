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

import java.time.Instant;

public interface IntrinsicFunctionAllowListFactory {

  IntrinsicFunctionAllowList create(IntrinsicFunctionAllowListContext context);

  /**
   * A placeholder for callers with no allow-list of their own (e.g. a test exercising unrelated
   * behavior) that never dispatch a {@code camunda.function.type} call in the first place. Refuses
   * every call rather than permitting them — unlike {@code SecretFilterFactory.disabled()}'s {@code
   * allowAll()} (over-permissive secret resolution is a lower-severity leak; unconditional
   * intrinsic-function dispatch is security-testing-findings#275's actual RCE-class primitive), so
   * this can never become a silent fail-open if a future caller reaches for it expecting a safe,
   * no-op default.
   */
  static IntrinsicFunctionAllowListFactory disabled() {
    return context -> IntrinsicFunctionAllowList.allowNone();
  }

  record IntrinsicFunctionAllowListContext(
      long processDefinitionKey, String elementId, Instant deadline) {}
}
