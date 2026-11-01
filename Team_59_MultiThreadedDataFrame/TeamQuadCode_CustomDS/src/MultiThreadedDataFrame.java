import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MultiThreadedDataFrame {

    // Switch modes without editing code:  java -Dparallel=false Main
    public static final boolean PARALLEL =
            Boolean.parseBoolean(System.getProperty("parallel", "true"));

    private List<String> columns = new ArrayList<>();               // column names, in CSV order
    private Map<String, List<String>> data = new LinkedHashMap<>(); // column-wise storage
    private int rowCount;
    private Map<String, Long> benchmarks = new LinkedHashMap<>();   // operation -> time in ms

    public MultiThreadedDataFrame(String csvFilePath) throws IOException {
        long start = System.nanoTime();
        loadCSV(csvFilePath);
        benchmarks.put("CSV Load", elapsedMs(start));
    }

    private MultiThreadedDataFrame() {
        // Used internally for filter results
    }

    // ---------- helpers ----------

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String label(String op) {
        return (PARALLEL ? "Parallel " : "Sequential ") + op;
    }

    private static <T> Stream<T> stream(Collection<T> c) {
        return PARALLEL ? c.parallelStream() : c.stream();
    }

    // ---------- loading ----------

    private void loadCSV(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line = br.readLine();
            if (line == null) return;

            // Read header (trimmed)
            columns = new ArrayList<>();
            for (String h : line.split(",", -1)) {
                String name = h.trim();
                columns.add(name);
                data.put(name, new ArrayList<>());
            }

            // Cache the column lists so we don't do a map lookup for every cell
            int colCount = columns.size();
            List<List<String>> colLists = new ArrayList<>();
            for (String name : columns) colLists.add(data.get(name));

            // Read rows. Limit -1 keeps trailing empty cells (e.g. "a,b,")
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length != colCount) continue; // skip malformed rows
                for (int i = 0; i < colCount; i++) {
                    String v = values[i].trim();
                    colLists.get(i).add(v.isEmpty() ? null : v); // null for empty cells
                }
                rowCount++;
            }
        }
    }

    // ---------- row view ----------

    private static class Row {
        final Map<String, String> values;
        Row(Map<String, String> values) { this.values = values; }
        String get(String column) { return values.get(column); }
    }

    // Columns -> rows (sequential; this is the main bottleneck)
    private List<Row> getRows() {
        List<Row> rows = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            Map<String, String> rowMap = new HashMap<>();
            for (String col : columns) {
                rowMap.put(col, data.get(col).get(i));
            }
            rows.add(new Row(rowMap));
        }
        return rows;
    }

    // Rows -> a new DataFrame (benchmarks are carried over)
    private MultiThreadedDataFrame fromRows(List<Row> rows) {
        MultiThreadedDataFrame newDF = new MultiThreadedDataFrame();
        newDF.columns = new ArrayList<>(this.columns);
        newDF.rowCount = rows.size();
        newDF.benchmarks = new LinkedHashMap<>(this.benchmarks); // FIX: don't lose benchmarks

        for (String col : columns) {
            newDF.data.put(col, new ArrayList<>(rows.size()));
        }
        for (Row row : rows) {
            for (String col : columns) {
                newDF.data.get(col).add(row.get(col));
            }
        }
        return newDF;
    }

    // ---------- sort ----------

    // True if every non-null value in the column parses as a number
    private boolean isNumericColumn(String col) {
        for (String v : data.get(col)) {
            if (v == null) continue;
            try {
                Double.parseDouble(v);
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public MultiThreadedDataFrame sortBy(String columnName, boolean ascending) {
        long start = System.nanoTime();

        List<Row> rows = getRows();

        Comparator<String> keyCmp;
        if (isNumericColumn(columnName)) {
            keyCmp = Comparator.comparingDouble(Double::parseDouble);
        } else {
            keyCmp = Comparator.naturalOrder();
        }
        if (!ascending) keyCmp = keyCmp.reversed();

        Comparator<Row> comparator =
                Comparator.comparing((Row r) -> r.get(columnName), Comparator.nullsLast(keyCmp));

        rows = stream(rows).sorted(comparator).collect(Collectors.toList());

        // Rebuild every column from the sorted rows
        for (String col : columns) {
            List<String> colData = new ArrayList<>(rows.size());
            for (Row row : rows) {
                colData.add(row.get(col));
            }
            data.put(col, colData);
        }

        benchmarks.put(label("Sort by " + columnName), elapsedMs(start));
        return this;
    }

    // ---------- filter ----------

    public MultiThreadedDataFrame filter(Predicate<Map<String, String>> condition) {
        long start = System.nanoTime();

        List<Row> rows = getRows();

        List<Row> filteredRows = stream(rows)
                .filter(row -> condition.test(row.values))
                .collect(Collectors.toList());

        benchmarks.put(label("Filter"), elapsedMs(start));
        System.out.println("Filter applied. Rows left: " + filteredRows.size());

        return fromRows(filteredRows);
    }

    // ---------- group by + aggregate ----------

    public Map<String, Double> groupByAggregate(String groupByCol, String aggCol, String operation) {
        // FIX: validate the operation up front, not inside the parallel stream
        String op = operation.toLowerCase().trim();
        if (!Set.of("sum", "avg", "min", "max").contains(op)) {
            throw new IllegalArgumentException("Unsupported operation: " + operation);
        }

        long start = System.nanoTime();

        List<String> groupValues = data.get(groupByCol);
        List<String> aggValues = data.get(aggCol);

        // Step 1 (sequential): put each value in its group's bucket
        Map<String, List<Double>> grouped = new HashMap<>();
        for (int i = 0; i < rowCount; i++) {
            String key = groupValues.get(i);
            String valStr = aggValues.get(i);
            if (valStr == null) continue;
            if (key == null) key = "NULL";
            try {
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(Double.parseDouble(valStr));
            } catch (NumberFormatException ignored) {
                // skip non-numeric values
            }
        }

        // Step 2 (parallel): aggregate each bucket.
        // FIX: collect into a map instead of writing to a shared map inside synchronized
        Map<String, Double> result = stream(grouped.entrySet())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> aggregate(e.getValue(), op),
                        (a, b) -> a,
                        LinkedHashMap::new));

        benchmarks.put(label("GroupBy " + groupByCol + " " + op.toUpperCase() + " of " + aggCol),
                elapsedMs(start));
        return result;
    }

    private static double aggregate(List<Double> values, String op) {
        switch (op) {
            case "sum": return values.stream().mapToDouble(Double::doubleValue).sum();
            case "avg": return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            case "max": return values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            case "min": return values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            default: throw new IllegalArgumentException("Unsupported operation: " + op);
        }
    }

    // ---------- export ----------

    public void exportToCSV(String outputPath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(String.join(",", columns));
            writer.newLine();

            for (int i = 0; i < rowCount; i++) {
                List<String> row = new ArrayList<>();
                for (String col : columns) {
                    String value = data.get(col).get(i);
                    if (value == null) {
                        value = ""; // FIX: empty cell, so re-loading the file gives null again
                    } else if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                        value = "\"" + value.replace("\"", "\"\"") + "\"";
                    }
                    row.add(value);
                }
                writer.write(String.join(",", row));
                writer.newLine();
            }
        }
        System.out.println("Data exported to " + outputPath);
    }

    // ---------- printing ----------

    public int getRowCount() {
        return rowCount;
    }

    public boolean columnExists(String columnName) {
        return columns.contains(columnName);
    }

    public void printBenchmarks() {
        System.out.println("\n--- Benchmark Results (ms) | mode: "
                + (PARALLEL ? "PARALLEL" : "SEQUENTIAL")
                + " | cores: " + Runtime.getRuntime().availableProcessors() + " ---");
        for (Map.Entry<String, Long> entry : benchmarks.entrySet()) {
            System.out.printf("%-60s : %d ms\n", entry.getKey(), entry.getValue());
        }
    }

    public void print() {
        System.out.println(String.join("\t", columns));
        for (int i = 0; i < rowCount; i++) {
            for (String col : columns) {
                String value = data.get(col).get(i);
                System.out.print((value == null ? "NULL" : value) + "\t");
            }
            System.out.println();
        }
    }

    public void printTopRows(int n) {
        int limit = Math.min(n, rowCount);
        if (limit == 0) {
            System.out.println("(no rows)");
            return;
        }

        // Determine column widths
        Map<String, Integer> columnWidths = new HashMap<>();
        for (String col : columns) {
            int maxLen = col.length();
            List<String> values = data.get(col);
            for (int i = 0; i < limit; i++) {
                String val = values.get(i);
                if (val == null) val = "NULL";
                maxLen = Math.max(maxLen, val.length());
            }
            columnWidths.put(col, maxLen);
        }

        // Print headers
        for (String col : columns) {
            System.out.print(padRight(col, columnWidths.get(col) + 2));
        }
        System.out.println();

        // Print rows
        for (int i = 0; i < limit; i++) {
            for (String col : columns) {
                String value = data.get(col).get(i);
                if (value == null) value = "NULL";
                System.out.print(padRight(value, columnWidths.get(col) + 2));
            }
            System.out.println();
        }
    }

    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }
}
