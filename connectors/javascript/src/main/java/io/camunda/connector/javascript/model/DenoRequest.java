package io.camunda.connector.javascript.model;


import java.util.List;

public record DenoRequest(String code, List<Object> parameters) { }
