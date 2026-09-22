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
package io.camunda.intrinsic;

/**
 * An {@link IntrinsicFunctionExecutor} for mappers that bind data that nothing marks as coming from
 * trusted model text — a FEEL evaluation result, or a job/element property reached through an
 * arbitrary ioMapping expression. By the time such a mapper sees a {@code camunda.function.type}
 * node, it cannot tell a value a process author wrote in the model from one that arrived as
 * payload/process data, so it must not dispatch either — it throws instead.
 */
public class DisabledIntrinsicFunctionExecutor implements IntrinsicFunctionExecutor {

  @Override
  public Object execute(String operationName, IntrinsicFunctionParams params) {
    throw new UnsupportedOperationException(
        "Intrinsic function dispatch is disabled for this mapper; refusing to execute '%s'"
            .formatted(operationName));
  }
}
