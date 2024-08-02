package io.camunda.connector.api.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record DocumentOperation(
    @JsonProperty("$name")
    String name,
    @JsonProperty("$params")
    Map<String, Object> params
) {}
