#!/usr/bin/env groovy

def T = new GroovyClassLoader(getClass().classLoader)
    .parseClass(new File(project.basedir, '../bin/TemplateTransforms.groovy'))

// "templateFile=recipeFile" tuples, one per line. Each recipe is written to the template's
// metadata.migratesFrom, replacing any existing value and keeping other metadata keys. Must run
// after the scripts that derive templates from one another, so a derived template never inherits
// its source's recipe.
def migrationRecipes = T.parseKeyValueLines(T.requiredProperty(binding, 'migrationRecipes'))

migrationRecipes.each { templatePath, recipePath ->
    def templateFile = T.existingFile(templatePath)
    def recipeFile = T.existingFile(recipePath)

    println("Injecting ${recipeFile.name} into ${templateFile.name}")
    def json = T.readJson(templateFile)
    def recipe = T.readJson(recipeFile)
    recipe.remove('$schema')

    def metadata = new LinkedHashMap((Map) (json.metadata ?: [:]))
    metadata.put("migratesFrom", recipe)

    T.writeJson(templateFile, T.putAfter(json, "keywords", "metadata", metadata))
}
