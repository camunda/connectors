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

import io.camunda.document.factory.DocumentFactory;
import java.util.Map;

/**
 * The context object provided to an inbound connector function. The context allows to fetch
 * information injected by the environment runtime.
 */
public interface InboundConnectorContext extends DocumentFactory {

  /**
   * Checks if the Connector can be activated. The Connector can be activated if the activation
   * condition is met or the Connector is not configured with an activation condition. This method
   * can be used when you want to perform an action only if the Connector is activable (creating
   * documents, marking an email as read, etc.).
   *
   * @param variables - an object containing inbound connector variables
   * @return ActivationCheckResult that should be interpreted by the Connector implementation
   */
  ActivationCheckResult canActivate(Object variables);

  /**
   * Correlates the inbound event to the matching process definition and returns the result.
   *
   * <p>Correlation may not succeed due to Connector configuration (e.g. if activation condition
   * specified by user is not met). In this case, the response will contain the corresponding error
   * code.
   *
   * <p>This method does not throw any exceptions. If correlation fails, the error is returned as a
   * part of the response. The connector implementation should handle the error according to the
   * {@link CorrelationFailureHandlingStrategy} provided in the response. If the strategy is {@link
   * CorrelationFailureHandlingStrategy.ForwardErrorToUpstream}, the error should be forwarded to
   * the upstream system. If the strategy is {@link CorrelationFailureHandlingStrategy.Ignore}, the
   * error should be ignored.
   *
   * @param variables - an object containing inbound connector variables
   * @return correlation result that should be interpreted by the Connector implementation
   * @see CorrelationResult
   * @see CorrelationFailureHandlingStrategy
   */
  @Deprecated
  CorrelationResult correlateWithResult(Object variables);

  /**
   * Correlates the inbound event to the matching process definition using the provided correlation
   * request and returns the result.
   *
   * <p>Correlation may not succeed due to Connector configuration (e.g. if activation condition
   * specified by user is not met). In this case, the response will contain the corresponding error
   * code.
   *
   * <p>This method does not throw any exceptions. If correlation fails, the error is returned as a
   * part of the response. The connector implementation should handle the error according to the
   * {@link CorrelationFailureHandlingStrategy} provided in the response. If the strategy is {@link
   * CorrelationFailureHandlingStrategy.ForwardErrorToUpstream}, the error should be forwarded to
   * the upstream system. If the strategy is {@link CorrelationFailureHandlingStrategy.Ignore}, the
   * error should be ignored.
   *
   * @param correlationRequest - an object containing the inbound connector variables and message ID
   * @return correlation result that should be interpreted by the Connector implementation
   * @see CorrelationResult
   * @see CorrelationFailureHandlingStrategy
   * @see CorrelationRequest
   */
  CorrelationResult correlate(CorrelationRequest correlationRequest);

  /**
   * /** Signals to the Connector runtime that inbound Connector execution was interrupted. As a
   * result of this call, the runtime may attempt to retry the execution or provide the user with an
   * appropriate alert.
   */
  void cancel(Throwable exception);

  /**
   * Low-level properties access method. Allows to perform custom deserialization. For a simpler
   * property access, consider using {@link #bindProperties(Class)}.
   *
   * <p>Note: this method doesn't perform validation or FEEl expression evaluation. Secret
   * replacement is performed using the {@link io.camunda.connector.api.secret.SecretProvider}
   * implementation available in the Connector runtime.
   *
   * @return raw properties as a map with secrets replaced
   */
  Map<String, Object> getProperties();

  /**
   * High-level properties access method. Allows to deserialize properties into a given type.
   *
   * <p>Additionally, this method takes care of secret replacement, properties validation, and FEEL
   * expression evaluation.
   *
   * <p>Secret values are substituted using the {@link
   * io.camunda.connector.api.secret.SecretProvider} implementations available in the Connector
   * runtime.
   *
   * <p>Properties validation is performed using the {@link
   * io.camunda.connector.api.validation.ValidationProvider} implementation available in the
   * Connector runtime.
   *
   * <p>FEEL expressions in properties are evaluated as encountered.
   *
   * @param cls a class to deserialize properties into
   * @param <T> a type to deserialize properties into
   * @return deserialized and validated properties with secrets replaced
   */
  <T> T bindProperties(Class<T> cls);

  /**
   * Provides an object that references the process definition that the inbound Connector is
   * configured for. The object can be used to access the process definition metadata.
   *
   * @return definition of the inbound Connector
   */
  InboundConnectorDefinition getDefinition();

  /**
   * Report the health to allow other components to process the current status of the Connector. The
   * data can be used to report data on liveliness and whether the Connector is running
   * successfully.
   *
   * <p>This method can be called as often as needed and the internal state of the inbound Connector
   * implementation requires it.
   */
  void reportHealth(Health health);

  /**
   * Report an activity to allow other components to access the activity log of the Connector. The
   * data can be used to report data about processed requests or failures.
   *
   * <p>This method can be called as often as needed and the internal state of the inbound Connector
   * implementation requires it.
   *
   * <p>Note: this method will not trigger application ERROR logs no matter what severity is
   * supplied.
   */
  void log(Activity activity);

  /**
   * Shortcut method to create an {@link ActivityBuilder} and log the activity.
   *
   * @see InboundConnectorContext#log(Activity)
   */
  default void log(ActivityBuilder activityBuilder) {
    log(activityBuilder.build());
  }
}
