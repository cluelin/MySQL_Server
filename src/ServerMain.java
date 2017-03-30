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

	private Connection mySQLconnection = null;
	private Statement statement = null;
	private PreparedStatement pstmt = null;
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;
	private PrintStream printStream;

	private JSONParser jsonParser = new JSONParser();
	private JSONObject objFromClient;

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
				statement = mySQLconnection.createStatement();

				System.out.println("MySQL : Connected");

				try {

					// 클라이언트로부터 읽어들일 리더 준비.
					bufferedReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
					printStream = new PrintStream(client.getOutputStream());

					String input = bufferedReader.readLine();

					if (input != null) {
						// 정해진 action 수행.
						objFromClient = (JSONObject) jsonParser.parse(input);
						checkAction(objFromClient.get("Action").toString());
					}

				} catch (Exception e) {
					e.printStackTrace();
					System.out.println("Server : Error!");
				} finally {
					System.out.println("Server : connection close");
					System.out.println("MySQL Server : connection close");
					// System.out.println("reservedRMAnumber Removed");
					// removePreservedRMAnumber();
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
	private void checkAction(String action) throws Exception {

		// String action = obj.get("Action").toString();

		System.out.println("action : " + action);

		if (action == null) {
			// Action 없을때.
			return;
		} else if (action.equals("requestRMAindex")) {
			getRMAindex();
		} else if (action.equals("requestSaveRMAData")) {
			// RMA information 저장
			// updateRMAInformation(obj);
			saveRMAInformation();
		} else if (action.equals("requestSearchRelatedRMA")) {
			searchRealatedRMAnumber();
		} else if (action.equals("requestSiteName")) {
			getSiteNameFromMysql(objFromClient.get("siteName").toString(), objFromClient.get("companyName").toString());
		} else if (action.equals("requestCompanyName")) {
			getCompanyNameFromMysql(objFromClient.get("companyName").toString());
		} else if (action.equals("requestCompanyDetail")) {
			getCompanyDetail(objFromClient.get("companyName").toString());
		} else if (action.equals("requestRMADetail")) {
			getRMADetailFromDatabase(objFromClient.get("rmaNumber").toString());
		}
	}

	// 다음번 RMA number 반환.
	private void getRMAindex() throws Exception {

		// statement = mySQLconnection.createStatement();
		ResultSet rs = statement.executeQuery("SHOW TABLE STATUS WHERE `Name` = 'rma_table'");
		rs.next();
		String nextid = rs.getString("Auto_increment");
		System.out.println("next index : " + nextid);

		JSONObject obj = new JSONObject();
		obj.put("RMAindex", nextid);

		printStream.println(obj.toJSONString());

		// preserve RMA NUMBER
		// 개선 필요. update시에 companyName과 siteName이 not null이면서 foreign key이기때문에
		// 미리 저장되어있는 이름을 사용해서 등록해둔다..
		// 종료시점에 해당 RMAnumber가 저장되지않았다면 삭제하는것도 고려해야한다.

		try{
			String sql = "INSERT INTO `rma_table` (rmaDate, rmaOrderNumber, rmaCompanyName, siteCode) VALUES ('','','!@#','!@#')";

			statement.executeUpdate(sql);
			removePreservedRMAnumber();

		}catch(Exception e){
			System.out.println("RMA number 기록을 위해 일부러 잘못 입력한다. 그래도 rmaNumber는 기록됨. ");
		}
		
		

	}

	public void removePreservedRMAnumber() throws Exception {

		String sql = "DELETE FROM `rma_table` WHERE `rmaCompanyName` = '!@#'";

		// statement = mySQLconnection.createStatement();
		statement.executeUpdate(sql);

	}

	// 사이트가 없으면 추가. 있으면 넘어가기.
	private int saveSite() throws Exception {

		// sql문 선언 준비.

		// company와 site명이 같은경우, 넘어감.
		ResultSet resultSet = statement
				.executeQuery("SELECT * FROM `site` where siteName = '" + objFromClient.get("siteName").toString()
						+ "' AND " + "companyName = '" + objFromClient.get("companyName") + "'");

		// 사이트가 존재하지 않을때 추가.
		if (!resultSet.next()) {

			ResultSet rs = statement.executeQuery("SHOW TABLE STATUS WHERE `Name` = 'site'");
			rs.next();
			int nextid = Integer.parseInt(rs.getString("Auto_increment"));

			System.out.println("Auto_increment : " + nextid);

			String sql = "INSERT INTO `site` (`siteName`, `companyName`) VALUES(?,?)";

			pstmt = mySQLconnection.prepareStatement(sql);
			pstmt.setString(1, objFromClient.get("siteName").toString());
			pstmt.setString(2, objFromClient.get("companyName").toString());
			pstmt.executeUpdate();

			// 추가하면서 추가된 곳의 ID를 넘겨줌.
			return nextid;

		} else {
			// 기존에 있으면..?

			return resultSet.getInt("siteCode");
		}

	}

	// 컴퍼니 정보 없으면 저장, 있으면 Update!
	private void saveCompanyInformation() throws Exception {

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `company` WHERE companyName = '" + objFromClient.get("companyName").toString() + "'");

		// 있으면 리턴.
		if (resultSet.next()) {
			System.out.println("MySQL : companyName 이미 존재. ");

			String sql = "UPDATE `company` " + "SET companyAddress ='" + objFromClient.get("companyAddress").toString()
					+ "'" + ", companyCity ='" + objFromClient.get("companyCity").toString() + "'"
					+ ", companyZipCode ='" + objFromClient.get("companyZipCode").toString() + "'" + ", companyPhone ='"
					+ objFromClient.get("companyPhone").toString() + "'" + ", companyEmail ='"
					+ objFromClient.get("companyEmail").toString() + "'" + " WHERE companyName= '"
					+ objFromClient.get("companyName").toString() + "'";

			System.out.println(sql);

			statement.executeUpdate(sql);
			return;
		}

		// 없으면 추가.

		String sql = "INSERT INTO company VALUES(?,?,?,?,?,?)";

		pstmt = mySQLconnection.prepareStatement(sql);
		pstmt.setString(1, objFromClient.get("companyName").toString());
		pstmt.setString(2, objFromClient.get("companyAddress").toString());
		pstmt.setString(3, objFromClient.get("companyCity").toString());
		pstmt.setString(4, objFromClient.get("companyZipCode").toString());
		pstmt.setString(5, objFromClient.get("companyPhone").toString());
		pstmt.setString(6, objFromClient.get("companyEmail").toString());

		pstmt.executeUpdate();

	}

	// updateRMAInformation, saveRMAInformation 둘중 하나면 충분.
	private void saveRMAInformation() throws Exception {

		saveCompanyInformation();
		int siteCode = saveSite();

		System.out.println("siteCode : " + siteCode);

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `rma_table` WHERE rmaNumber = '" + objFromClient.get("rmaNumber").toString() + "'");

		if (resultSet.next()) {

			String sql = "UPDATE `rma_table` SET rmaDate = '" + objFromClient.get("rmaDate").toString() + "',"
					+ "rmaOrderNumber = '" + objFromClient.get("rmaOrderNumber").toString() + "'," + "rmaContents = '"
					+ objFromClient.get("rmaContents").toString() + "'," + "rmaBillTo = '"
					+ objFromClient.get("rmaBillTo").toString() + "'," + "rmaShipTo = '"
					+ objFromClient.get("rmaShipTo").toString() + "'," + "rmaTrackingNumber = '"
					+ objFromClient.get("rmaTrackingNumber").toString() + "',siteCode = '" + siteCode + "' "
					+ "WHERE rmaNumber= '" + objFromClient.get("rmaNumber").toString() + "'";

			System.out.println(sql);

			statement.executeUpdate(sql);

		} else {

			String sql = "INSERT INTO `rma_table` (rmaIndex, rmaNumber, rmaDate, rmaOrderNumber, "
					+ "rmaContents, rmaBillTo, rmaShipTo, rmaTrackingNumber, "
					+ "rmaCompanyName, siteCode) VALUES (?,?,?,?,?,?,?,?,?,?)";

			pstmt = mySQLconnection.prepareStatement(sql);

			pstmt.setString(1, objFromClient.get("rmaNumber").toString().replace("DA", ""));
			pstmt.setString(2, objFromClient.get("rmaNumber").toString());
			pstmt.setString(3, objFromClient.get("rmaDate").toString());
			pstmt.setString(4, objFromClient.get("rmaOrderNumber").toString());
			pstmt.setString(5, objFromClient.get("rmaContents").toString());
			pstmt.setString(6, objFromClient.get("rmaBillTo").toString());
			pstmt.setString(7, objFromClient.get("rmaShipTo").toString());
			pstmt.setString(8, objFromClient.get("rmaTrackingNumber").toString());
			pstmt.setString(9, objFromClient.get("companyName").toString());
			pstmt.setInt(10, siteCode);

			pstmt.executeUpdate();

		}

	}

	private void updateRMAInformation(JSONObject objFromClient) throws Exception {
		saveCompanyInformation();

		String sql = "UPDATE `rma_table` SET " + "rmaNumber = '" + objFromClient.get("rmaNumber").toString() + "',"
				+ "rmaDate = '" + objFromClient.get("rmaDate").toString() + "'," + "rmaOrderNumber = '"
				+ objFromClient.get("rmaOrderNumber").toString() + "'," + "rmaContents = '"
				+ objFromClient.get("rmaContents").toString() + "'," + "rmaBillTo = '"
				+ objFromClient.get("rmaBillTo").toString() + "'," + "rmaShipTo = '"
				+ objFromClient.get("rmaShipTo").toString() + "'," + "rmaTrackingNumber = '"
				+ objFromClient.get("rmaTrackingNumber").toString() + "'," + "rmaCompanyName = '"
				+ objFromClient.get("companyName").toString() + "'," + "rmaCompanySite ='"
				+ objFromClient.get("companySiteName").toString() + "'" + "WHERE rmaIndex= "
				+ (objFromClient.get("rmaNumber").toString()).replace("DA", "");

		// statement = mySQLconnection.createStatement();

		statement.executeUpdate(sql);

		// Item 정보 저장

		sql = "INSERT INTO `RMAitemTable` (`serialNumber`, `rmaIndex`, `rmaNumber`, `itemName`) VALUES (?,?,?,?)";
		pstmt = mySQLconnection.prepareStatement(sql);

		for (int i = 0; i < Integer.parseInt(objFromClient.get("itemCount").toString()); i++) {
			System.out.println(objFromClient.get("serialNumber" + i).toString());
			System.out.println(objFromClient.get("itemName" + i).toString());

			pstmt.setString(1, objFromClient.get("serialNumber" + i).toString());
			pstmt.setString(2, (objFromClient.get("rmaNumber").toString()).replace("DA", ""));
			pstmt.setString(3, (objFromClient.get("rmaNumber").toString()));
			pstmt.setString(4, objFromClient.get("itemName" + i).toString());
			pstmt.executeUpdate();

		}

	}

	private void searchRealatedRMAnumber() throws Exception {

		System.out.println("searchRealatedRMAnumber");

		String sql = "select * from rma_table where rmaCompanyName = '" + objFromClient.get("companyName").toString()
				+ "'";

		System.out.println(sql);
		// statement = mySQLconnection.createStatement();

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

	private void getSiteNameFromMysql(String prefix, String CompanyName) throws Exception {

		// String sql = "SELECT siteName FROM site WHERE siteName LIKE '" +
		// prefix + "%'";

		String sql = "SELECT siteName FROM site WHERE siteName LIKE '" + prefix + "%' AND companyName = '" + CompanyName
				+ "'";

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {
			String siteName = resultSet.getString("siteName");

			System.out.println("siteName list : " + siteName);

			JSONObject siteNameJSON = new JSONObject();
			siteNameJSON.put("siteName", siteName);

			printStream.println(siteNameJSON.toJSONString());
		}
	}

	private void getCompanyNameFromMysql(String prefix) throws Exception {

		String sql = "SELECT companyName FROM company WHERE companyName LIKE '" + prefix
				+ "%' AND companyName <> '!@#'";

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {
			String companyName = resultSet.getString("companyName");

			JSONObject companyNameJSON = new JSONObject();
			companyNameJSON.put("companyName", companyName);

			printStream.println(companyNameJSON.toJSONString());
		}
	}

	private void getCompanyDetail(String companyName) throws Exception {
		String sql = "SELECT * FROM company WHERE companyName = '" + companyName + "'";

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {

			JSONObject companyDetailJSON = new JSONObject();
			companyDetailJSON.put("companyAddress", resultSet.getString("companyAddress"));
			companyDetailJSON.put("companyCity", resultSet.getString("companyCity"));
			companyDetailJSON.put("companyZipCode", resultSet.getString("companyZipCode"));
			companyDetailJSON.put("companyPhone", resultSet.getString("companyPhone"));
			companyDetailJSON.put("companyEmail", resultSet.getString("companyEmail"));

			printStream.println(companyDetailJSON.toJSONString());
		}
	}

	private void getRMADetailFromDatabase(String rmaNumber) throws Exception {

		String sql = "SELECT * FROM rma_table WHERE rmaNumber = '" + rmaNumber + "'";

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {

			JSONObject RMADetailJSON = new JSONObject();
			RMADetailJSON.put("rmaNumber", resultSet.getString("rmaNumber"));
			RMADetailJSON.put("rmaDate", resultSet.getString("rmaDate"));

			RMADetailJSON.put("rmaOrderNumber", resultSet.getString("rmaOrderNumber"));
			RMADetailJSON.put("rmaContents", resultSet.getString("rmaContents"));
			RMADetailJSON.put("rmaBillTo", resultSet.getString("rmaBillTo"));
			RMADetailJSON.put("rmaShipTo", resultSet.getString("rmaShipTo"));
			RMADetailJSON.put("rmaTrackingNumber", resultSet.getString("rmaTrackingNumber"));

			printStream.println(RMADetailJSON.toJSONString());
		}

	}

}
