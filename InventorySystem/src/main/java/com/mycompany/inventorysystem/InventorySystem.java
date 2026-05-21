
// The Main Application Management System
package com.mycompany.inventorysystem; 

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Scanner;

public class InventorySystem {
    private static final String DB_URL = "jdbc:sqlite:/Users/ridahasan/Downloads/amazon.db";
    private static final String DB_USER = ""; 
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("--- Amazon Reseller SQL Inventory System ---");

        while (running) {
            System.out.println("\n1. Add a Product to Database");
            System.out.println("2. View Live Inventory from SQL");
            System.out.println("3. Delete a Product from Database");
            System.out.println("4. Log a New Sale (Deduct Stock)"); // New Option
            System.out.println("5. Exit");
            System.out.print("Choose an option: ");
            
            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            if (choice == 1) {
                System.out.print("Enter Product Name: ");
                String name = scanner.nextLine();
                System.out.print("Enter UPC (Barcode): ");
                String upc = scanner.nextLine();
                System.out.print("Enter Quantity: ");
                int quantity = scanner.nextInt();
                System.out.print("Enter Listed Price: ");
                double price = scanner.nextDouble();
                
                addProductToDatabase(name, upc, quantity, price);

            } else if (choice == 2) {
                viewInventoryFromDatabase();

            } else if (choice == 3) {
                System.out.print("\nEnter the Product ID (1, 2, 3...) you want to delete: ");
                int idToDelete = scanner.nextInt();
                scanner.nextLine(); 
                
                System.out.print("Are you absolutely sure you want to delete this item? (yes/no): ");
                String confirmation = scanner.nextLine().trim().toLowerCase();
                
                if (confirmation.equals("yes")) {
                    deleteProductFromDatabase(idToDelete);
                } else {
                    System.out.println("❌ Deletion canceled.");
                }

            } else if (choice == 4) {
                // New Sales Input Logic
                System.out.print("\nEnter the Product ID that was sold: ");
                int soldId = scanner.nextInt();
                System.out.print("Enter Quantity Sold: ");
                int qtySold = scanner.nextInt();
                System.out.print("Enter Total Gross Amount Customer Paid: ");
                double soldPrice = scanner.nextDouble();

                logSaleToDatabase(soldId, qtySold, soldPrice);

            } else if (choice == 5) {
                running = false;
                System.out.println("Exiting system.");
            } else {
                System.out.println("⚠️ Invalid choice. Please choose 1-5.");
            }
        }
        scanner.close();
    }

    // --- PHASE 2: THE SQL CONNECTIONS ---

    // Method that inserts a new row into SQL table
    public static void addProductToDatabase(String name, String upc, int quantity, double price) {
        String insertSQL = "INSERT INTO products (product_name, upc, quantity, price) VALUES (?, ?, ?, ?)";

        // Try-with-resources automatically closes the connection when done
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            
            // Map the Java variables to the SQL '?' placeholders
            pstmt.setString(1, name);
            pstmt.setString(2, upc);
            pstmt.setInt(3, quantity);
            float roundedPrice = Math.round(price * 100.0f) / 100.0f;
            pstmt.setFloat(4, roundedPrice);
            // pstmt.setDouble(4, (float) price);

            pstmt.executeUpdate(); // Run the SQL command
            System.out.println("✅ Product saved permanently to SQL Database!");

        } catch (SQLException e) {
            System.out.println("❌ Database Error: " + e.getMessage());
        }
    }

    // Method that selects all rows and displays them
    public static void viewInventoryFromDatabase() {
        String querySQL = "SELECT product_name, upc, quantity, price FROM products";

        System.out.println("\n--- Current SQL Database Inventory ---");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(querySQL)) {

            double grandTotalValue = 0;
            boolean hasData = false;

            // Loop through the rows returned by SQL
            while (rs.next()) {
                hasData = true;
                String name = rs.getString("product_name");
                String upc = rs.getString("upc");
                int quantity = rs.getInt("quantity");
                double price = rs.getFloat("price");
                double totalValue = quantity * price;
                grandTotalValue += totalValue;

                System.out.printf("Product: %s | UPC: %s | Price: $%.2f | Qty: %d\n", name, upc, price, quantity);
            }

            if (!hasData) {
                System.out.println("No products found in the database.");
            } else {
                System.out.printf("\n📈 Total Portfolio Inventory Value: $%.2f\n", grandTotalValue);
            }

        } catch (SQLException e) {
            System.out.println("❌ Database Error: " + e.getMessage());
        }
    }
    
    private static void deleteProductFromDatabase(int productId) {
    // The ? is a placeholder for the ID the user wants to delete
    String sql = "DELETE FROM products WHERE id = ?";

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
         PreparedStatement pstmt = conn.prepareStatement(sql)) {

        // Bind the ID to the SQL statement
        pstmt.setInt(1, productId);
        
        // executeUpdate returns how many rows were actually affected
        int rowsDeleted = pstmt.executeUpdate();

        if (rowsDeleted > 0) {
            System.out.println("✅ Product ID " + productId + " was successfully deleted from the database!");
        } else {
            System.out.println("❌ Error: Product ID " + productId + " was not found.");
        }

    } catch (SQLException e) {
        System.out.println("⚠️ Database error while trying to delete: " + e.getMessage());
    }
}
    
    private static void logSaleToDatabase(int productId, int qtySold, double soldPrice) {
    // Calculate an estimated Amazon fee (15% referral fee is standard)
    double amazonFees = Math.round((soldPrice * 0.15) * 100.0) / 100.0;
    
    // SQL to insert into our new transaction log table
    String insertSaleSql = "INSERT INTO sales_orders (product_id, quantity_sold, sold_price, amazon_fees) VALUES (?, ?, ?, ?)";
    
    // SQL to update our inventory table by subtracting the quantity sold
    String updateInventorySql = "UPDATE products SET quantity = quantity - ? WHERE id = ?";

    try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
        // Turn off Auto-Commit so BOTH queries must succeed together 
        conn.setAutoCommit(false);

        try (PreparedStatement insertStmt = conn.prepareStatement(insertSaleSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateInventorySql)) {
            
            // Log the sale details
            insertStmt.setInt(1, productId);
            insertStmt.setInt(2, qtySold);
            insertStmt.setDouble(3, soldPrice);
            insertStmt.setDouble(4, amazonFees);
            insertStmt.executeUpdate();

            // Deduct the stock
            updateStmt.setInt(1, qtySold);
            updateStmt.setInt(2, productId);
            updateStmt.executeUpdate();

            // Commit the transaction permanently to the database
            conn.commit();
            System.out.println("💰 Sale logged successfully! Active stock deducted.");
            System.out.printf("   Amazon Referral Fee (15%%) Deducted: $%.2f\n", amazonFees);

        } catch (SQLException e) {
            conn.rollback(); // If anything fails, it will undo changes to keep data safe
            throw e;
        }
    } catch (SQLException e) {
        System.out.println("⚠️ Error processing sale transaction: " + e.getMessage());
    }
} 
}


// System.out.printf("Product: %s | UPC: %s | Qty: %d | Price: $%.2f | Value: $%.2f\n", 
                        // name, upc, quantity, price, totalValue);