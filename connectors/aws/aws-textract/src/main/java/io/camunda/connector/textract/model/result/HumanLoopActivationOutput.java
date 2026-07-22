/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.textract.model.result;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/** Connector-owned mirror of the AWS SDK v2 {@code HumanLoopActivationOutput} shape. */
@JsonPropertyOrder({
  "humanLoopArn",
  "humanLoopActivationReasons",
  "humanLoopActivationConditionsEvaluationResults"
})
public record HumanLoopActivationOutput(
    String humanLoopArn,
    List<String> humanLoopActivationReasons,
    String humanLoopActivationConditionsEvaluationResults) {

  public static HumanLoopActivationOutput from(
      final software.amazon.awssdk.services.textract.model.HumanLoopActivationOutput output) {
    if (output == null) {
      return null;
    }
    return new HumanLoopActivationOutput(
        output.humanLoopArn(),
        output.hasHumanLoopActivationReasons() ? output.humanLoopActivationReasons() : null,
        output.humanLoopActivationConditionsEvaluationResults());
  }
}
