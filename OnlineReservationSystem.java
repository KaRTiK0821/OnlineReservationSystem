import java.sql.*;
import java.util.Scanner;

public class OnlineReservationSystem {
    private static Connection connection;
    private static Scanner scanner = new Scanner(System.in);
    private static String currentUser = null;

    public static void main(String[] args) {
        initializeDatabaseConnection();
        
        if (connection != null) {
            System.out.println("Welcome to Online Railway Reservation System");
            mainMenu();
        }
        
        closeResources();
    }

    private static void initializeDatabaseConnection() {
        try {
            // Load MySQL JDBC Driver
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // Establish connection with additional parameters
            connection = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/railway_reservation?" +
                "useSSL=false&" +
                "allowPublicKeyRetrieval=true&" +
                "autoReconnect=true&" +
                "serverTimezone=UTC",
                "root", 
                "root"
            );
            
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found!");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Database connection failed!");
            System.err.println("Error Code: " + e.getErrorCode());
            System.err.println("Message: " + e.getMessage());
            
            // Specific error guidance
            if (e.getErrorCode() == 1045) {
                System.err.println("Authentication failed - check username/password");
            } else if (e.getErrorCode() == 1049) {
                System.err.println("Database 'railway_reservation' doesn't exist");
            }
        }
    }

    private static void mainMenu() {
        while (true) {
            if (currentUser == null) {
                System.out.println("\n1. Login\n2. Exit");
                System.out.print("Enter your choice: ");
                
                try {
                    int choice = Integer.parseInt(scanner.nextLine());
                    
                    switch (choice) {
                        case 1:
                            login();
                            break;
                        case 2:
                            System.out.println("Thank you for using the system. Goodbye!");
                            return;
                        default:
                            System.out.println("Invalid choice. Please try again.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid number!");
                }
            } else {
                System.out.println("\n1. Make Reservation\n2. Cancel Reservation\n3. View Reservations\n4. Logout");
                System.out.print("Enter your choice: ");
                
                try {
                    int choice = Integer.parseInt(scanner.nextLine());
                    
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
                } catch (NumberFormatException e) {
                    System.out.println("Please enter a valid number!");
                } catch (SQLException e) {
                    System.err.println("Database error: " + e.getMessage());
                }
            }
        }
    }

    private static void login() throws SQLException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();
        
        String query = "SELECT user_id, full_name FROM users WHERE username = ? AND password = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, username);
            statement.setString(2, password);
            
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    currentUser = username;
                    System.out.println("Login successful! Welcome, " + result.getString("full_name"));
                } else {
                    System.out.println("Invalid username or password. Please try again.");
                }
            }
        }
    }

    private static void makeReservation() throws SQLException {
        // Display available trains
        displayAvailableTrains();
        
        System.out.print("\nEnter train number: ");
        String trainNumber = scanner.nextLine();
        
        // Verify train availability
        if (!isTrainAvailable(trainNumber)) {
            System.out.println("Invalid train number or no seats available.");
            return;
        }
        
        // Get reservation details
        ReservationDetails details = getReservationDetails();
        
        // Process reservation
        String pnr = processReservation(trainNumber, details);
        
        if (pnr != null) {
            System.out.println("Reservation successful!");
            System.out.println("Your PNR number is: " + pnr);
            updateTrainSeats(trainNumber, -1); // Decrease available seats
        } else {
            System.out.println("Reservation failed. Please try again.");
        }
    }

    private static void displayAvailableTrains() throws SQLException {
        System.out.println("\nAvailable Trains:");
        String query = "SELECT train_number, train_name, source_station, destination_station, available_seats " +
                      "FROM trains WHERE available_seats > 0";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            System.out.printf("%-10s %-30s %-20s %-20s %-10s\n", 
                "Number", "Name", "Source", "Destination", "Seats");
            
            while (rs.next()) {
                System.out.printf("%-10s %-30s %-20s %-20s %-10s\n", 
                    rs.getString("train_number"),
                    rs.getString("train_name"),
                    rs.getString("source_station"),
                    rs.getString("destination_station"),
                    rs.getInt("available_seats"));
            }
        }
    }

    private static boolean isTrainAvailable(String trainNumber) throws SQLException {
        String query = "SELECT 1 FROM trains WHERE train_number = ? AND available_seats > 0";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, trainNumber);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static ReservationDetails getReservationDetails() {
        ReservationDetails details = new ReservationDetails();
        
        System.out.print("Enter class type (Sleeper/AC/General): ");
        details.classType = scanner.nextLine();
        
        System.out.print("Enter journey date (YYYY-MM-DD): ");
        details.journeyDate = scanner.nextLine();
        
        System.out.print("Enter from station: ");
        details.fromStation = scanner.nextLine();
        
        System.out.print("Enter to station: ");
        details.toStation = scanner.nextLine();
        
        return details;
    }

    private static String processReservation(String trainNumber, ReservationDetails details) throws SQLException {
        // Get user ID
        int userId = getUserId(currentUser);
        if (userId == -1) return null;
        
        // Generate PNR
        String pnr = "PNR" + System.currentTimeMillis();
        
        // Insert reservation
        String query = "INSERT INTO reservations (pnr_number, user_id, train_number, " +
                      "class_type, journey_date, from_station, to_station) " +
                      "VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, pnr);
            stmt.setInt(2, userId);
            stmt.setString(3, trainNumber);
            stmt.setString(4, details.classType);
            stmt.setString(5, details.journeyDate);
            stmt.setString(6, details.fromStation);
            stmt.setString(7, details.toStation);
            
            return stmt.executeUpdate() > 0 ? pnr : null;
        }
    }

    private static int getUserId(String username) throws SQLException {
        String query = "SELECT user_id FROM users WHERE username = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("user_id") : -1;
            }
        }
    }

    private static void updateTrainSeats(String trainNumber, int change) throws SQLException {
        String query = "UPDATE trains SET available_seats = available_seats + ? WHERE train_number = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, change);
            stmt.setString(2, trainNumber);
            stmt.executeUpdate();
        }
    }

    private static void cancelReservation() throws SQLException {
        System.out.print("Enter your PNR number: ");
        String pnr = scanner.nextLine();
        
        // Get reservation details
        ReservationInfo info = getReservationInfo(pnr);
        
        if (info == null) {
            System.out.println("No reservation found with this PNR number or you don't have permission to cancel it.");
            return;
        }
        
        // Display reservation details
        displayReservationInfo(info);
        
        // Confirm cancellation
        System.out.print("\nDo you want to cancel this reservation? (yes/no): ");
        String confirmation = scanner.nextLine();
        
        if (confirmation.equalsIgnoreCase("yes")) {
            if (cancelReservation(pnr)) {
                updateTrainSeats(info.trainNumber, 1); // Increase available seats
                System.out.println("Reservation cancelled successfully.");
            } else {
                System.out.println("Cancellation failed. Please try again.");
            }
        } else {
            System.out.println("Cancellation aborted.");
        }
    }

    private static ReservationInfo getReservationInfo(String pnr) throws SQLException {
        String query = "SELECT r.*, t.train_name FROM reservations r " +
                       "JOIN trains t ON r.train_number = t.train_number " +
                       "WHERE pnr_number = ? AND r.user_id = (SELECT user_id FROM users WHERE username = ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, pnr);
            stmt.setString(2, currentUser);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ReservationInfo info = new ReservationInfo();
                    info.pnrNumber = rs.getString("pnr_number");
                    info.trainName = rs.getString("train_name");
                    info.trainNumber = rs.getString("train_number");
                    info.classType = rs.getString("class_type");
                    info.journeyDate = rs.getString("journey_date");
                    info.fromStation = rs.getString("from_station");
                    info.toStation = rs.getString("to_station");
                    info.status = rs.getString("status");
                    return info;
                }
            }
        }
        return null;
    }

    private static void displayReservationInfo(ReservationInfo info) {
        System.out.println("\nReservation Details:");
        System.out.println("PNR Number: " + info.pnrNumber);
        System.out.println("Train: " + info.trainName + " (" + info.trainNumber + ")");
        System.out.println("Class: " + info.classType);
        System.out.println("Journey Date: " + info.journeyDate);
        System.out.println("From: " + info.fromStation);
        System.out.println("To: " + info.toStation);
        System.out.println("Status: " + info.status);
    }

    private static boolean cancelReservation(String pnr) throws SQLException {
        String query = "UPDATE reservations SET status = 'Cancelled' WHERE pnr_number = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, pnr);
            return stmt.executeUpdate() > 0;
        }
    }

    private static void viewReservations() throws SQLException {
        String query = "SELECT r.*, t.train_name FROM reservations r " +
                       "JOIN trains t ON r.train_number = t.train_number " +
                       "WHERE r.user_id = (SELECT user_id FROM users WHERE username = ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, currentUser);
            
            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\nYour Reservations:");
                System.out.printf("%-15s %-20s %-10s %-15s %-12s %-15s %-15s %-10s\n", 
                    "PNR", "Train", "Number", "Class", "Journey Date", "From", "To", "Status");
                
                while (rs.next()) {
                    System.out.printf("%-15s %-20s %-10s %-15s %-12s %-15s %-15s %-10s\n", 
                        rs.getString("pnr_number"),
                        rs.getString("train_name"),
                        rs.getString("train_number"),
                        rs.getString("class_type"),
                        rs.getString("journey_date"),
                        rs.getString("from_station"),
                        rs.getString("to_station"),
                        rs.getString("status"));
                }
            }
        }
    }

    private static void closeResources() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }

    // Helper classes for data organization
    private static class ReservationDetails {
        String classType;
        String journeyDate;
        String fromStation;
        String toStation;
    }

    private static class ReservationInfo {
        String pnrNumber;
        String trainName;
        String trainNumber;
        String classType;
        String journeyDate;
        String fromStation;
        String toStation;
        String status;
    }
}