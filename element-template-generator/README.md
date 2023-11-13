# Connector template generator

Connector template generator is a set of tools for automated creation of connector templates for Camunda Modeler.

## Modules

- [element-template-generator-core](core) is a library that defines the DSL for programmatic creation of element templates,
as well as the interfaces for different template generator implementations.
  - It also contains the annotation-based generator implementation that can be used to generate Connector templates.
- [element-template-generator-maven-plugin](maven-plugin) contains a Maven plugin that invokes the annotation-based template generator.
- [element-template-generator-http-dsl](http-dsl) is a utility library that extends the core DSL with functionality tailored specifically for the [REST protocol connector](../connectors/http/rest).
- [congen-cli](congen-cli) is a universal command-line tool that can invoke different generator implementations.
- [openapi-parser](openapi-parser) is a generator implementation that transforms OpenAPI specifications into the core DSL.
  - It can be invoked with [congen-cli](congen-cli).

Refer to the documentation of individual modules for more details.
