import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.print.attribute.standard.Severity;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ServerMain implements Runnable {

	// input/output information
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;
	private PrintStream printStream;

	// JSON
	private JSONParser jsonParser = new JSONParser();
	private JSONObject objFromClient;
	private JSONObject objToClient = new JSONObject();

	private Connection mySQLconnection = null;
	private Statement statement = null;
	private PreparedStatement pstmt = null;

	// Main Method Main Method Main Method Main Method Main Method Main Method
	// Main Method Main Method Main Method
	public static void main(String[] args) {

		Thread serverMain = new Thread(new ServerMain());
		serverMain.start();

	}
	// Main Method Main Method Main Method Main Method Main Method Main Method
	// Main Method Main Method Main Method

	@Override
	public void run() {

		try {

			// 서버소켓.
			ServerSocket serverSocket = new ServerSocket(ServerInformation.SERVER_PORT);

			while (true) {

				System.out.println("Sever : waitting...");

				// 서버소켓으로 접근하는 소켓 열어주기.
				Socket clientSocket = serverSocket.accept();
				System.out.println("Sever : connected");

				new Thread(new ServerThread(clientSocket)).start();
				
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}


}

