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

// Adds the read-only prompt-caching states that the annotation generator cannot express and
// places every provider's field directly after Model.
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
if (json.id?.toString()?.contains("ai-agent-task.v2")) {
    updatedProperties = configurePromptCaching(updatedProperties)
}

json.put('properties', updatedProperties)
mapper.writeValue(new File((String) outputFile), json)
