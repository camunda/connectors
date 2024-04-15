# Postman Collections parser for [`congen`](../congen-cli/README.md)

The Postman Collections parser is a generator implementation for [`congen`](../congen-cli/README.md) that generates
connector templates from Postman Collections specifications.

## Usage

The generator accepts the path or URL of the Postman Collections specification as the first parameter,
followed by an optional list of operation paths to include in the generated template.

The following command will ask the generator to include only the `/Process instances/Search for process instances` operation.

```shell
congen postman-collections-outbound generate https://raw.githubusercontent.com/camunda-community-hub/camunda-8-api-postman-collection/main/Operate%20Public%20API%20-%20SaaS.postman_collection.json "/Process instances/Search for process instances"
```

Use the options provided by `congen` to customize the generated template.
For example, the following command will generate the template with the custom element template ID.

```shell
congen --id my-element-template postman-collections-outbound generate https://raw.githubusercontent.com/camunda-community-hub/camunda-8-api-postman-collection/main/Operate%20Public%20API%20-%20SaaS.postman_collection.json
```

Or load collection directly from Postman:

```shell
congen postman-collections-outbound generate "https://api.postman.com/collections/<collection id>?access_key=<your access key>"
```

Refer to the [congen documentation](../congen-cli/README.md) for more information on the available options.

## Known limitations

- The Postman Collections generator currently supports only Postman Collections v.2.1.0 specifications.
- Only the following authentication methods from the Postman Collections specification are supported:
- - No authentication
- - HTTP Basic authentication
- - HTTP Bearer authentication
- - OAuth 2.0 with the `client_credentials` grant type
- - API key authentication
- The generator will ignore operations that have unsupported content types or authentication methods.
- Collections variables are not processed.
- Collections events are not processed.
- Overridden authentication params are not applied

## Feedback

Having an issue or want to leave a feedback? Start filing us a [ticket](https://github.com/camunda/connectors/issues/new?title=[Postman%20Generator]%20%3Cyour%20issue%3E&body=Describe%20your%20issue%20here...).
