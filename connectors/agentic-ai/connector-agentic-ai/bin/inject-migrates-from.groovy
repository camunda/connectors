#!/usr/bin/env groovy

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

// "templateFile=recipeFile" tuples, one per line. Each recipe is written to the template's
// metadata.migratesFrom, replacing any existing value. Must run after the scripts that derive
// templates from one another, so a derived template never inherits its source's recipe.
def migrationRecipes = binding.hasVariable('migrationRecipes') ? migrationRecipes : null
if (!migrationRecipes) {
    System.err.println("Error: migrationRecipes property required")
    System.exit(1)
}

def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

((String) migrationRecipes).eachLine { line ->
    line = line.trim()
    if (!line) return
    def parts = line.split('=', 2)
    if (parts.length != 2) {
        System.err.println("Error: expected templateFile=recipeFile, got '${line}'")
        System.exit(1)
    }
    def templateFile = new File(parts[0].trim())
    def recipeFile = new File(parts[1].trim())
    [templateFile, recipeFile].each {
        if (!it.exists()) {
            System.err.println("Error: ${it} not found")
            System.exit(1)
        }
    }

    println("Injecting ${recipeFile.name} into ${templateFile.name}")
    def json = mapper.readValue(templateFile, Map.class)
    def recipe = mapper.readValue(recipeFile, Map.class)
    recipe.remove('$schema')

    def ordered = new LinkedHashMap()
    json.each { key, value ->
        if (key == "metadata") return
        ordered.put(key, value)
        if (key == "keywords") {
            ordered.put("metadata", [migratesFrom: recipe])
        }
    }
    if (!ordered.containsKey("metadata")) {
        ordered.put("metadata", [migratesFrom: recipe])
    }
    mapper.writeValue(templateFile, ordered)
}
