# congen

`congen` is a universal command-line tool that can invoke different Connector template generator implementations.

It can be bundled with generators via Java Service Loader mechanism. Generators must be available in the classpath
and implement the `io.camunda.connector.generator.api.CliCompatibleTemplateGenerator` interface.

`congen` comes pre-bundled with [OpenAPI template generator](../openapi-parser).

## Installation

Currently, the only way to install `congen` is to build it from source.

### Local build

To build `congen` locally, check out the repository and build the project with Maven:

```shell
mvn install -pl element-template-generator/congen-cli -am
```

This will build the `congen-cli` module and all its dependencies.

Navigate to the `element-template-generator/congen-cli/target/appassembler` directory.
The executable `congen` script is located in the `bin` directory. The compiled Java code required for
running `congen` is located in the `repo` directory. Make sure to copy both directories to your application installation directory.

Executables for Windows and Unix systems are provided (`.bat` and Shell scripts, respectively).

## Usage

Run `congen -h` to see the usage information and a list of available parameters.

`congen` supports the following commands:
- `generate` invokes the selected generator implementation and prints the generated template to the standard output.
- `scan` invokes the generator in a dry-run mode and prints the information
- `list` prints the list of available generators and usage information for each generator

### Examples

The following command will invoke the generator named `openapi-outbound` and pass the specified OpenAPI specification.

```shell
congen generate openapi-outbound https://petstore3.swagger.io/api/v3/openapi.json
```

Note that `congen` will pass all parameters specified after the command name to the generator implementation.
For example, the OpenAPI generator accepts the path or URL of the OpenAPI specification as the first parameter,
followed by an optional list of operation IDs to include in the generated template.

The following command will ask the generator to include only the `findPetsById` and `addPet` operations.

```shell
congen generate openapi-outbound https://petstore3.swagger.io/api/v3/openapi.json findPetsById addPet
```

The command below will generate the template with the custom element template ID.

```shell
congen --id my-element-template generate openapi-outbound https://petstore3.swagger.io/api/v3/openapi.json
```

Refer to the [OpenAPI generator documentation](../openapi-parser/README.md) for more information on
how to use it.
