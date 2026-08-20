#!/usr/bin/env groovy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

def sourceFile = sourceFile
if (!sourceFile) {
    System.err.println("Error: Source file path required as property")
    System.exit(1)
}

def outputFile = outputFile
if (!outputFile) {
    System.err.println("Error: Output file path required as property")
    System.exit(1)
}

// optional: agentType for the zeebe:agentDefinition marker; unset means no marker is added
def agentType = binding.hasVariable('agentType') ? agentType : null

def file = new File((String) sourceFile)
if (!file.exists()) {
    System.err.println("Error: Source file ${sourceFile} not found")
    System.exit(1)
}

def mapper = new ObjectMapper()
mapper.enable(SerializationFeature.INDENT_OUTPUT)

def json = mapper.readValue(file, Map.class)

def updatedProperties = []

((List) json.get('properties')).each { property ->
    updatedProperties.add(property)

    // Mark the element as a native agent definition so the engine creates an agent-definition
    // record at deploy time.
    if (agentType && property.binding?.type == "zeebe:taskDefinition" && property.binding?.property == "type") {
        updatedProperties.add([
            value: (String) agentType,
            binding: [
                type: "zeebe:agentDefinition",
                property: "agentType"
            ],
            type: "Hidden"
        ])
    }
}

json.put('properties', updatedProperties)
mapper.writeValue(new File((String) outputFile), json)
