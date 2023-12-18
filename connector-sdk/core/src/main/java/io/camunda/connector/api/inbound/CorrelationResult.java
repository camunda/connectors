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

public sealed interface CorrelationResult {

  sealed interface Success extends CorrelationResult {
    record ProcessInstanceCreated(Long processInstanceKey, String tenantId) implements Success {}

    record MessagePublished(Long messageKey, String tenantId) implements Success {}

    record MessageAlreadyCorrelated() implements Success {
      public static final MessageAlreadyCorrelated INSTANCE = new MessageAlreadyCorrelated();
    }
  }

  sealed interface Failure extends CorrelationResult {

    default boolean isRetryable() {
      return false;
    }

    record InvalidInput(String message, Throwable error) implements Failure {}

    record ActivationConditionNotMet() implements Failure {
      public static final ActivationConditionNotMet INSTANCE = new ActivationConditionNotMet();
    }

    record ZeebeClientStatus(String status, String message) implements Failure {
      @Override
      public boolean isRetryable() {
        return true;
      }
    }

    record Other(Throwable error) implements Failure {
      @Override
      public boolean isRetryable() {
        return true;
      }
    }
  }
}
