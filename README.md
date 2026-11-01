# MultiThreaded DataFrame System

A Java-based DataFrame implementation that performs CSV data processing using sequential and parallel execution. The project explores parallel computing using Java Parallel Streams and evaluates its impact on performance.

## Features

- CSV loading and exporting.
- Column-wise data storage.
- Sorting by numerical and textual columns.
- Filtering records based on conditions.
- Group By with SUM, AVG, MIN and MAX aggregation.
- Sequential and parallel execution modes.
- Execution time benchmarking.

## Tech Stack

- Java
- Java Collections Framework
- Java Streams and Parallel Streams
- Java I/O
- Multithreading and Parallel Computing

## How to Run

**1. Compile the project**

```bash
javac Main.java DataFrameInterface.java MultiThreadedDataFrame.java
```

**2. Run in Parallel Mode (Default)**

```bash
java Main
```

**3. Run in Sequential Mode**

```bash
java -Dparallel=false Main
```

Enter the path to your CSV dataset when prompted.

## Implementation Details

- Uses `LinkedHashMap` and `ArrayList` for column-wise data storage.
- Uses Java Streams for sequential processing and Parallel Streams for parallel execution.
- Implements sorting, filtering and grouping with aggregation.
- Uses `System.nanoTime()` to measure operation execution times.
- Supports switching between execution modes through a JVM system property without changing source code.

## Performance Evaluation

Benchmarked using a dataset containing **2 million records**, with four measured runs per operation. CSV loading time was excluded.

| Operation | Sequential (ms) | Parallel (ms) | Improvement |
|---|---:|---:|---:|
| Sorting | 2023.336 | 1852.056 | 8.47% |
| Filtering | 1487.304 | 1147.408 | 22.85% |
| Group By | 441.657 | 374.079 | 15.30% |
| **Overall** | **3952.297** | **3373.543** | **14.64%** |

### Observations

- Filtering showed the highest performance improvement.
- Parallel execution improved all three operations in this experiment.
- Performance gains depend on workload size and parallelization overhead.

## Conclusion

The project demonstrates how parallel computing can improve data processing performance. Comparing sequential and parallel execution highlights the benefits and limitations of parallelization across different DataFrame operations.
