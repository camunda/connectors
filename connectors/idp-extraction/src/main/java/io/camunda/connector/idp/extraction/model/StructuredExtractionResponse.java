package io.camunda.connector.idp.extraction.model;

import java.util.Map;

public record StructuredExtractionResponse(Map<String, String> extractedFields, Map<String, Float> confidenceScore) {}
