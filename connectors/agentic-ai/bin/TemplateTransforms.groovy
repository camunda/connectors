/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import java.nio.file.Files

/**
 * Generic helpers shared by the element template transform scripts of the agentic AI modules. The
 * scripts find properties by hard-coded ids, so every lookup here fails the build when an id or
 * anchor is missing instead of silently leaving the template unchanged. Connector-specific helpers
 * live next to the connector's scripts, e.g. connector-aiagent/bin/AgentTemplateTransforms.groovy.
 *
 * Load it from a gmavenplus script with:
 * <pre>
 * def T = new GroovyClassLoader(getClass().classLoader)
 *     .parseClass(new File(project.basedir, '../bin/TemplateTransforms.groovy'))
 * </pre>
 */
class TemplateTransforms {

    static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    static void fail(String message) {
        throw new IllegalStateException(message)
    }

    static Object optionalProperty(Binding binding, String name) {
        return binding.hasVariable(name) ? binding.getVariable(name) : null
    }

    static String requiredProperty(Binding binding, String name) {
        def value = optionalProperty(binding, name)
        if (!value) {
            fail("Script property '${name}' is required")
        }
        return value.toString().trim()
    }

    static File existingFile(Object path) {
        def file = new File(path.toString().trim())
        if (!file.exists()) {
            fail("File ${file} not found")
        }
        return file
    }

    static Map readJson(File file) {
        return MAPPER.readValue(file, Map)
    }

    static void writeJson(File file, Object json) {
        MAPPER.writeValue(file, json)
    }

    /** Parses "key=value" lines. Blank lines are skipped; any other line without "=" fails. */
    static Map<String, String> parseKeyValueLines(Object text) {
        Map<String, String> result = [:]
        (text?.toString() ?: "").eachLine { String line ->
            line = line.trim()
            if (!line) return
            def parts = line.split('=', 2)
            if (parts.length != 2) {
                fail("Expected a 'key=value' line, got '${line}'")
            }
            result[parts[0].trim()] = parts[1].trim()
        }
        return result
    }


    /**
     * Copies the existing output file to versioned/NAME-VERSION.json if the new template has a
     * higher version.
     */
    static void backupIfVersionBumped(File outputFile, Map newJson) {
        if (!outputFile.exists()) {
            return
        }
        def existingVersion = readJson(outputFile).version as Integer
        def newVersion = newJson.version as Integer
        if (!(newVersion > existingVersion)) {
            return
        }
        def baseName = outputFile.name.replaceFirst(/\.json$/, "")
        def versionedFile = new File(new File(outputFile.parent, "versioned"), "${baseName}-${existingVersion}.json")
        Files.copy(outputFile.toPath(), versionedFile.toPath())
        println("Backed up existing template version ${existingVersion} to: ${versionedFile.path}")
    }

    /** Returns a copy of the map with the entry inserted right after anchorKey, replacing any existing entry for key. */
    static Map putAfter(Map map, String anchorKey, String key, Object value) {
        if (!map.containsKey(anchorKey)) {
            fail("Key '${anchorKey}' not found, cannot insert '${key}' after it")
        }
        def result = new LinkedHashMap()
        map.each { k, v ->
            if (k == key) return
            result.put(k, v)
            if (k == anchorKey) {
                result.put(key, value)
            }
        }
        return result
    }

    /**
     * Adds a "deprecated" block after "keywords" if a message is given, otherwise removes any
     * inherited block.
     */
    static Map applyDeprecation(Map json, Object message, Object documentationRef) {
        if (!message) {
            json.remove("deprecated")
            return json
        }
        if (!documentationRef) {
            fail("A deprecation message requires a deprecation documentation reference")
        }
        return putAfter(json, "keywords", "deprecated", [
            message         : message.toString(),
            documentationRef: documentationRef.toString()
        ])
    }

    static void requireIds(List entries, Collection<String> ids, String what) {
        def missing = ids.findAll { id -> !entries.any { it.id == id } }
        if (missing) {
            fail("${what} not found: ${missing}")
        }
    }

    static boolean isTaskDefinitionType(Map property) {
        return property.binding?.type == "zeebe:taskDefinition" && property.binding?.property == "type"
    }

    static void requireTaskDefinitionType(List properties) {
        if (!properties.any { isTaskDefinitionType((Map) it) }) {
            fail("Property with a zeebe:taskDefinition type binding not found")
        }
    }

    /** Moves the given properties, keeping their relative order, right after the property afterId. */
    static List moveAfter(List properties, List<String> idsToMove, String afterId) {
        requireIds(properties, idsToMove + afterId, "Properties to reorder")
        def moving = properties.findAll { it.id in idsToMove }
        def remaining = properties.findAll { !(it.id in idsToMove) }
        remaining.addAll(remaining.findIndexOf { it.id == afterId } + 1, moving)
        return remaining
    }
}
