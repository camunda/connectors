# OpenAPI parser for [`congen`](../congen-cli/README.md)

The OpenAPI parser is a generator implementation for [`congen`](../congen-cli/README.md) that generates
connector templates from OpenAPI specifications.

## Usage

The generator accepts the path or URL of the OpenAPI specification as the first parameter,
followed by an optional list of operation IDs to include in the generated template.

The following command will ask the generator to include only the `listPets` operation.

```shell
congen openapi-outbound generate https://raw.githubusercontent.com/OAI/OpenAPI-Specification/main/examples/v3.0/petstore.json listPets
```

Use the options provided by `congen` to customize the generated template.
For example, the following command will generate the template with the custom element template ID.

```shell
congen --id my-element-template openapi-outbound generate https://raw.githubusercontent.com/OAI/OpenAPI-Specification/main/examples/v3.0/petstore.json
```

Refer to the [congen documentation](../congen-cli/README.md) for more information on the available options.

### Body example generation

By default, the generator will try to break down the request payload into individual fields.
This makes it easier to work with the payload in the BPMN modeler. For complex payloads,
the generator will generate a single field for the whole request payload and prefill it with an example value.

You can force the generator to generate a single field for the whole request payload by using the `--raw-body` option.
Note that this is not a standard option of `congen` and is only supported by the OpenAPI generator.
Add it after all other inputs.

```shell
congen openapi-outbound generate https://raw.githubusercontent.com/OAI/OpenAPI-Specification/main/examples/v3.0/petstore.json --raw-body
```

## Known limitations

The OpenAPI parser currently supports only OpenAPI 3.0 specifications.

Only the following authentication methods from the OpenAPI specification are supported:

- HTTP Basic authentication
- HTTP Bearer authentication
- OAuth 2.0 with the `client_credentials` grant type
- No authentication

Only the following content types are supported for request and response payloads:

- `application/json`
- `text/plain`

The generator will ignore operations that have unsupported content types or authentication methods.
