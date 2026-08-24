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

// optional: mark the template as deprecated; unset means no "deprecated" block is added
def deprecationMessage = binding.hasVariable('deprecationMessage') ? deprecationMessage : null
def deprecationDocumentationRef = binding.hasVariable('deprecationDocumentationRef') ? deprecationDocumentationRef : null

def file = new File((String) sourceFile)
if (!file.exists()) {
    System.err.println("Error: Source file ${sourceFile} not found")
    System.exit(1)
}

def mapper = new ObjectMapper()
mapper.enable(SerializationFeature.INDENT_OUTPUT)

def json = mapper.readValue(file, Map.class)

if (deprecationMessage) {
    def orderedJson = new LinkedHashMap()
    json.each { key, value ->
        orderedJson.put(key, value)
        if (key == "keywords") {
            orderedJson.put("deprecated", [
                message         : (String) deprecationMessage,
                documentationRef: (String) deprecationDocumentationRef
            ])
        }
    }
    json = orderedJson
} else {
    // never carry over a marker from the source template if this run doesn't want one
    json.remove("deprecated")
}

def updatedProperties = []

((List) json.get('properties')).each { property ->
    // never carry over a marker from the source template; this script adds its own below
    if (property.binding?.type == "zeebe:agentDefinition") {
        return
    }

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
