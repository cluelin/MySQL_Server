import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ServerMain implements Runnable {

	// ������ �ڱ� port number�� �˰������� ��.
	final public static int SERVER_PORT = 9090;

	// mySQL����ϱ����� �غ�.
	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static final String DB_URL = "jdbc:mysql://localhost:3306/rma";

	// mySQL ����� �α���
	static final String USERNAME = "root";
	static final String PASSWORD = "111111";

	Connection mySQLconnection = null;
	Statement statement = null;
	PreparedStatement pstmt = null;
	BufferedReader bufferedReader;

	@Override
	public void run() {

		try {

			// ��������.
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);

			while (true) {

				System.out.println("Sever : waitting...");

				// ������������ �����ϴ� ���� �����ֱ�.
				Socket client = serverSocket.accept();
				System.out.println("Sever : connected");

				// �̰� ���� �𸣰���.
				Class.forName(JDBC_DRIVER);
				// mySQL�� ����.
				mySQLconnection = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);

				System.out.println("MySQL : Connected");

				try {

					// Ŭ���̾�Ʈ�κ��� �о���� ���� �غ�.
					bufferedReader = new BufferedReader(new InputStreamReader(client.getInputStream()));

					JSONParser jsonParser = new JSONParser();
					JSONObject obj = (JSONObject) jsonParser.parse(bufferedReader.readLine());

					saveCompanyInformation(obj);

				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Server : Error!");
				} finally {
					System.out.println("Server : connection close");
					System.out.println("MySQL Server : connection close");
					mySQLconnection.close();
					client.close();
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {

		Thread serverMain = new Thread(new ServerMain());
		serverMain.start();

	}
	
	
	private boolean checkRowExist(String TableName, String PrimaryKey) throws Exception{
		
		// sql�� ���� �غ�.
		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `site` where " +  TableName + " = '" + PrimaryKey + "'");

		if (!resultSet.next()) {
			return false;
		}
		
		return true;
		
	}
	
	
	
	//����Ʈ�� ������ �߰�. ������ �Ѿ��. 
	private void saveSiteExist(JSONObject obj) throws Exception{
		
		// sql�� ���� �غ�.
		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `site` where siteName = '" + obj.get("companySiteName").toString() + "'");

		if (!resultSet.next()) {
			
			System.out.println("����Ʈ�����");
			String sql = "INSERT INTO `site` VALUES(?)";

			pstmt = mySQLconnection.prepareStatement(sql);
			pstmt.setString(1, obj.get("companySiteName").toString());
			pstmt.executeUpdate();
		}
		
	}

	//���۴� ���� �߰�. 
	private void saveCompanyInformation(JSONObject obj) throws Exception {
		
		saveSiteExist(obj);

		String sql = "INSERT INTO company VALUES(?,?,?,?,?,?,?)";

		pstmt = mySQLconnection.prepareStatement(sql);
		pstmt.setString(1, obj.get("companyName").toString());
		pstmt.setString(2, obj.get("companyAddress").toString());
		pstmt.setString(3, obj.get("companyCity").toString());
		pstmt.setInt(4, Integer.parseInt(obj.get("companyZipCode").toString()));
		pstmt.setString(5, obj.get("companyPhone").toString());
		pstmt.setString(6, obj.get("companyEmail").toString());
		pstmt.setString(7, obj.get("companySiteName").toString());

		pstmt.executeUpdate();

	}

}
