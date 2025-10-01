#!/usr/bin/env groovy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

def src = sourceFile
if (!src) {
    System.err.println("Error: Source file path required as property")
    System.exit(1)
}

def out = outputFile
if (!out) {
    System.err.println("Error: Output file path required as property")
    System.exit(1)
}

def source = new File((String) src)
if (!source.exists()) {
    System.err.println("Error: Source file ${src} not found")
    System.exit(1)
}

def output = new File((String) out)

def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

def json = mapper.readValue(source, Map)

def targetBindingName = 'io.camunda.agenticai.gateway.type'

json.properties?.each { property ->
    if (property.binding?.type == 'zeebe:property' && property.binding?.name == targetBindingName) {
        if (!property.containsKey('condition')) {
            property.condition = [
                property: 'data.connectorModeConfiguration.type',
                equals  : 'aiAgentTool',
                type    : 'simple'
            ]
        }
        return
    }
}

// Ensure output directory exists
output.parentFile?.mkdirs()

mapper.writeValue(output, json)

