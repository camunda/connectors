package io.camunda.connector.inbound.utils;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class ObjectMapperSupplier {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private ObjectMapperSupplier(){}
    
    public static ObjectMapper getMapperInstance() {
        return MAPPER;
    }
    
}
