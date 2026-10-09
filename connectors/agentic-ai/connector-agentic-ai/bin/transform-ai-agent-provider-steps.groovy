#!/usr/bin/env groovy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

def compatiblePresets = [
    [id: "ollama", name: "Ollama",
     description: "Local models via Ollama.",
     keywords: ["Ollama", "Local", "Llama"],
     properties: [
         "provider.openai.backend.custom.endpoint": "http://localhost:11434/v1",
         "provider.openai.backend.custom.authentication.type": "none"
     ]],
    [id: "lmstudio", name: "LM Studio",
     description: "Local models via LM Studio.",
     keywords: ["LM Studio", "Local"],
     properties: [
         "provider.openai.backend.custom.endpoint": "http://localhost:1234/v1",
         "provider.openai.backend.custom.authentication.type": "none"
     ]],
    [id: "camunda", name: "Camunda-provided LLM (SaaS only)",
     description: "Only on select plans.",
     keywords: ["Camunda", "LLM", "SaaS"],
     properties: [
         "provider.openai.backend.custom.endpoint": "=camunda.secrets.CAMUNDA_PROVIDED_LLM_API_ENDPOINT",
         "provider.openai.backend.custom.authentication.type": "apiKey",
         "provider.openai.backend.custom.authentication.apiKey": "=camunda.secrets.CAMUNDA_PROVIDED_LLM_API_KEY",
         "provider.openai.model.model": "=camunda.secrets.CAMUNDA_PROVIDED_LLM_DEFAULT_MODEL"
     ]]
]

((String) sourceFiles).eachLine { path ->
    if (!path.trim()) return
    println("Transforming provider steps in ${path.trim()}")
    def file = new File(path.trim())
    def json = mapper.readValue(file, Map.class)
    def presetsById = json.presets.collectEntries { [(it.id): it] }
    def presets = []

    // ETG stops at the connection record instead of following its backend discriminator.
    json.steps.each { step ->
        def providerPreset = presetsById[step.presetId]
        def providerProperties = providerPreset.get("properties")
        def providerId = providerProperties["provider.type"]
        def backendProperty = json.get("properties").find {
            it.id?.endsWith(".backend.type") &&
                it.condition?.property == "provider.type" && it.condition?.equals == providerId
        }
        if (!backendProperty) {
            presets.add(providerPreset)
            return
        }

        def prefix = step.presetId
        def backendKey = backendProperty.id
        def keywords = step.keywords
        step.remove("presetId")
        step.remove("keywords")
        step.steps = backendProperty.choices.collect { backend ->
            def presetId = "${prefix}_type_${backend.value}".toString()
            presets.add([
                id: presetId,
                properties: providerProperties + [(backendKey): backend.value]
            ])
            def name = backend.value == "custom"
                ? "${step.name - ~/ AI$/} ${backend.name.toLowerCase()}".toString()
                : backend.name
            [name: name, keywords: (keywords + [backend.name]).unique(), presetId: presetId]
        }

        if (providerId == "openai") {
            compatiblePresets.each { preset ->
                def presetId = "${prefix}_type_custom_${preset.id}".toString()
                presets.add([
                    id: presetId,
                    properties: providerProperties + [
                        (backendKey): "custom",
                        "provider.openai.api.type": "completions"
                    ] + preset.get("properties")
                ])
                step.steps.add([
                    name: preset.name, description: preset.description,
                    keywords: preset.keywords, presetId: presetId
                ])
            }
        }
    }

    json.presets = presets
    mapper.writeValue(file, json)
}
