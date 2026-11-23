#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

export VAULT_ADDR="${VAULT_ADDR:-https://vault.int.camunda.com/}"
MOUNT="${CONNECTOR_SECRETS_MOUNT:-secret}"
VAULT_PATH="${CONNECTOR_SECRETS_PATH:-products/connectors/local-testing}"
PREFIX="SECRET_"
MANIFEST=".available-secrets.md"
BUILD=0
DEBUG_PORT=""

while [ $# -gt 0 ]; do
  case "$1" in
    --build) BUILD=1 ;;
    --debug-port) DEBUG_PORT="$2"; shift ;;
    -h|--help) echo "Usage: $0 [--build] [--debug-port PORT] [VAULT_PATH]"; exit 0 ;;
    *) VAULT_PATH="$1" ;;
  esac
  shift
done

for tool in vault jq mvn; do
  command -v "$tool" >/dev/null || { echo "Missing tool: $tool (run 'asdf install')" >&2; exit 1; }
done

norm() { printf '%s' "$1" | tr '[:lower:]' '[:upper:]' | sed -E 's/[^A-Z0-9]+/_/g; s/^_+|_+$//g'; }

echo "Logging in to $VAULT_ADDR (browser opens)..." >&2
VAULT_TOKEN="$(vault login -method=oidc -no-store -token-only)"
export VAULT_TOKEN

seen=" "
{
  echo "# Available connector secrets"
  echo
  echo "Generated from Vault \`$MOUNT/$VAULT_PATH\`. Names only, never values. Regenerated on every start."
  echo 'Use in BPMN/element templates as `{{secrets.NAME}}`.'
  echo
  echo "| NAME | Vault secret |"
  echo "|---|---|"
} > "$MANIFEST"

count=0
while IFS= read -r secret; do
  case "$secret" in */) continue ;; esac
  while IFS= read -r -d '' key && IFS= read -r -d '' value; do
    name="$(norm "$secret")_$(norm "$key")"
    case "$seen" in *" $name "*) echo "Name collision: $name. Rename a secret or key in Vault." >&2; exit 1 ;; esac
    seen="$seen$name "
    export "$PREFIX$name=$value"
    echo "| \`$name\` | $secret |" >> "$MANIFEST"
    count=$((count + 1))
  done < <(vault kv get -format=json -mount="$MOUNT" "$VAULT_PATH/$secret" \
             | jq -j '.data.data | to_entries[] | select(.value != "") | "\(.key)\u0000\(.value|tostring)\u0000"')
done < <(vault kv list -format=json -mount="$MOUNT" "$VAULT_PATH" | jq -r '.[]')

[ "$count" -gt 0 ] || { echo "No secrets found in $MOUNT/$VAULT_PATH" >&2; exit 1; }
echo "Loaded $count secrets, manifest: $MANIFEST" >&2
unset VAULT_TOKEN
echo "Starting LocalConnectorRuntime with these secrets now..."

if [ "$BUILD" = 1 ]; then
  mvn -q install -DskipTests -pl apps/bundle/default-bundle -am
fi

export CAMUNDA_CONNECTOR_SECRETPROVIDER_ENVIRONMENT_PREFIX="$PREFIX"
export CAMUNDA_CLIENT_MODE="${CAMUNDA_CLIENT_MODE:-self-managed}"
export CAMUNDA_CLIENT_REST_ADDRESS="${CAMUNDA_CLIENT_REST_ADDRESS:-http://localhost:8080}"
export CAMUNDA_CLIENT_GRPC_ADDRESS="${CAMUNDA_CLIENT_GRPC_ADDRESS:-http://localhost:26500}"
if [ -n "$DEBUG_PORT" ]; then
  export MAVEN_OPTS="${MAVEN_OPTS:-} -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:$DEBUG_PORT"
fi

exec mvn -pl apps/bundle/default-bundle test-compile org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -DskipTests -Dexec.mainClass=io.camunda.connector.runtime.app.ConnectorRuntimeApplication \
  -Dexec.classpathScope=test
