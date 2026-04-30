Problem: Traditional Java DataFrames process data sequentially, causing inefficiencies with large datasets. Our project develops a custom data structure—a Multi-Threaded DataFrame— that uses parallel computing to accelerate sorting, filtering, and aggregation, improving scalability and performance.

Description:

Parallel Sorting — Fast column-based sorting using multithreading.
Parallel Filtering — High-performance filtering with lambda-based conditions.
Parallel GroupBy & Aggregation — Supports sum, avg, min, and max over groups.
CSV Load & Export — Reads and writes CSV files with automatic null handling.
Benchmarking — Tracks performance of each major operation (in ms).
This custom DataFrame structure improves scalability and speed, making it ideal for lightweight data analysis in Java environments.

Data Structures Used:

ArrayList – For storing column names and column-wise data (fast indexed access).
LinkedHashMap – For maintaining insertion order in columns and benchmarks.
HashMap – For internal row representation and group-by aggregations.
List – For row-level operations like filtering and sorting.
Map<String, String> – Represents individual rows for easy access by column name.
Core Logic and Implementation:

Parallel Sorting : Data is divided into smaller chunks and each chunk is sorted in a separate thread. The results are merged after all threads complete.
Parallel Filtering : Rows are split across threads and filtered using a custom condition. The filtered data is collected and returned as a new DataFrame.
GroupBy and Aggregation : Rows are grouped based on a column value. Aggregation functions like sum, avg, min, and max are applied to the grouped data.Multiple threads handle different groups in parallel
CSV Handling : Reads data line-by-line and stores it in memory. Automatically handles missing values.Supports exporting the final data back to a CSV file.
Benchmarking : Time is recorded before and after each operation. Execution time is printed in milliseconds for performance comparison.
