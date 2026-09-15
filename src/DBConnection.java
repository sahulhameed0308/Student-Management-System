import java.sql.*;

public class DBConnection {

    public static void main(String[] args) {

        Connection con = getConnection();

        if (con != null) {
            System.out.println("Database Connected Successfully");
        }
    }

    public static Connection getConnection() {

        Connection con = null;

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/student_db",
                    "root",
                    "Sahul0308"
            );

        } catch (Exception e) {
            System.out.println(e);
        }

        return con;
    }
}