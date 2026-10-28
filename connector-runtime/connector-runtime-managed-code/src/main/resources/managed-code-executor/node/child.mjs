// Runs user_script.mjs from the work directory and writes the contract response to stdout.
//
// Started by executor_core in a fresh Node.js process for every call. Adapted from the local
// wrapper of POC 0.3, reading the request from stdin instead of request.json.

import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { inspect } from 'node:util';

const CONTRACT_VERSION = '1';
const MAX_MESSAGE_LENGTH = 1024;
const MAX_STACK_TRACE_LENGTH = 8192;

// console output in user code goes to the logs, not to the response.
function writeLog(...values) {
  process.stderr.write(`${values.map((value) => (typeof value === 'string' ? value : inspect(value))).join(' ')}\n`);
}
console.log = writeLog;
console.info = writeLog;
console.warn = writeLog;
console.error = writeLog;

function failure(code, error) {
  const message = error instanceof Error ? `${error.name}: ${error.message}` : String(error);
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

const isObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

let input = '';
for await (const chunk of process.stdin) input += chunk;
const request = JSON.parse(input);

let response;
let result;
try {
  if (request.contractVersion !== CONTRACT_VERSION) throw new TypeError('unsupported or missing contractVersion');
  if (!isObject(request.variables)) throw new TypeError("'variables' must be a JSON object");
  if (!isObject(request.context)) throw new TypeError("'context' must be a JSON object");
  const { execute } = await import(pathToFileURL(path.join(process.argv[2], 'user_script.mjs')).href);
  result = await execute(request.variables, request.context);
} catch (error) {
  response = failure('SCRIPT_ERROR', error);
}

if (response === undefined) {
  try {
    if (!isObject(result)) throw new TypeError('execute() must return a JSON object');
    response = { contractVersion: CONTRACT_VERSION, outcome: 'COMPLETED', variables: result };
    JSON.stringify(response);
  } catch (error) {
    response = failure('INVALID_RESULT', error);
  }
}

process.stdout.write(JSON.stringify(response));
