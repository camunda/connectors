import { inspect } from 'node:util';
import { readFile } from 'node:fs/promises';

const CONTRACT_VERSION = '1';
const MAX_MESSAGE_LENGTH = 1024;
const MAX_STACK_TRACE_LENGTH = 8192;

function writeLog(...values) {
  process.stderr.write(`${values.map((value) => (
    typeof value === 'string' ? value : inspect(value)
  )).join(' ')}\n`);
}

console.log = writeLog;
console.info = writeLog;
console.warn = writeLog;
console.error = writeLog;

function failure(code, error) {
  const message = error instanceof Error ? error.message : String(error);
  const stackTrace = error instanceof Error && error.stack ? error.stack : message;
  return {
    contractVersion: CONTRACT_VERSION,
    outcome: 'FAILED',
    error: {
      code,
      message: message.slice(0, MAX_MESSAGE_LENGTH),
      stackTrace: stackTrace.slice(0, MAX_STACK_TRACE_LENGTH),
      retryable: false,
      truncated: stackTrace.length > MAX_STACK_TRACE_LENGTH,
    },
  };
}

const request = JSON.parse(await readFile('request.json', 'utf8'));

let response;
let result;
try {
  if (request.contractVersion !== CONTRACT_VERSION) {
    throw new TypeError('unsupported or missing contractVersion');
  }
  if (request.variables === null
      || typeof request.variables !== 'object'
      || Array.isArray(request.variables)) {
    throw new TypeError("'variables' must be a JSON object");
  }
  if (request.context === null
      || typeof request.context !== 'object'
      || Array.isArray(request.context)) {
    throw new TypeError("'context' must be a JSON object");
  }
  const { execute } = await import('./user_script.mjs');
  result = await execute(request.variables, request.context);
} catch (error) {
  response = failure('SCRIPT_ERROR', error);
}

if (response === undefined) {
  try {
    if (result === null || typeof result !== 'object' || Array.isArray(result)) {
      throw new TypeError('execute() must return a JSON object');
    }
    response = {
      contractVersion: CONTRACT_VERSION,
      outcome: 'COMPLETED',
      variables: result,
    };
    JSON.stringify(response);
  } catch (error) {
    response = failure('INVALID_RESULT', error);
  }
}

process.stdout.write(JSON.stringify(response));
