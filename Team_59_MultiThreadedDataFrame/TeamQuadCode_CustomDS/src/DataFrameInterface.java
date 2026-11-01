import java.util.*;
import java.io.*;

public class DataFrameInterface {
    private MultiThreadedDataFrame df;
    private Scanner scanner = new Scanner(System.in);

    public void start() {
        try {
            System.out.print("Enter path to CSV file: ");
            String path = scanner.nextLine().trim();
            df = new MultiThreadedDataFrame(path);
            System.out.println("\nCSV Loaded. Rows: " + df.getRowCount());
            df.printBenchmarks();

            boolean running = true;
            while (running) {
                showMenu();
                int choice = getValidChoice();

                System.out.println("\n----------------------------------");

                switch (choice) {
                    case 1 -> {
                        df.printTopRows(6);
                        autoExport();
                        waitForEnter();
                    }
                    case 2 -> {
                        handleSort();
                        autoExport();
                        waitForEnter();
                    }
                    case 3 -> {
                        handleFilter();
                        autoExport();
                        waitForEnter();
                    }
                    case 4 -> {
                        handleGroupBy();
                        waitForEnter();
                    }
                    case 0 -> {
                        System.out.println("\nExiting... Goodbye!");
                        running = false;
                    }
                    default -> System.out.println("Invalid choice. Please enter a valid option.");
                }

                System.out.println("----------------------------------\n");
            }
        } catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void showMenu() {
        System.out.println("""
            ==================================
            DataFrame Interface Menu
            ==================================
            1. Print top 6 rows
            2. Sort by column
            3. Filter rows
            4. Group by + Aggregate
            0. Exit
        """);
        System.out.print("Choose an option: ");
    }

    private int getValidChoice() {
        while (true) {
            try {
                int choice = Integer.parseInt(scanner.nextLine().trim());
                if (choice >= 0 && choice <= 4) {
                    return choice;
                } else {
                    System.out.println("Invalid choice. Please enter a number between 0 and 4.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }

    private void handleSort() {
        System.out.print("Enter the column to sort by: ");
        String col = scanner.nextLine().trim();

        if (!df.columnExists(col)) {
            System.out.println("Error: Column '" + col + "' does not exist.");
            return;
        }

        boolean asc = getValidBoolean("Ascending? (true/false): ");
        df.sortBy(col, asc);
        System.out.println("Data sorted by column: " + col + (asc ? " in ascending order." : " in descending order."));
        df.printBenchmarks();
    }

    private void handleFilter() {
        System.out.print("Enter the column to filter by: ");
        String col = scanner.nextLine().trim();

        if (!df.columnExists(col)) {
            System.out.println("Error: Column '" + col + "' does not exist.");
            return;
        }

        System.out.print("Enter the minimum value for filtering: ");
        double threshold = getValidDoubleInput();

        // FIX: null / non-numeric cells are simply excluded instead of crashing the program
        df = df.filter(row -> {
            String v = row.get(col);
            if (v == null) return false;
            try {
                return Double.parseDouble(v) > threshold;
            } catch (NumberFormatException e) {
                return false;
            }
        });
        System.out.println("Rows filtered where '" + col + "' > " + threshold);
        df.printBenchmarks(); // FIX: benchmarks are now shown after a filter
    }

    private void handleGroupBy() {
        System.out.print("Enter the column to group by: ");
        String groupCol = scanner.nextLine().trim();

        if (!df.columnExists(groupCol)) {
            System.out.println("Error: Column '" + groupCol + "' does not exist.");
            return;
        }

        System.out.print("Enter the column to aggregate: ");
        String aggCol = scanner.nextLine().trim();

        if (!df.columnExists(aggCol)) {
            System.out.println("Error: Column '" + aggCol + "' does not exist.");
            return;
        }

        System.out.print("Enter the aggregation operation (sum/avg/min/max): ");
        String op = scanner.nextLine().trim();

        try {
            Map<String, Double> result = df.groupByAggregate(groupCol, aggCol, op);
            if (result.isEmpty()) {
                System.out.println("No data to display for the selected aggregation.");
            } else {
                System.out.println("\nGrouped Data:");
                result.forEach((k, v) -> System.out.printf("%s => %.2f\n", k, v));
            }
            df.printBenchmarks();
        } catch (IllegalArgumentException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private double getValidDoubleInput() {
        while (true) {
            try {
                return Double.parseDouble(scanner.nextLine().trim());
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a valid number.");
            }
        }
    }

    private boolean getValidBoolean(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = scanner.nextLine().trim().toLowerCase();
            if (s.equals("true") || s.equals("t") || s.equals("y")) return true;
            if (s.equals("false") || s.equals("f") || s.equals("n")) return false;
            System.out.println("Please enter true or false.");
        }
    }

    private void autoExport() {
        try {
            df.exportToCSV("processed.csv"); // FIX: no dependency on a src/ folder existing
            System.out.println("Auto-exported to: processed.csv");
        } catch (IOException e) {
            System.out.println("Auto-export failed: " + e.getMessage());
        }
    }

    private void waitForEnter() {
        System.out.print("\nPress Enter to continue...");
        scanner.nextLine();

        // Simulate screen clear with newlines
        for (int i = 0; i < 40; i++) System.out.println();
    }
}
