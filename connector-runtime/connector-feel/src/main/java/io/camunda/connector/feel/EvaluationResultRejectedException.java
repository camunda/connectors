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
package io.camunda.connector.feel;

/**
 * Thrown by an {@link EvaluationResultProcessor} to reject an evaluation result outright.
 *
 * <p>This is a deliberate decision about the content of a result, not a failure to evaluate, so the
 * evaluator lets it through unchanged instead of wrapping it in a {@link
 * FeelEngineWrapperException}. Wrapping it would hide the reason from the caller, which for a
 * rejection is the only thing that makes it actionable.
 */
public class EvaluationResultRejectedException extends RuntimeException {

  public EvaluationResultRejectedException(String message) {
    // No cause and no stack trace: this carries a decision, not a fault, and is caught by the
    // caller that asked for it.
    super(message, null, false, false);
  }
}
