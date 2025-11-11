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
              stdio:
                command: npx
                args:
                  - '-y'
                  - '@modelcontextprotocol/server-filesystem'
                  - '<path-to-files>'

            # STDIO servers can be started in any runtime/language, e.g. as docker container        
            time:
              stdio:
                command: docker
                args:
                  - 'run'
                  - '-i'
                  - '--rm'
                  - 'mcp/time'

            # Remote HTTP/SSE MCP server
            # start the OpenMemory MCP server first as documented on https://mem0.ai/openmemory-mcp
            openmemory:
              sse:
                # replace with the URL returned by the OpenMemory MCP link UI
                url: http://localhost:8765/mcp/openmemory/sse/<your-client-id>
```

## Example Usage

Example inputs which can be entered in the initial form:

- `Get the current time in NYC, compare it to the Berlin time zone and write the results to a markdown file`
- `What is today's wikipedia article of the day?`
- `When was the last time we spoke?`: should start to interact with the OpenMemory instance. You can prompt it to
  remember things and then ask it later about it in a different process.

_Made with ❤️ by Camunda_
