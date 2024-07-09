/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.function.Function;

public enum InvokeModelModel {
  @JsonProperty("Jamba-Instruct")
  JAMBA_INSTRUCT("ai21.jamba-instruct-v1:0", Formatter::AI21LabsJurassicFormatter),
  JURASSIC_2_MID("ai21.j2-mid-v1", Formatter::AI21LabsJurassicFormatter),
  JURASSIC_2_ULTRA("Jurassic-2 Ultra", Formatter::AI21LabsJurassicFormatter),
  ;

  private final String modelId;
  private final Function<String, String> getFormatted;

  InvokeModelModel(String modelId, Function<String, String> getFormatted) {
    this.modelId = modelId;
    this.getFormatted = getFormatted;
  }
}
