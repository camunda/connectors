# Camunda Microsoft Teams Connector

Find the user documentation in our [Camunda Platform 8 Docs](https://docs.camunda.io/docs/components/integration-framework/connectors/out-of-the-box-connectors/available-connectors-overview).

## Build

```bash
mvn clean package
```

## API

### Input

#### Authentication
##### Bearer token

```json
{
  "authentication": {
    "type": "token",
    "token": "{{secrets.BEARER_TOKEN_KEY}}"
  },
  "data": {}
}
```
##### Client secrets
```json
{
  "authentication": {
    "type": "clientCredentials",
    "clientId": "{{secrets.CLIENT_ID_KEY}}",
    "tenantId": "{{secrets.TENANT_ID_KEY}}",
    "clientSecret": "{{secrets.CLIENT_SECRET_KEY}}"
  },
  "data": {}
}
```

#### Data

##### Create channel

```json
 {
  "authentication": {},
  "data": {
    "method": "createChannel",
    "groupId": "{{secrets.GROUP_ID_KEY}}",
    "name": "{{secrets.CHANNEL_NAME_KEY}}",
    "channelType": "standard",
    "owner": "{{secrets.CHANNEL_OWNER_KEY}}"
  }
}
```
##### Get channel

```json
{
  "authentication": {},
  "data": {
    "method": "getChannel",
    "groupId": "{{secrets.GROUP_ID_KEY}}",
    "channelId": "{{secrets.CHANNEL_ID_KEY}}"
  }
}
```
##### Get channel message by id

```json
{
  "authentication": {},
  "data": {
    "method": "getMessageFromChannel",
    "groupId": "{{secrets.GROUP_ID_KEY}}",
    "channelId": "{{secrets.CHANNEL_ID_KEY}}",
    "messageId": "{{secrets.CHANNEL_MESSAGE_ID_KEY}}"
  }
}
```
##### Get channel members

```json
{
  "authentication": {},
  "data": {
    "method": "listMembersInChannel",
    "groupId": "{{secrets.GROUP_ID_KEY}}",
    "channelId": "{{secrets.CHANNEL_ID_KEY}}"
  }
}
```
##### Get channel messages

```json
{
  "authentication": {},
  "data": {
    "method": "listChannelMessages",
    "groupId": "{{secrets.GROUP_ID_KEY}}",
    "channelId": "{{secrets.CHANNEL_ID_KEY}}"
  }
}
```

##### Get list all channels

```json
{
  "authentication": {},
  "data": {
    "method": "listAllChannels",
    "groupId": "{{secrets.GROUP_ID_KEY}}",
    "filter": "{{secrets.CHANNEL_FILTER_KEY}}"
  }
}
```

##### Get list message replies in channel

```json
{
  "authentication": {},
  "data": {
    "method": "listMessageRepliesInChannel",
    "groupId": "19:1c5b01696d2e4a179c292bc9cf04e63b@thread.v2",
    "channelId": "abc01234-0c7f-012c-9876-testClientId",
    "messageId": "01234436675734"
  }
}
```
##### Send message in channel

```json
{
  "authentication": {},
  "data": {
    "method": "sendMessageToChannel",
    "groupId": "{{secrets.GROUP_ID_KEY}}",
    "channelId": "{{secrets.CHANNEL_ID_KEY}}",
    "content": "{{secrets.CHANNEL_MESSAGE_CONTENT_KEY}}"
  }
}
```

### Output

See [Microsoft Graph API documentation](https://learn.microsoft.com/en-us/graph/api/resources/channel?view=graph-rest-1.0)

## Element Template

The element templates can be found in the [element-templates/microsoft-teams-connector.json](element-templates/microsoft-teams-connector.json) file.
