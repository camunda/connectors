#!/usr/bin/env groovy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import java.nio.file.Files

static def replaceDocumentationLinks(String text) {
    if (text == null) return null
    return text.replace(
        "out-of-the-box-connectors/agentic-ai-aiagent-task/",
        "out-of-the-box-connectors/agentic-ai-aiagent-subprocess/"
    )
}

// Moves the given property ids to sit right after another property id. No-op if either side
// isn't found. Duplicated in transform-ai-agent-task-template.groovy since this script's
// execution runs before that one's, per the pom's execution order for the v2 task/sub-process
// pair.
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

// Adds the read-only prompt-caching states that the annotation generator cannot express and
// places every provider's field directly after Model. Duplicated in the task transform for the
// execution-order reason above.
static def readOnlyPromptCachingProperty(
    String id,
    String description,
    String tooltip,
    boolean enabled,
    String bindingName,
    String provider
) {
    return [
        id: id,
        label: "Prompt caching",
        description: description,
        tooltip: tooltip,
        value: enabled,
        editable: false,
        group: "model",
        binding: [name: bindingName, type: "property"],
        condition: [property: "provider.type", equals: provider, type: "simple"],
        type: "Boolean"
    ]
}

static def configurePromptCaching(List properties) {
    def cachingPropertyIds = [
        "provider.anthropic.model.parameters.promptCaching.enabled",
        "provider.bedrock.model.parameters.promptCaching.enabled",
        "promptCaching.openai.status",
        "promptCaching.googleGemini.status",
        "promptCaching.custom.status"
    ]
    def cachingProperties = properties.findAll { it.id in cachingPropertyIds }
        .collectEntries { [(it.id): it] }
    def remaining = properties.findAll { !(it.id in cachingPropertyIds) }

    def cachingTooltip = { String documentationUrl ->
        "Can speed up responses and lower API costs by reusing text from recent requests. Best for long conversations or large documents." +
            "<br><br>See the <a href=\"${documentationUrl}\" target=\"_blank\">caching documentation</a>."
    }
    def integrations = [
        [
            modelId: "provider.anthropic.model.model",
            cachingProperty: cachingProperties["provider.anthropic.model.parameters.promptCaching.enabled"]
        ],
        [
            modelId: "provider.bedrock.model.model",
            cachingProperty: cachingProperties["provider.bedrock.model.parameters.promptCaching.enabled"]
        ],
        [
            modelId: "provider.openai.model.model",
            cachingProperty: readOnlyPromptCachingProperty(
                "promptCaching.openai.status",
                "Automatic.",
                cachingTooltip("https://developers.openai.com/api/docs/guides/prompt-caching"),
                true,
                "modeler:promptCachingOpenAI",
                "openai"
            )
        ],
        [
            modelId: "provider.googleGemini.model.model",
            cachingProperty: readOnlyPromptCachingProperty(
                "promptCaching.googleGemini.status",
                "Automatic.",
                cachingTooltip("https://ai.google.dev/gemini-api/docs/caching"),
                true,
                "modeler:promptCachingGoogleGemini",
                "google-gemini"
            )
        ],
        [
            modelId: "provider.model",
            cachingProperty: readOnlyPromptCachingProperty(
                "promptCaching.custom.status",
                "Not available.",
                "The prompt caching property does not control caching for custom implementations. Use a custom solution instead.",
                false,
                "modeler:promptCachingCustom",
                "custom"
            )
        ]
    ]

    integrations.each { integration ->
        def anchorIndex = remaining.findIndexOf { it.id == integration.modelId }
        if (anchorIndex >= 0 && integration.cachingProperty) {
            remaining.add(anchorIndex + 1, integration.cachingProperty)
        }
    }
    return remaining
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

def templateId = templateId
if (!templateId) {
    System.err.println("Error: Template id required as property")
    System.exit(1)
}

def connectorType = connectorType
if (!connectorType) {
    System.err.println("Error: Connector type required as property")
    System.exit(1)
}

// optional: agentType for the zeebe:agentDefinition marker; unset means no marker is added
def agentType = binding.hasVariable('agentType') ? agentType : null

// optional: mark the derived template as deprecated; unset means no "deprecated" block is added
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

// never carry over a "deprecated" block from the source template; this script decides
// deprecation for the derived template on its own, via the properties above
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
    json.remove("deprecated")
}

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
        println("Backed up existing template version ${existingVersion} to: ${versionedFile.path}")
    }
}

