#!/usr/bin/env groovy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import java.nio.file.Files

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
def outputFilePath = new File((String) outputFile)

def file = new File((String) sourceFile)
if (!file.exists()) {
    System.err.println("Error: Source file ${sourceFile} not found")
    System.exit(1)
}

def mapper = new ObjectMapper()
mapper.enable(SerializationFeature.INDENT_OUTPUT)

def json = mapper.readValue(file, Map.class)

// copy existing file to versioned directory if version was updated
if (outputFilePath.exists()) {
    def existingJson = mapper.readValue(outputFilePath, Map.class)
    def existingVersion = existingJson.version as Integer
    def sourceVersion = json.version as Integer

    if (sourceVersion > existingVersion) {
        def baseName = outputFilePath.name.replaceFirst(/\.json$/, "")
        def versionedDir = new File(outputFilePath.parent, "versioned")
        def versionedFile = new File(versionedDir, "${baseName}-${existingVersion}.json")

        Files.copy(outputFilePath.toPath(), versionedFile.toPath())
        println("Backed up existing job worker template version ${existingVersion} to: ${versionedFile.path}")
    }
}

// Transform properties
def updatedProperties = []
json.properties.each { property ->
    if (property.id == "data.clientResponse") {
        // change data.clientResponse text field to be an input mapping mapping to internal_clientResponse
        property.id = "internal_clientResponse"
        property.binding = [
            name: "internal_clientResponse",
            type: "zeebe:input"
        ]
        updatedProperties.add(property)

        // add a hidden property reading from the input
        updatedProperties.add([
            id: "data.clientResponse",
            value: "=internal_clientResponse",
            binding: [
                name: "data.clientResponse",
                type: "zeebe:property"
            ],
            type: "Hidden"
        ])
    } else if (property.id == "activationCondition") {
        property.value = "=if result.kind = \"task\" then\n  is defined(result.status.state) and not(list contains([\"submitted\", \"working\"], result.status.state))\nelse\n  true"
        updatedProperties.add(property)
    } else if (property.id == "correlationKeyProcess") {
        property.value = "=internal_clientResponse.pollingData.id"
        property.type = "Hidden"
        property.remove("feel")
        property.remove("constraints")
        updatedProperties.add(property)
    } else if (property.id == "correlationKeyPayload") {
        property.value = "=pollingData.id"
        property.type = "Hidden"
        property.remove("feel")
        property.remove("constraints")
        updatedProperties.add(property)
    } else if (property.id == "resultExpression"){
        property.value = "={response: result}"
        updatedProperties.add(property)
    } else {
        updatedProperties.add(property)
    }
}

json.properties = updatedProperties
mapper.writeValue(outputFilePath, json)
