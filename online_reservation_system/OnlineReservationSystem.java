import java.sql.*;
import java.util.Scanner;
import java.text.SimpleDateFormat;
import java.util.Date;

public class OnlineReservationSystem {
    private static Connection connection;
    private static Scanner scanner = new Scanner(System.in);
    private static String currentUser = null;

    public static void main(String[] args) {
        try {
            // Connect to database
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/railway_reservation?useSSL=false&allowPublicKeyRetrieval=true", 
                "root", 
                "root" // replace with your database password
            );
            
            System.out.println("Welcome to Online Railway Reservation System");
            
            while (true) {
                if (currentUser == null) {
                    System.out.println("\n1. Login\n2. Exit");
                    System.out.print("Enter your choice: ");
                    int choice = scanner.nextInt();
                    scanner.nextLine(); // consume newline
                    
                    switch (choice) {
                        case 1:
                            login();
                            break;
                        case 2:
                            System.out.println("Thank you for using the system. Goodbye!");
                            connection.close();
                            return;
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                } else {
                    System.out.println("\n1. Make Reservation\n2. Cancel Reservation\n3. View Reservations\n4. Logout");
                    System.out.print("Enter your choice: ");
                    int choice = scanner.nextInt();
                    scanner.nextLine(); // consume newline
                    
                    switch (choice) {
                        case 1:
                            makeReservation();
                            break;
                        case 2:
                            cancelReservation();
                            break;
                        case 3:
                            viewReservations();
                            break;
                        case 4:
                            currentUser = null;
                            System.out.println("Logged out successfully.");
                            break;
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void login() throws SQLException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        statement.setString(2, password);
        
        ResultSet result = statement.executeQuery();
        if (result.next()) {
            currentUser = username;
            System.out.println("Login successful! Welcome, " + result.getString("full_name"));
        } else {
            System.out.println("Invalid username or password. Please try again.");
        }
    }
    
    private static void makeReservation() throws SQLException {
        // Display available trains
        System.out.println("\nAvailable Trains:");
        String trainQuery = "SELECT * FROM trains WHERE available_seats > 0";
        Statement trainStatement = connection.createStatement();
        ResultSet trains = trainStatement.executeQuery(trainQuery);
        
        System.out.printf("%-10s %-30s %-20s %-20s %-10s\n", 
            "Number", "Name", "Source", "Destination", "Seats");
        while (trains.next()) {
            System.out.printf("%-10s %-30s %-20s %-20s %-10s\n", 
                trains.getString("train_number"),
                trains.getString("train_name"),
                trains.getString("source_station"),
                trains.getString("destination_station"),
                trains.getInt("available_seats"));
        }
        
        System.out.print("\nEnter train number: ");
        String trainNumber = scanner.nextLine();
        
        // Verify train exists and has available seats
        String verifyTrain = "SELECT * FROM trains WHERE train_number = ? AND available_seats > 0";
        PreparedStatement verifyStatement = connection.prepareStatement(verifyTrain);
        verifyStatement.setString(1, trainNumber);
        ResultSet trainResult = verifyStatement.executeQuery();
        
        if (!trainResult.next()) {
            System.out.println("Invalid train number or no seats available.");
            return;
        }
        
        // Get user details
        String userQuery = "SELECT user_id FROM users WHERE username = ?";
        PreparedStatement userStatement = connection.prepareStatement(userQuery);
        userStatement.setString(1, currentUser);
        ResultSet userResult = userStatement.executeQuery();
        userResult.next();
        int userId = userResult.getInt("user_id");
        
        // Get reservation details
        System.out.print("Enter class type (Sleeper/AC/General): ");
        String classType = scanner.nextLine();
        
        System.out.print("Enter journey date (YYYY-MM-DD): ");
        String journeyDate = scanner.nextLine();
        
        System.out.print("Enter from station: ");
        String fromStation = scanner.nextLine();
        
        System.out.print("Enter to station: ");
        String toStation = scanner.nextLine();
        
        // Generate PNR
        String pnr = "PNR" + System.currentTimeMillis();
        
        // Insert reservation
        String insertQuery = "INSERT INTO reservations (pnr_number, user_id, train_number, " +
                            "class_type, journey_date, from_station, to_station) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement insertStatement = connection.prepareStatement(insertQuery);
        insertStatement.setString(1, pnr);
        insertStatement.setInt(2, userId);
        insertStatement.setString(3, trainNumber);
        insertStatement.setString(4, classType);
        insertStatement.setString(5, journeyDate);
        insertStatement.setString(6, fromStation);
        insertStatement.setString(7, toStation);
        
        int rowsAffected = insertStatement.executeUpdate();
        
        if (rowsAffected > 0) {
            // Update available seats
            String updateSeats = "UPDATE trains SET available_seats = available_seats - 1 WHERE train_number = ?";
            PreparedStatement updateStatement = connection.prepareStatement(updateSeats);
            updateStatement.setString(1, trainNumber);
            updateStatement.executeUpdate();
            
            System.out.println("Reservation successful!");
            System.out.println("Your PNR number is: " + pnr);
        } else {
            System.out.println("Reservation failed. Please try again.");
        }
    }
    
    private static void cancelReservation() throws SQLException {
        System.out.print("Enter your PNR number: ");
        String pnr = scanner.nextLine();
        
        // Get reservation details
        String query = "SELECT r.*, t.train_name FROM reservations r " +
                       "JOIN trains t ON r.train_number = t.train_number " +
                       "WHERE pnr_number = ? AND r.user_id = (SELECT user_id FROM users WHERE username = ?)";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, pnr);
        statement.setString(2, currentUser);
        
        ResultSet result = statement.executeQuery();
        
        if (result.next()) {
            System.out.println("\nReservation Details:");
            System.out.println("PNR Number: " + result.getString("pnr_number"));
            System.out.println("Train: " + result.getString("train_name") + " (" + result.getString("train_number") + ")");
            System.out.println("Class: " + result.getString("class_type"));
            System.out.println("Journey Date: " + result.getString("journey_date"));
            System.out.println("From: " + result.getString("from_station"));
            System.out.println("To: " + result.getString("to_station"));
            System.out.println("Status: " + result.getString("status"));
            
            System.out.print("\nDo you want to cancel this reservation? (yes/no): ");
            String confirmation = scanner.nextLine();
            
            if (confirmation.equalsIgnoreCase("yes")) {
                // Cancel reservation
                String cancelQuery = "UPDATE reservations SET status = 'Cancelled' WHERE pnr_number = ?";
                PreparedStatement cancelStatement = connection.prepareStatement(cancelQuery);
                cancelStatement.setString(1, pnr);
                int rowsAffected = cancelStatement.executeUpdate();
                
                if (rowsAffected > 0) {
                    // Update available seats
                    String updateSeats = "UPDATE trains SET available_seats = available_seats + 1 " +
                                       "WHERE train_number = ?";
                    PreparedStatement updateStatement = connection.prepareStatement(updateSeats);
                    updateStatement.setString(1, result.getString("train_number"));
                    updateStatement.executeUpdate();
                    
                    System.out.println("Reservation cancelled successfully.");
                } else {
                    System.out.println("Cancellation failed. Please try again.");
                }
            } else {
                System.out.println("Cancellation aborted.");
            }
        } else {
            System.out.println("No reservation found with this PNR number or you don't have permission to cancel it.");
        }
    }
    
    private static void viewReservations() throws SQLException {
        String query = "SELECT r.*, t.train_name FROM reservations r " +
                       "JOIN trains t ON r.train_number = t.train_number " +
                       "WHERE r.user_id = (SELECT user_id FROM users WHERE username = ?)";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, currentUser);
        
        ResultSet result = statement.executeQuery();
        
        System.out.println("\nYour Reservations:");
        System.out.printf("%-15s %-20s %-10s %-15s %-12s %-15s %-15s %-10s\n", 
            "PNR", "Train", "Number", "Class", "Journey Date", "From", "To", "Status");
        
        while (result.next()) {
            System.out.printf("%-15s %-20s %-10s %-15s %-12s %-15s %-15s %-10s\n", 
                result.getString("pnr_number"),
                result.getString("train_name"),
                result.getString("train_number"),
                result.getString("class_type"),
                result.getString("journey_date"),
                result.getString("from_station"),
                result.getString("to_station"),
                result.getString("status"));
        }
    }
}