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

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class ServerMain implements Runnable {

	// 서버는 자기 port number만 알고있으면 됨.
	final public static int SERVER_PORT = 9090;

	// mySQL사용하기위한 준비.
	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static final String DB_URL = "jdbc:mysql://localhost:3306/RMA_DATABASE";

	// mySQL 사용자 로그인
	static final String USERNAME = "root";
	static final String PASSWORD = "111111";

	Connection mySQLconnection = null;
	Statement statement = null;
	PreparedStatement pstmt = null;
	BufferedReader bufferedReader;
	BufferedWriter bufferedWriter;
	PrintStream printStream;

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
					printStream = new PrintStream(client.getOutputStream());

					JSONParser jsonParser = new JSONParser();
					JSONObject obj;
					String input = bufferedReader.readLine();

					if (input != null) {
						// 정해진 action 수행.
						obj = (JSONObject) jsonParser.parse(input);
						checkAction(obj);
					}

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

		} catch (

		Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {

		Thread serverMain = new Thread(new ServerMain());
		serverMain.start();

	}

	// 수행할 동작 결정
	private void checkAction(JSONObject obj) throws Exception {

		String actionStr = obj.get("Action").toString();

		System.out.println("action : " + actionStr);

		if (actionStr == null) {
			// Action 없을때.
			return;
		} else if (actionStr.equals("requestRMAindex")) {
			getRMAindex();
		} else if (actionStr.equals("requestSaveRMAData")) {
			// RMA information 저장
			updateRMAInformation(obj);
		} else if (actionStr.equals("requestSearchRelatedRMA")) {
			searchRealatedRMAnumber(obj);
		}
	}

	// 다음번 RMA number 반환.
	private void getRMAindex() throws Exception {

		statement = mySQLconnection.createStatement();
		ResultSet rs = statement.executeQuery("SHOW TABLE STATUS WHERE `Name` = 'rma_table'");
		rs.next();
		String nextid = rs.getString("Auto_increment");
		System.out.println("next index : " + nextid);

		JSONObject obj = new JSONObject();
		obj.put("RMAindex", nextid);

		printStream.println(obj.toJSONString());

		statement = mySQLconnection.createStatement();

		// preserve RMA NUMBER
		// 개선 필요. update시에 companyName과 siteName이 not null이면서 foreign key이기때문에
		// 미리 저장되어있는 이름을 사용해서 등록해둔다..
		// 종료시점에 해당 RMAnumber가 저장되지않았다면 삭제하는것도 고려해야한다.
		String sql = "INSERT INTO `rma_table` (rmaDate, rmaOrderNumber, "
				+ "rmaContents, rmaBillTo, rmaShipTo, rmaTrackingNumber, "
				+ "rmaCompanyName, rmaCompanySite) VALUES ('','','','','','','companyName','SiteName')";

		statement.executeUpdate(sql);

	}

	// 사이트가 없으면 추가. 있으면 넘어가기.
	private void saveSiteExist(JSONObject obj) throws Exception {

		// sql문 선언 준비.
		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement
				.executeQuery("SELECT * FROM `site` where siteName = '" + obj.get("companySiteName").toString() + "'");

		if (!resultSet.next()) {

			System.out.println("사이트가없어여 , " + obj.get("companySiteName").toString());
			String sql = "INSERT INTO `site` VALUES(?)";

			pstmt = mySQLconnection.prepareStatement(sql);
			pstmt.setString(1, obj.get("companySiteName").toString());
			pstmt.executeUpdate();
		}

	}

	// 컴퍼니 정보 없으면 저장, 있으면 넘어감..
	private void saveCompanyInformation(JSONObject obj) throws Exception {

		saveSiteExist(obj);

		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `company` WHERE companyName = '" + obj.get("companyName").toString() + "'");

		// 있으면 리턴.
		if (resultSet.next()) {
			System.out.println("MySQL : companyName 이미 존재. ");
			return;
		}

		// 없으면 추가.

		String sql = "INSERT INTO company VALUES(?,?,?,?,?,?,?)";

		pstmt = mySQLconnection.prepareStatement(sql);
		pstmt.setString(1, obj.get("companyName").toString());
		pstmt.setString(2, obj.get("companyAddress").toString());
		pstmt.setString(3, obj.get("companyCity").toString());
		pstmt.setString(4, obj.get("companyZipCode").toString());
		pstmt.setString(5, obj.get("companyPhone").toString());
		pstmt.setString(6, obj.get("companyEmail").toString());
		pstmt.setString(7, obj.get("companySiteName").toString());

		pstmt.executeUpdate();

	}

	// 봉인 예정
	private void saveRMAInformation(JSONObject obj) throws Exception {

		saveCompanyInformation(obj);

		String sql = "INSERT INTO `rma_table` (rmaDate, rmaItem, rmaOrderNumber, "
				+ "rmaContents, rmaBillTo, rmaShipTo, rmaTrackingNumber, "
				+ "rmaCompanyName, rmaCompanySite) VALUES (?,'RMA Item',?,?,?,?,?,'companyName','SiteName')";

		pstmt = mySQLconnection.prepareStatement(sql);
		pstmt.setString(1, obj.get("rmaDate").toString());
		// pstmt.setString(2, obj.get("rmaItem").toString());
		pstmt.setString(2, "RMA Item");
		pstmt.setString(3, obj.get("rmaOrderNumber").toString());
		pstmt.setString(4, obj.get("rmaContents").toString());
		pstmt.setString(5, obj.get("rmaBillTo").toString());
		pstmt.setString(6, obj.get("rmaShipTo").toString());
		pstmt.setString(7, obj.get("rmaTrackingNumber").toString());
		pstmt.setString(8, obj.get("companyName").toString());
		pstmt.setString(9, obj.get("companySiteName").toString());

		pstmt.executeUpdate();

	}

	private void searchRealatedRMAnumber(JSONObject obj) throws Exception {

		System.out.println("searchRealatedRMAnumber");

		String sql = "select * from rma_table where rmaCompanyName = '" + obj.get("companyName").toString() + "'";

		System.out.println(sql);
		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {
			String rmaNumber = resultSet.getString("rmaNumber");
			String rmaContents = resultSet.getString("rmaContents");
			String rmaDate = resultSet.getString("rmaDate");

			JSONObject outputObj = new JSONObject();
			outputObj.put("RMAnumber", rmaNumber);
			outputObj.put("RMAcontents", rmaContents);
			outputObj.put("RMAdate", rmaDate);

			printStream.println(outputObj.toJSONString());

			System.out.println("rmaNumber : " + rmaNumber + " rmaContents : " + rmaContents);
		}

	}

	private void updateRMAInformation(JSONObject obj) throws Exception {
		saveCompanyInformation(obj);

		String sql = "UPDATE `rma_table` SET " + "rmaNumber = '" + obj.get("rmaNumber").toString() + "',"
				+ "rmaDate = '" + obj.get("rmaDate").toString() + "'," + "rmaOrderNumber = '"
				+ obj.get("rmaOrderNumber").toString() + "'," + "rmaContents = '" + obj.get("rmaContents").toString()
				+ "'," + "rmaBillTo = '" + obj.get("rmaBillTo").toString() + "'," + "rmaShipTo = '"
				+ obj.get("rmaShipTo").toString() + "'," + "rmaTrackingNumber = '"
				+ obj.get("rmaTrackingNumber").toString() + "'," + "rmaCompanyName = '"
				+ obj.get("companyName").toString() + "'," + "rmaCompanySite ='" + obj.get("companySiteName").toString()
				+ "'" + "WHERE rmaIndex= " + (obj.get("rmaNumber").toString()).replace("DA", "");

		statement = mySQLconnection.createStatement();

		statement.executeUpdate(sql);

		// Item 정보 저장

		sql = "INSERT INTO `RMAitemTable` (`serialNumber`, `rmaIndex`, `rmaNumber`, `itemName`) VALUES (?,?,?,?)";
		pstmt = mySQLconnection.prepareStatement(sql);

		for (int i = 0; i < Integer.parseInt(obj.get("itemCount").toString()); i++) {
			System.out.println(obj.get("serialNumber" + i).toString());
			System.out.println(obj.get("itemName" + i).toString());

			pstmt.setString(1, obj.get("serialNumber" + i).toString());
			pstmt.setString(2, (obj.get("rmaNumber").toString()).replace("DA", ""));
			pstmt.setString(3, (obj.get("rmaNumber").toString()));
			pstmt.setString(4, obj.get("itemName" + i).toString());
			pstmt.executeUpdate();

		}

	}
}
