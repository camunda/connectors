# Inbound Connector Implementation (work in progress)

This project contains the main runtime logic for the Inbound Connector Runtime.

## Inbound Webhook Connector

The inbound webhook connector is directly baked into the Inbound Runtime, as one generic
HTTP endpoint is opened up and used. This is not provided as seperate connector library.


### Webhook Connector Properties

The following properties can be set for a webhook in your BPMN Model:

| Property | Required | Default value | Description |
| :- | :- | :- | :- |
| inbound.type | yes | | Needs to be set to `webhook` |
| inbound.context | yes |  | Context path used on Webhook REST endpoint (http://endpoint/inbound/`context`/) |
| inbound.activationCondition | yes | |
| inbound.variableMapping | no | |
| **HMAC** |
| inbound.shouldValidateHmac | yes | `disabled` | `enabled` or `disabled` |
| inbound.hmacSecret | no |  | Secret key shared between Camunda and webhook caller
| inbound.hmacHeader | no | | Header where the actual HMAC signature is stored
| inbound.hmacAlgorithm | no | | `sha1`, `sha256`, or `sha512` |


### Calculating HMAC of your content

If you want to play around with the connector and need to calculate the HMAC of your content,
you'll need to decide:

- HMAC algorithm (e.g., SHA-256)
- Secret key (e.g., __mySecretKey__)

Example of calculating HMAC signature with `gh-webhook-request.json` file content and secret `mySecretKey`:

```
openssl dgst -sha256 -hmac "mySecretKey" < src/test/resources/hmac/gh-webhook-request.json
```

```
$> dd22cfb7ae96875d81bd1a695a0244f2b4c32c0938be0b445f520b0b3e0f43fd
```



### Example

#### Deploy process

Via Modeler or zbctl:

```bash
# working process { context=GITHUB_INBOUND, shouldValidateHmac=disabled }
zbctl --insecure deploy resource example/pull-request-notification.bpmn

# working process { context=GITHUB_INBOUND, shouldValidateHmac=enabled }
zbctl --insecure deploy resource example/pull-request-notification-hmac-on.bpmn

# broken process { context=GITHUB_INBOUND_BROKEN, shouldValidateHmac=disabled }
zbctl --insecure deploy resource example/broken.bpmn
```

### Start a process


```bash
# webhook that activates for file example/pull-request-notification.bpmn
curl -XPOST -H 'Content-Type: application/json' localhost:8080/inbound/GITHUB_INBOUND  --data @example/webhook-payload-activates.json

# webhook that activates for file example/pull-request-notification-hmac-on.bpmn
curl -XPOST -H 'Content-Type: application/json' -H "X-Hub-Signature-256: sha256=98ae3cdd258e3f7e7334d7963c779237392d05d32e991a167f3943e9f8747de2" localhost:8080/inbound/GITHUB_INBOUND  --data @example/webhook-payload-activates-packed.json

# webhook that does not activate (wrong HMAC signature) for file example/pull-request-notification-hmac-on.bpmn
curl -XPOST -H 'Content-Type: application/json' -H "X-Hub-Signature-256: sha256=f93e172fb95efbc1406aee1140d6283f37a36b832cd4efc8b1e64114f29c86f3" localhost:8080/inbound/GITHUB_INBOUND  --data @example/webhook-payload-activates-packed.json

# webhook that is ignored (wrong type) for file example/pull-request-notification.bpmn
curl -XPOST -H 'Content-Type: application/json' localhost:8080/inbound/GITHUB_INBOUND  --data @example/webhook-payload-ignored.json

# webhook that reports an error (broken activation condition) for file example/broken.bpmn
curl -XPOST -H 'Content-Type: application/json' localhost:8080/inbound/GITHUB_INBOUND_BROKEN  --data @example/webhook-payload-ignored.json
```
