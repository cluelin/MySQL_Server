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
			// 이건 뭔지 모르겠음.
			Class.forName(ServerInformation.JDBC_DRIVER);

			// mySQL과 접속.
			mySQLconnection = DriverManager.getConnection(ServerInformation.DB_URL, ServerInformation.USERNAME,
					ServerInformation.PASSWORD);

			System.out.println("MySQL : Connected");
			JOptionPane.showMessageDialog(null, "DB연결 성공");
		} catch (SQLException e) {

			e.printStackTrace();

			JOptionPane.showMessageDialog(null, "DB연결 실패");
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

			// 서버소켓.
			ServerSocket serverSocket = new ServerSocket(ServerInformation.SERVER_PORT);

			while (true) {

				System.out.println("Sever : waitting...");

				// 서버소켓으로 접근하는 소켓 열어주기.
				Socket clientSocket = serverSocket.accept();
				System.out.println("Sever : connected");

				new Thread(new ServerThread(clientSocket, mySQLconnection)).start();

			}

		} catch (Exception e) {
			e.printStackTrace();
			
			JOptionPane.showMessageDialog(null, "이미 서버가 동작중입니다.");
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
