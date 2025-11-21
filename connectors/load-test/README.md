# Load Test Connector

The Load Test Connector is designed specifically for load testing Camunda Platform 8. It allows you to simulate realistic workloads by controlling CPU usage and I/O wait times.

## Features

### Operation 1: Load Test (Specific Duration)
Execute precise load testing with specific durations for I/O wait and CPU burn operations.

**Parameters:**
- **I/O wait (ms)**: Duration in milliseconds to wait (simulates I/O operations like database calls, API requests). Uses `Thread.sleep()` and relies on virtual threads for efficiency.
- **CPU burn (ms)**: Duration in milliseconds to perform CPU-intensive computations (uses non-trivial operations to prevent JIT optimization).

### Operation 2: Random Load Test (Random Within Bounds)
Execute load testing with randomized durations within specified bounds, useful for simulating variable workloads.

**Parameters:**
- **Min I/O wait (ms)**: Minimum duration for I/O wait
- **Max I/O wait (ms)**: Maximum duration for I/O wait
- **Min CPU burn (ms)**: Minimum duration for CPU burn
- **Max CPU burn (ms)**: Maximum duration for CPU burn

## Response

Both operations return a `LoadTestResult` object with the following fields:

```json
{
  "requestedIoWaitMs": 50,
  "requestedCpuBurnMs": 100,
  "actualIoWaitMs": 51,
  "actualCpuBurnMs": 102,
  "accumulator": 1234567890
}
```

- `requestedIoWaitMs`: The requested I/O wait duration
- `requestedCpuBurnMs`: The requested CPU burn duration
- `actualIoWaitMs`: The actual measured I/O wait duration
- `actualCpuBurnMs`: The actual measured CPU burn duration
- `accumulator`: Result of CPU computations (prevents dead-code elimination)

## Constraints

- Maximum duration for any operation: **10,000 ms** (10 seconds)
- Minimum duration: **0 ms**
- Values exceeding the maximum are automatically capped at 10,000 ms

## Usage Examples

### Example 1: Fixed Duration Load Test
Simulate a service call with 30ms I/O wait and 50ms CPU processing:

```json
{
  "ioWaitMs": 30,
  "cpuBurnMs": 50
}
```

### Example 2: I/O Only
Simulate pure I/O operations (e.g., waiting for external API):

```json
{
  "ioWaitMs": 100,
  "cpuBurnMs": 0
}
```

### Example 3: CPU Only
Simulate pure CPU-intensive work:

```json
{
  "ioWaitMs": 0,
  "cpuBurnMs": 200
}
```

### Example 4: Random Load Test
Simulate variable workload patterns:

```json
{
  "minIoWaitMs": 10,
  "maxIoWaitMs": 100,
  "minCpuBurnMs": 20,
  "maxCpuBurnMs": 200
}
```

## Implementation Details

### I/O Wait
Uses `Thread.sleep()` to simulate I/O wait. This is efficient with virtual threads and doesn't consume CPU resources.

### CPU Burn
Performs actual CPU-intensive computations using:
- XORShift-like pseudo-random number generation
- Arithmetic operations (multiplication, addition)
- Bitwise operations (XOR, shift)
- Mathematical functions (modulo, absolute value)

These operations are designed to be non-trivial enough that JIT compiler cannot optimize them away, ensuring actual CPU consumption.

## Use Cases

1. **Load Testing**: Simulate realistic process workloads with controlled CPU and I/O characteristics
2. **Performance Testing**: Test Camunda Platform 8 performance under various workload patterns
3. **Capacity Planning**: Determine system capacity by running multiple process instances with known resource requirements
4. **Benchmarking**: Compare performance across different deployment configurations
5. **Virtual Thread Testing**: Verify virtual thread behavior with I/O-heavy vs CPU-heavy workloads

## Notes

- Both I/O wait and CPU burn parameters are optional—you can specify either one or both
- The connector measures and reports actual execution times, which may slightly exceed requested times
- The execution order is: I/O wait first, then CPU burn
- The accumulator value ensures CPU work isn't optimized away and can be used to verify work was actually performed

