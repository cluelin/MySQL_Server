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

	// 서버는 자기 port number만 알고있으면 됨.
	final public static int SERVER_PORT = 9090;

	// mySQL사용하기위한 준비.
	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static final String DB_URL = "jdbc:mysql://localhost:3306/rma";

	// mySQL 사용자 로그인
	static final String USERNAME = "root";
	static final String PASSWORD = "111111";

	Connection mySQLconnection = null;
	Statement statement = null;
	PreparedStatement pstmt = null;
	BufferedReader bufferedReader;

	@Override
	public void run() {

		try {

			// 서버소켓.
			ServerSocket serverSocket = new ServerSocket(SERVER_PORT);

			while (true) {

				System.out.println("Sever : waitting...");

				// 서버소켓으로 접근하는 소켓 열어주기.
				Socket client = serverSocket.accept();
				System.out.println("Sever : connected");

				// 이건 뭔지 모르겠음.
				Class.forName(JDBC_DRIVER);
				// mySQL과 접속.
				mySQLconnection = DriverManager.getConnection(DB_URL, USERNAME, PASSWORD);

				System.out.println("MySQL : Connected");

				try {

					// 클라이언트로부터 읽어들일 리더 준비.
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
		
		// sql문 선언 준비.
		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `site` where " +  TableName + " = '" + PrimaryKey + "'");

		if (!resultSet.next()) {
			return false;
		}
		
		return true;
		
	}
	
	
	
	//사이트가 없으면 추가. 있으면 넘어가기. 
	private void saveSiteExist(JSONObject obj) throws Exception{
		
		// sql문 선언 준비.
		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `site` where siteName = '" + obj.get("companySiteName").toString() + "'");

		if (!resultSet.next()) {
			
			System.out.println("사이트가없어여");
			String sql = "INSERT INTO `site` VALUES(?)";

			pstmt = mySQLconnection.prepareStatement(sql);
			pstmt.setString(1, obj.get("companySiteName").toString());
			pstmt.executeUpdate();
		}
		
	}

	//컴퍼니 정보 추가. 
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