// Update template metadata
json.id = (String) templateId
json.name = "AI Agent Sub-process"
json.description = "Run a multi-step AI reasoning loop with dynamic tool selection"
json.documentationRef = replaceDocumentationLinks(json.documentationRef)

if (isHybrid) {
    json.id += "-hybrid"
    json.name = "Hybrid " + json.name
}

if (deprecationMessage) {
    json.name += " (Deprecated)"
}

// Change BPMN element configuration
json.appliesTo = ["bpmn:SubProcess"]
json.elementType.value = "bpmn:AdHocSubProcess"

// Transform groups
def updatedGroups = []

((List) json.get('groups')).each { group ->
    if (group.tooltip) {
        group.tooltip = replaceDocumentationLinks(group.tooltip)
    }

    updatedGroups.add(group)

    if (group.id == "limits") {
        updatedGroups.add([
            id: "events",
            label: "Event handling",
            tooltip : "Configure how event sub-process results are handled. Results are added as user messages to the running agent.",
            openByDefault: false
        ])
    }
}

json.put('groups', updatedGroups)

// Transform properties
def skipProperties = [
    "data.tools.containerElementId",
    "data.tools.toolCallResults",
    "resultExpression"
]
def updatedProperties = []

((List) json.get('properties')).each { property ->
    if (property.id in skipProperties) {
        return
    }

    // never carry over a marker from the source template; this script adds its own below
    if (property.binding?.type == "zeebe:agentDefinition") {
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
        property.value = (String) connectorType

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
            value: "={\n  id: toolCall._meta.id,\n  name: toolCall._meta.name,\n  content: toolCallResult,\n  completedAt: now()\n}",
            type: "Hidden"
        ])

        // Mark the ad-hoc sub-process as an agentic tool container so linting rules
        // (e.g. fromAi() validation) can detect it.
        updatedProperties.add([
            value: "true",
            binding: [
                name: "io.camunda.agenticai.toolContainer",
                type: "zeebe:property"
            ],
            type: "Hidden"
        ])

        // Mark the element as a native agent definition so the engine creates an agent-definition
        // record at deploy time.
        if (agentType) {
            updatedProperties.add([
                value: (String) agentType,
                binding: [
                    type: "zeebe:agentDefinition",
                    property: "agentType"
                ],
                type: "Hidden"
            ])
        }
    } else if (property.id == "id") {
        property.value = (String) templateId
        updatedProperties.add(property)
    } else if (property.id == "resultVariable") {
        property.binding = [source: "=agent", type: "zeebe:output"]
        property.value = "agent"
        updatedProperties.add(property)
    } else if (property.id == "data.agentContext") {
        property.id = "agentContext"
        property.description = "Initial agent context from previous interactions. Avoid reusing context variables across agents to prevent issues with stale data or tool access."
        property.optional = false
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
                description: "Behavior on completing an event sub-process.",
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
                        name: "Cancel tool calls",
                        value: "INTERRUPT_TOOL_CALLS"
                    ]
                ]
            ])
        }
    }
}

// empty input mapping for local agent variable — must be last so it is
// evaluated after the agentContext input has read =agent.context
updatedProperties.add([
    binding: [
        name: "agent",
        type: "zeebe:input"
    ],
    type: "Hidden"
])

// OpenAI's Effort is declared per API family (a sibling of Model, like the other per-family
// request parameters), so it's emitted before Model. Move it after, matching Anthropic/Bedrock.
updatedProperties = moveAfter(
    updatedProperties,
    ["provider.openai.api.completions.effort", "provider.openai.api.responses.effort"],
    "provider.openai.model.model"
)
if (file.name.contains("ai-agent-task.v2")) {
    updatedProperties = configurePromptCaching(updatedProperties)
}

json.put('properties', updatedProperties)
mapper.writeValue(outputFilePath, json)
