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
    if (!json.name?.toString()?.endsWith(" (Deprecated)")) {
        json.name += " (Deprecated)"
    }
} else {
    // never carry over a marker from the source template if this run doesn't want one
    json.remove("deprecated")
}

// Moves the given property ids to sit right after another property id. No-op if either side
// isn't found.
static def moveAfter(List properties, List idsToMove, String afterId) {
    def moving = properties.findAll { it.id in idsToMove }
    if (moving.isEmpty()) {
        return properties
    }
    def remaining = properties.findAll { !(it.id in idsToMove) }
    def anchorIndex = remaining.findIndexOf { it.id == afterId }
    if (anchorIndex < 0) {
        return properties
    }
    remaining.addAll(anchorIndex + 1, moving)
    return remaining
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

// OpenAI's Effort is declared per API family (a sibling of Model, like the other per-family
// request parameters), so it's emitted before Model. Move it after, matching Anthropic/Bedrock.
updatedProperties = moveAfter(
    updatedProperties,
    ["provider.openai.api.completions.effort", "provider.openai.api.responses.effort"],
    "provider.openai.model.model"
)

json.put('properties', updatedProperties)
mapper.writeValue(new File((String) outputFile), json)
