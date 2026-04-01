/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.model.abbyy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AbbyyTransactionResponse(
    @JsonProperty("id") String id,
    @JsonProperty("status") String status,
    @JsonProperty("documents") List<AbbyyDocument> documents,
    @JsonProperty("sourceFiles") List<AbbyySourceFile> sourceFiles) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AbbyyDocument(
      @JsonProperty("id") String id,
      @JsonProperty("resultFiles") List<AbbyyResultFile> resultFiles,
      @JsonProperty("classification") AbbyyClassification classification,
      @JsonProperty("businessRulesErrors") List<Object> businessRulesErrors) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AbbyyResultFile(
      @JsonProperty("fileId") String fileId, @JsonProperty("type") String type) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AbbyySourceFile(@JsonProperty("id") String id, @JsonProperty("name") String name) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AbbyyClassification(
      @JsonProperty("isResultClassConfident") boolean isResultClassConfident,
      @JsonProperty("resultClass") String resultClass,
      @JsonProperty("classConfidences") List<AbbyyClassConfidence> classConfidences) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AbbyyClassConfidence(
      @JsonProperty("class") String className, @JsonProperty("confidence") int confidence) {}
}
