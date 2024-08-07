package io.camunda.connector.generator.java.util;

import io.camunda.connector.generator.dsl.Property;

public record DocsProperty(
    String label, String type, String description, Object exampleValue, boolean required,
    Property property
) {}
