import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import java.nio.file.Files

def mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
def recipes = binding.getVariable("migrationRecipes").toString()

recipes.eachLine { line ->
    line = line.trim()
    if (!line) {
        return
    }
    def paths = line.split("=", 2)
    if (paths.length != 2) {
        throw new IllegalArgumentException("Expected a template=recipe path, got '${line}'")
    }

    def templateFile = new File(paths[0])
    def recipeFile = new File(paths[1])
    if (!templateFile.isFile() || !recipeFile.isFile()) {
        throw new IllegalStateException("Migration template or recipe file is missing: ${line}")
    }

    def template = mapper.readValue(templateFile, Map)
    def recipe = mapper.readValue(recipeFile, Map)
    recipe.remove("\$schema")
    def metadata = new LinkedHashMap((Map) (template.metadata ?: [:]))
    metadata.put("migratesFrom", recipe)

    def updated = new LinkedHashMap()
    def anchor = template.containsKey("keywords") ? "keywords" : "description"
    template.each { key, value ->
        if (key != "metadata") {
            updated.put(key, value)
            if (key == anchor) {
                updated.put("metadata", metadata)
            }
        }
    }
    if (!updated.containsKey("metadata")) {
        updated.put("metadata", metadata)
    }

    Files.writeString(templateFile.toPath(), mapper.writeValueAsString(updated) + "\n")
}
