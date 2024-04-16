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

import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.Property.FeelMode;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeSubscriptionProperty;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyCondition.IsActive;
import io.camunda.connector.generator.dsl.PropertyConstraints.Pattern;
import java.util.List;

public class CommonProperties {

  public static PropertyBuilder resultExpression() {
    return TextProperty.builder()
        .id("resultExpression")
        .group("output")
        .label("Result expression")
        .description("Expression to map the response into process variables")
        .feel(FeelMode.required);
  }

  public static PropertyBuilder resultVariable() {
    return StringProperty.builder()
        .id("resultVariable")
        .group("output")
        .label("Result variable")
        .description("Name of variable to store the response in")
        .feel(FeelMode.disabled);
  }

  public static PropertyBuilder errorExpression() {
    return TextProperty.builder()
        .id("errorExpression")
        .label("Error expression")
        .group("error")
        .description(
            "Expression to handle errors. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/use-connectors/\" target=\"_blank\">documentation</a>.")
        .feel(FeelMode.required);
  }

  public static PropertyBuilder retryCount() {
    return StringProperty.builder()
        .id("retryCount")
        .label("Retries")
        .description("Number of retries")
        .group("retries")
        .value("3");
  }

  public static PropertyBuilder retryBackoff() {
    return StringProperty.builder()
        .id("retryBackoff")
        .label("Retry backoff")
        .description("ISO-8601 duration to wait between retries")
        .group("retries")
        .value("PT0S");
  }

  public static PropertyBuilder activationCondition() {
    return StringProperty.builder()
        .id("activationCondition")
        .label("Activation condition")
        .description(
            "Condition under which the Connector triggers. Leave empty to catch all events")
        .group("activation")
        .feel(FeelMode.required)
        .optional(true)
        .binding(new ZeebeProperty("activationCondition"));
  }

  public static PropertyBuilder correlationKeyProcess() {
    return StringProperty.builder()
        .id("correlationKeyProcess")
        .label("Correlation key (process)")
        .description("Sets up the correlation key from process variables")
        .group("correlation")
        .feel(FeelMode.required)
        .binding(ZeebeSubscriptionProperty.CORRELATION_KEY)
        .constraints(PropertyConstraints.builder().notEmpty(true).build());
  }

  public static PropertyBuilder correlationKeyPayload() {
    return StringProperty.builder()
        .id("correlationKeyPayload")
        .label("Correlation key (payload)")
        .description("Extracts the correlation key from the incoming message payload")
        .group("correlation")
        .feel(FeelMode.required)
        .binding(new PropertyBinding.ZeebeProperty("correlationKeyExpression"))
        .constraints(PropertyConstraints.builder().notEmpty(true).build());
  }

  public static PropertyBuilder messageIdExpression() {
    return StringProperty.builder()
        .id("messageIdExpression")
        .label("Message ID expression")
        .description("Expression to extract unique identifier of a message")
        .group("correlation")
        .feel(FeelMode.required)
        .optional(true)
        .binding(new PropertyBinding.ZeebeProperty("messageIdExpression"));
  }

  public static PropertyBuilder messageNameUuidHidden() {
    return HiddenProperty.builder()
        .id("messageNameUuid")
        .group("correlation")
        .generatedValue()
        .binding(PropertyBinding.MessageProperty.NAME);
  }

  public static PropertyBuilder correlationRequiredDropdown() {
    return DropdownProperty.builder()
        .choices(
            List.of(
                new DropdownChoice("Correlation not required", "notRequired"),
                new DropdownChoice("Correlation required", "required")))
        .id("correlationRequired")
        .label("Subprocess correlation required")
        .description(
            "Indicates whether correlation is required. This is needed for event-based subprocess message start events")
        .group("correlation")
        .value("notRequired")
        .binding(new ZeebeProperty("correlationRequired"));
  }

  public static PropertyBuilder deduplicationModeManualFlag() {
    return BooleanProperty.builder()
        .id("deduplicationModeManualFlag")
        .label("Manual mode")
        .group("deduplication")
        .description(
            "By default, similar connectors receive the same deduplication ID. Customize by activating manual mode")
        .value(false)
        .binding(new ZeebeProperty("deduplicationModeManualFlag"));
  }

  public static PropertyBuilder deduplicationId() {
    return StringProperty.builder()
        .id("deduplicationId")
        .label("Deduplication ID")
        .group("deduplication")
        .feel(FeelMode.disabled)
        .binding(new ZeebeProperty("deduplicationId"))
        .constraints(
            PropertyConstraints.builder()
                .notEmpty(true)
                .pattern(
                    new Pattern(
                        "^[a-zA-Z0-9_-]+$",
                        "Only alphanumeric characters, dashes, and underscores are allowed"))
                .build())
        .condition(new Equals("deduplicationModeManualFlag", true));
  }

  public static PropertyBuilder deduplicationModeManual() {
    return HiddenProperty.builder()
        .id("deduplicationModeManual")
        .group("deduplication")
        .value("MANUAL")
        .condition(new IsActive("deduplicationId", true))
        .binding(new ZeebeProperty("deduplicationMode"));
  }

  public static PropertyBuilder deduplicationModeAuto() {
    return HiddenProperty.builder()
        .id("deduplicationModeAuto")
        .group("deduplication")
        .value("AUTO")
        .condition(new IsActive("deduplicationId", false))
        .binding(new ZeebeProperty("deduplicationMode"));
  }
}
