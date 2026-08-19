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
package io.camunda.connector.runtime.core.secret;

import io.camunda.client.api.response.SecretReference;
import io.camunda.connector.feel.EvaluationResultProcessor;
import java.util.List;
import java.util.Map;

/**
 * Rejects an evaluation result that still carries the legacy secret syntax, before any secret value
 * is substituted into it.
 *
 * <p>Order matters. Checking after substitution would scan resolved secret material, so a secret
 * whose value happened to contain {@code secrets.} text would be reported as a configuration using
 * unsupported syntax — a wrong diagnostic derived from reading a plaintext secret. Running first
 * means the check only ever sees what the cluster returned.
 */
public class LegacySecretSyntaxRejectingProcessor implements EvaluationResultProcessor {

  private final EvaluationResultProcessor delegate;

  public LegacySecretSyntaxRejectingProcessor(EvaluationResultProcessor delegate) {
    this.delegate = delegate;
  }

  @Override
  public Object process(Object result, List<SecretReference> referencedSecrets) {
    reject(result);
    return delegate.process(result, referencedSecrets);
  }

  private static void reject(Object node) {
    switch (node) {
      case null -> {}
      case String text -> {
        if (SecretUtil.containsLegacySecretReference(text)) {
          throw new LegacySecretSyntaxException();
        }
      }
      case Map<?, ?> map -> map.values().forEach(LegacySecretSyntaxRejectingProcessor::reject);
      case List<?> list -> list.forEach(LegacySecretSyntaxRejectingProcessor::reject);
      default -> {}
    }
  }

  /** Signals that a resolved value still carries the unsupported legacy secret syntax. */
  public static final class LegacySecretSyntaxException extends RuntimeException {
    public LegacySecretSyntaxException() {
      super(null, null, false, false);
    }
  }
}
