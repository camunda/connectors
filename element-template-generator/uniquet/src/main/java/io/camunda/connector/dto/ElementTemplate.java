package io.camunda.connector.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ElementTemplate(
    @JsonProperty(required = true) String id, @JsonProperty(required = true) Integer version) {}
