# Inbound Webhook Connector Runtime (WORK IN PROGRESS)

see https://github.com/camunda/product-hub/issues/174

Currently work in progress - not yet stable!


## Start Inbound Connector Runtime

### Configure for C8 SaaS

You can use Camunda SaaS. Edit `src/main/application.properties` accordingly:

```properties
zeebe.client.cloud.cluster-id=xxx
zeebe.client.cloud.client-id=xxx
zeebe.client.cloud.client-secret=xxx
zeebe.client.cloud.region=bru-2
```

You can further configure the connection to Operate.  As a default, it will use the `cluster-id` and credentials configured for Zeebe, but can also configure it otherwise.

Connect to Operate locally using username and password:

```properties
camunda.operate.client.url=http://localhost:8081
camunda.operate.client.username=demo
camunda.operate.client.password=demo
```

Set specific credentials for Operate (different than for Zeebe API):

```properties
camunda.operate.client.client-id=xxx
camunda.operate.client.client-secret=xxx
```



### Use Docker Compose

```bash
docker compose up --wait
```

URLs:

- Zeebe: 'grpc://localhost:26500'
- Operate: `http://localhost:8081`
- Elasticsearch: `http://localhost:9200`

Make sure `src/main/application.properties` is configured properly:


```properties
zeebe.client.broker.gateway-address=127.0.0.1:26500
zeebe.client.security.plaintext=true
```

When running against a self-managed environment you might also need to configure the keycloak endpoint to not use Operate username/password authentication.

```properties
camunda.operate.client.keycloak-url=http://localhost:18080
camunda.operate.client.keycloak-realm=camunda-platform
```

### Further Configurations

The Connector can read all properties from [https://github.com/camunda-community-hub/spring-zeebe](Spring Zeebe), this is especially important to configure the connection to Zeebe.


### Run

```bash
mvn spring-boot:run
```


## Webhook Connector Properties

| Property | Required | Default value | Description |
| :- | :- | :- | :- |
| inbound.type | yes | | Needs to be set to `webhook` |
| inbound.context | yes |  | Contxt path used on Webhook REST endpoint (http://endpoint/inbound/`context`/) |
| inbound.activationCondition | yes | |
| inbound.variableMapping | no | |    
| **Secret Handling** |
| inbound.secretExtractor  | yes | |
| inbound.secret  | yes | |
| **HMAC** |
| inbound.shouldValidateHmac | no | `disabled` | `enabled` or `disabled` |
| inbound.hmacSecret | no | |
| inbound.hmacHeader | no | |
| inbound.hmacAlgorithm | no | | `sha1`, `sha256`, or `sha512` |

## Run example

### Deploy process

Via Modeler or zbctl:

```bash
# working process { context=GITHUB_INBOUND }
zbctl --insecure deploy resource example/pull-request-notification.bpmn

# broken process { context=GITHUB_INBOUND_BROKEN }
zbctl --insecure deploy resource example/broken.bpmn
```

### Start a process


```bash
# webhook that activates
curl -XPOST -H 'Content-Type: application/json' -H "X-Hub-Signature: secrets.GITHUB_INBOUND_SECRET" localhost:8080/inbound/GITHUB_INBOUND  --data @example/webhook-payload-activates.json

# webhook without secret
curl -XPOST -H 'Content-Type: application/json' localhost:8080/inbound/GITHUB_INBOUND  --data @example/webhook-payload-activates.json

# webhook that is ignored (wrong type)
curl -XPOST -H 'Content-Type: application/json' -H "X-Hub-Signature: secrets.GITHUB_INBOUND_SECRET" localhost:8080/inbound/GITHUB_INBOUND  --data @example/webhook-payload-ignored.json

# webhook that reports an error (broken activation condition)
curl -XPOST -H 'Content-Type: application/json' -H "X-Hub-Signature: secrets.GITHUB_INBOUND_SECRET" localhost:8080/inbound/GITHUB_INBOUND_BROKEN  --data @example/webhook-payload-ignored.json
```
