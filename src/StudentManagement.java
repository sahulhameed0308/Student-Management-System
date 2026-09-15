import java.sql.*;
import java.util.Scanner;

public class StudentManagement {

    static Scanner sc = new Scanner(System.in);

    // Teacher Login
    static boolean teacherLogin() {


        System.out.println("TEACHER LOGIN");

        System.out.print("Enter Username : ");
        String username = sc.next();

        System.out.print("Enter Password : ");
        String password = sc.next();

        if (username.equals("Thalapathy") && password.equals("TVK")) {
            System.out.println("\nLogin Successful!\n");
            return true;
        } else {
            System.out.println("\nInvalid Username or Password!");
            return false;
        }
    }

    public static void main(String[] args) {

        while (true) {

            if (!teacherLogin()) {
                return;
            }

            int choice;

            do {


                System.out.println("     STUDENT MANAGEMENT SYSTEM");
                System.out.println("1. Add Student");
                System.out.println("2. View Students");
                System.out.println("3. Search Student");
                System.out.println("4. Update Student");
                System.out.println("5. Delete Student");
                System.out.println("6. Logout");
                System.out.println("7. Exit");
                System.out.print("Enter Choice : ");

                choice = sc.nextInt();

                switch (choice) {

                    case 1:
                        addStudent();
                        break;

                    case 2:
                        viewStudents();
                        break;

                    case 3:
                        searchStudent();
                        break;

                    case 4:
                        updateStudent();
                        break;

                    case 5:
                        deleteStudent();
                        break;

                    case 6:
                        System.out.println("\nLogged Out Successfully!");
                        break;

                    case 7:
                        System.out.println("\nThank You!");
                        System.exit(0);

                    default:
                        System.out.println("Invalid Choice");
                }

            } while (choice != 6);
        }
    }

    // Add Student
    static void addStudent() {

        try {

            Connection con = DBConnection.getConnection();

            sc.nextLine();

            System.out.print("Enter Student Name : ");
            String name = sc.nextLine();

            System.out.print("Enter Student Email : ");
            String email = sc.nextLine();

            System.out.print("Enter Course : ");
            String course = sc.nextLine();

            String query = "INSERT INTO students(st_name,email_id,course) VALUES(?,?,?)";

            PreparedStatement ps = con.prepareStatement(query);

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, course);

            int result = ps.executeUpdate();

            if(result>0)
                System.out.println("Student Added Successfully");
            else
                System.out.println("Failed!");

            con.close();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // View Students
    static void viewStudents() {

        try {

            Connection con = DBConnection.getConnection();

            String query = "SELECT * FROM students";

            Statement st = con.createStatement();

            ResultSet rs = st.executeQuery(query);


            System.out.println("ID\tName\tEmail\t\t\tCourse");


            while (rs.next()) {

                System.out.println(
                        rs.getInt("id") + "\t" +
                                rs.getString("st_name") + "\t" +
                                rs.getString("email_id") + "\t" +
                                rs.getString("course")
                );
            }

            con.close();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // Search Student
    static void searchStudent() {

        try {

            Connection con = DBConnection.getConnection();

            System.out.print("Enter Student ID : ");
            int id = sc.nextInt();

            String query = "SELECT * FROM students WHERE id=?";

            PreparedStatement ps = con.prepareStatement(query);

            ps.setInt(1, id);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {

                System.out.println("\nStudent Found");
                System.out.println("-----------------------");
                System.out.println("ID     : " + rs.getInt("id"));
                System.out.println("Name   : " + rs.getString("st_name"));
                System.out.println("Email  : " + rs.getString("email_id"));
                System.out.println("Course : " + rs.getString("course"));

            } else {

                System.out.println("Student Not Found");
            }

            con.close();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // Update Student
    static void updateStudent() {

        try {

            Connection con = DBConnection.getConnection();

            System.out.print("Enter Student ID : ");
            int id = sc.nextInt();

            sc.nextLine();

            System.out.print("Enter New Name : ");
            String name = sc.nextLine();

            System.out.print("Enter New Email : ");
            String email = sc.nextLine();

            System.out.print("Enter New Course : ");
            String course = sc.nextLine();

            String query = "UPDATE students SET st_name=?,email_id=?,course=? WHERE id=?";

            PreparedStatement ps = con.prepareStatement(query);

            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, course);
            ps.setInt(4, id);

            int result = ps.executeUpdate();

            if(result>0)
                System.out.println("Student Updated Successfully");
            else
                System.out.println("Student Not Found");

            con.close();

        } catch (Exception e) {
            System.out.println(e);
        }
    }

    // Delete Student
    static void deleteStudent() {

        try {

            Connection con = DBConnection.getConnection();

            System.out.print("Enter Student ID : ");
            int id = sc.nextInt();

            String query = "DELETE FROM students WHERE id=?";

            PreparedStatement ps = con.prepareStatement(query);

            ps.setInt(1, id);

            int result = ps.executeUpdate();

            if(result>0)
                System.out.println("Student Deleted Successfully");
            else
                System.out.println("Student Not Found");

            con.close();

        } catch (Exception e) {
            System.out.println(e);
        }
    }
}