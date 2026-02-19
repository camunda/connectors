# MCP Client Example

This example relies on an MCP client being configured as part of the connector runtime. To use the client IDs
referenced in the example, add the following configuration to your connectors application config:

```yaml
camunda:
  connector:
    agenticai:
      mcp:
        client:
          enabled: true
          clients:
            # STDIO filesystem server started via NPX (make sure you have a Node.js environment available)
            # replace path to files to the directory you want the model to have access to
            # you can also add multiple paths, see https://github.com/modelcontextprotocol/servers/tree/main/src/filesystem
            filesystem:
              type: stdio
              stdio:
                command: npx
                args:
                  - '-y'
                  - '@modelcontextprotocol/server-filesystem'
                  - '<path-to-files>'

            # STDIO servers can be started in any runtime/language, e.g. as docker container        
            time:
              type: stdio
              stdio:
                command: docker
                args:
                  - 'run'
                  - '-i'
                  - '--rm'
                  - 'mcp/time'

            # Remote Streamable HTTP MCP server example
            # fetch:
            #   enabled: true
            #   type: http
            #   http:
            #     url: https://remote.mcpservers.org/fetch/mcp
            #     headers:
            #       X-Dummy: dummy-value
            #     # authentication examples
            #     authentication:
            #       type: basic # or bearer or oauth
            #       basic:
            #         username: my-username
            #         password: my-password
            #       bearer:
            #         token: my-token
            #       oauth:
            #         oauth-token-endpoint: http://example.com/oauth/token
            #         client-id: my-client-id
            #         client-secret: my-client-secret
            #         scopes: my-scope
            #         audience: my-audience
            #         client-authentication: basic-auth-header # or credentials-body
```

## Example Usage

Example inputs which can be entered in the initial form:

- `Get the current time in NYC, compare it to the Berlin time zone and write the results to a markdown file`
- `What is today's wikipedia article of the day?`

_Made with ❤️ by Camunda_
