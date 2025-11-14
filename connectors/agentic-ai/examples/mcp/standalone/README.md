# MCP Standalone example

Example how to use MCP clients in standalone mode for tool listing and calling.

This example relies on a filesystem MCP client being configured as part of the connector runtime. Add the following
configuration to your connectors application config:

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
              stdio:
                command: npx
                args:
                  - '-y'
                  - '@modelcontextprotocol/server-filesystem'
                  - '<path-to-files>'
```
