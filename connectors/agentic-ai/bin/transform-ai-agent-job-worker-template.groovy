#!/usr/bin/env groovy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import java.nio.file.Files

static def replaceDocumentationLinks(String text) {
    if (text == null) return null
    return text.replace(
        "out-of-the-box-connectors/agentic-ai-aiagent-task/",
        "out-of-the-box-connectors/agentic-ai-aiagent-process/"
    )
}

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

def isHybrid = json.id?.toString()?.contains("-hybrid")

// copy existing file to versioned directory if version was updated
if (outputFilePath.exists() && !isHybrid) {
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

// Update template metadata
json.id = "io.camunda.connectors.agenticai.aiagent.jobworker.v1"
json.name = "AI Agent Process"
json.description = "Processes user requests with an integrated, customizable toolbox and services for dynamic workflows."

if (isHybrid) {
    json.id += "-hybrid"
    json.name = "Hybrid " + json.name
}

// Change BPMN element configuration
json.appliesTo = ["bpmn:SubProcess"]
json.elementType.value = "bpmn:AdHocSubProcess"

// Transform groups
def updatedGroups = []

json.groups.each { group ->
    if (group.tooltip) {
        group.tooltip = replaceDocumentationLinks(group.tooltip)
    }

    updatedGroups.add(group)

    if (group.id == "limits") {
        updatedGroups.add([
            id: "events",
            label: "Event handling",
            openByDefault: false
        ])
    }
}

json.groups = updatedGroups

// Transform properties
def skipProperties = [
    "data.tools.containerElementId",
    "data.tools.toolCallResults",
    "resultExpression"
]
def updatedProperties = []

json.properties.each { property ->
    if (property.id in skipProperties) {
        return
    }

    if (property.description) {
        property.description = replaceDocumentationLinks(property.description)
    }

    if (property.tooltip) {
        property.tooltip = replaceDocumentationLinks(property.tooltip)
    }

    // Update specific property values and bindings
    if (property.binding?.type == "zeebe:taskDefinition" && property.binding?.property == "type") {
        property.value = "io.camunda.agenticai:aiagent-job-worker:1"

        // Add new hidden properties after the type property
        updatedProperties.add(property)

        updatedProperties.add([
            id: "outputCollection",
            binding: [
                property: "outputCollection",
                type: "zeebe:adHoc"
            ],
            value: "toolCallResults",
            type: "Hidden"
        ])

        updatedProperties.add([
            id: "outputElement",
            binding: [
                property: "outputElement",
                type: "zeebe:adHoc"
            ],
            value: "={\n  id: toolCall._meta.id,\n  name: toolCall._meta.name,\n  content: toolCallResult\n}",
            type: "Hidden"
        ])
    } else if (property.id == "id") {
        property.value = "io.camunda.connectors.agenticai.aiagent.jobworker.v1"
        updatedProperties.add(property)
    } else if (property.id == "resultVariable") {
        property.binding = [source: "=agent", type: "zeebe:output"]
        property.value = "agent"
        updatedProperties.add(property)
    } else if (property.id == "data.agentContext") {
        property.id = "agentContext"
        property.description = "Initial agent context from previous interactions. Avoid reusing context variables across agents to prevent issues with stale data or tool access."
        property.optional = true
        property.feel = "required"
        property.binding.name = "agentContext"
        property.remove("value")
        property.remove("constraints")
        updatedProperties.add(property)
    } else {
        updatedProperties.add(property)

        // Add includeAgentContext after includeAssistantMessage
        if (property.id == "data.response.includeAssistantMessage") {
            updatedProperties.add([
                id: "data.response.includeAgentContext",
                label: "Include agent context",
                description: "Include the agent context as part of the result object.",
                optional: true,
                feel: "static",
                group: "response",
                binding: [
                    name: "data.response.includeAgentContext",
                    type: "zeebe:input"
                ],
                tooltip: "Use this option if you need to re-inject the previous agent context into a future agent execution, for example when modeling a user feedback loop between an agent and a user task.",
                type: "Boolean"
            ])
        }

        // Add events behavior property after limits
        if (property.id == "data.limits.maxModelCalls") {
            updatedProperties.add([
                id: "data.events.behavior",
                label: "Event handling behavior",
                description: "Behavior in combination with an event sub-process.",
                optional: false,
                value: "WAIT_FOR_TOOL_CALL_RESULTS",
                constraints: [
                    notEmpty: true
                ],
                group: "events",
                binding: [
                    name: "data.events.behavior",
                    type: "zeebe:input"
                ],
                type: "Dropdown",
                choices: [
                    [
                        name: "Wait for tool call results",
                        value: "WAIT_FOR_TOOL_CALL_RESULTS"
                    ],
                    [
                        name: "Interrupt tool calls",
                        value: "INTERRUPT_TOOL_CALLS"
                    ]
                ]
            ])
        }
    }
}

json.properties = updatedProperties
mapper.writeValue(outputFilePath, json)
