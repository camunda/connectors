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
package io.camunda.connector.generator.dsl;

import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeSubscriptionProperty;

public class CommonProperties {

  public static final PropertyBuilder RESULT_EXPRESSION =
      TextProperty.builder()
          .id("resultExpression")
          .group("output")
          .label("Result expression")
          .description("Expression to map the response into process variables")
          .feel(FeelMode.required);

  public static final PropertyBuilder RESULT_VARIABLE =
      StringProperty.builder()
          .id("resultVariable")
          .group("output")
          .label("Result variable")
          .description("Name of variable to store the response in")
          .feel(FeelMode.disabled);

  public static final PropertyBuilder ERROR_EXPRESSION =
      TextProperty.builder()
          .id("errorExpression")
          .label("Error expression")
          .group("error")
          .description(
              "Expression to handle errors. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/use-connectors/\" target=\"_blank\">documentation</a>.")
          .feel(FeelMode.required);

  public static final PropertyBuilder RETRY_COUNT =
      StringProperty.builder()
          .id("retryCount")
          .label("Retries")
          .description("Number of retries")
          .group("retries")
          .value("3");

  public static final PropertyBuilder RETRY_BACKOFF =
      StringProperty.builder()
          .id("retryBackoff")
          .label("Retry backoff")
          .description("ISO-8601 duration to wait between retries")
          .group("retries")
          .value("PT0S");

  public static final PropertyBuilder ACTIVATION_CONDITION =
      StringProperty.builder()
          .id("activationCondition")
          .label("Activation condition")
          .description(
              "Condition under which the Connector triggers. Leave empty to catch all events")
          .group("activation")
          .feel(FeelMode.required)
          .optional(true);

  public static final PropertyBuilder CORRELATION_KEY_PROCESS =
      StringProperty.builder()
          .id("correlationKeyProcess")
          .label("Correlation key (process)")
          .description("Sets up the correlation key from process variables")
          .group("activation")
          .feel(FeelMode.required)
          .binding(ZeebeSubscriptionProperty.CORRELATION_KEY)
          .constraints(PropertyConstraints.builder().notEmpty(true).build());

  public static final PropertyBuilder CORRELATION_KEY_PAYLOAD =
      StringProperty.builder()
          .id("correlationKeyPayload")
          .label("Correlation key (payload)")
          .description("Extracts the correlation key from the incoming message payload")
          .group("activation")
          .feel(FeelMode.required)
          .binding(new PropertyBinding.ZeebeProperty("correlationKeyExpression"))
          .constraints(PropertyConstraints.builder().notEmpty(true).build());

  public static final PropertyBuilder MESSAGE_ID_EXPRESSION =
      StringProperty.builder()
          .id("messageIdExpression")
          .label("Message ID expression")
          .description("Expression to extract unique identifier of a message")
          .group("activation")
          .feel(FeelMode.required)
          .optional(true)
          .binding(new PropertyBinding.ZeebeProperty("messageIdExpression"));

  public static final PropertyBuilder MESSAGE_NAME_UUID =
      HiddenProperty.builder()
          .id("messageNameUuid")
          .group("activation")
          .generatedValue()
          .binding(PropertyBinding.MessageProperty.NAME);
}
