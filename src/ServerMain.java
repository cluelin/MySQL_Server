import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import javax.swing.JOptionPane;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ServerMain implements Runnable {

	// Main Method Main Method Main Method Main Method Main Method Main Method
	// Main Method Main Method Main Method
	public static void main(String[] args) {

		Thread serverMain = new Thread(new ServerMain());
		serverMain.start();

	}

	private Connection getConnectWithSQL() {

		Connection mySQLconnection = null;

		try {
			// �̰� ���� �𸣰���.
			Class.forName(ServerInformation.JDBC_DRIVER);

			// mySQL�� ����.
			mySQLconnection = DriverManager.getConnection(ServerInformation.DB_URL, ServerInformation.USERNAME,
					ServerInformation.PASSWORD);

			System.out.println("MySQL : Connected");
			JOptionPane.showMessageDialog(null, "DB���� ����");
		} catch (SQLException e) {

			e.printStackTrace();

			JOptionPane.showMessageDialog(null, "DB���� ����");
			System.exit(0);

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}

		return mySQLconnection;
	}

	// Main Method Main Method Main Method Main Method Main Method Main Method
	// Main Method Main Method Main Method

	@Override
	public void run() {

		Connection mySQLconnection = getConnectWithSQL();

		try {

			// ��������.
			ServerSocket serverSocket = new ServerSocket(ServerInformation.SERVER_PORT);

			while (true) {

				System.out.println("Sever : waitting...");

				// ������������ �����ϴ� ���� �����ֱ�.
				Socket clientSocket = serverSocket.accept();
				System.out.println("Sever : connected");

				new Thread(new ServerThread(clientSocket, mySQLconnection)).start();

			}

		} catch (Exception e) {
			e.printStackTrace();
			
			JOptionPane.showMessageDialog(null, "�̹� ������ �������Դϴ�.");
		} finally {

			try {
				mySQLconnection.close();
				System.out.println("MySQL Server : connection close");
			} catch (SQLException e) {

				e.printStackTrace();
			}

		}
	}

}
