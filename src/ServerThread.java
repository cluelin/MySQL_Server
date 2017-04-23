import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
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

public class ServerThread implements Runnable {

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

	private Socket client;

	public ServerThread(Socket socket) {

		this.client = socket;

	}

	@Override
	public void run() {

		try {

			// 이건 뭔지 모르겠음.
			Class.forName(ServerInformation.JDBC_DRIVER);

			// mySQL과 접속.
			mySQLconnection = DriverManager.getConnection(ServerInformation.DB_URL, ServerInformation.USERNAME,
					ServerInformation.PASSWORD);

			statement = mySQLconnection.createStatement();

			System.out.println("MySQL : Connected");

			// Reader and Print setting
			bufferedReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
			printStream = new PrintStream(client.getOutputStream());

			while (true) {
				String input = bufferedReader.readLine();

				if (input != null) {
					// 정해진 action 수행.
					objFromClient = (JSONObject) jsonParser.parse(input);
					checkAction(objFromClient.get("Action").toString());
				} else {
					break;
				}

			}

		} catch (Exception e) {
			// 서버 연결 에러.
			e.printStackTrace();
			System.out.println("initial set up error  or read error");

		} finally {
			try {
				// 연결 정상 종료.
				System.out.println("Server : connection close");
				System.out.println("MySQL Server : connection close");
				mySQLconnection.close();
				client.close();
			} catch (Exception e) {

				System.out.println("termination Error");

				e.printStackTrace();
			}

		}

	}

	// 수행할 동작 결정
	private void checkAction(String action) throws Exception {

		System.out.println("action : " + action);

		if (action == null) {
			// Action 없을때.
			return;
		} else if (action.equals("requestRMAindex")) {

			getRMAindex();

		} else if (action.equals("requestSaveRMAData")) {

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

		} else if (action.equals("requestItemName")) {

			getItemNameFromDatabase(objFromClient.get("itemName").toString());

		}
	}

	// send next RMA number to Client.
	private void getRMAindex() throws Exception {

		// 다음 RMA number를 peak 해본다. (사용하지는 않고 번호만 알아냄)
		ResultSet rs = statement.executeQuery("SHOW TABLE STATUS WHERE `Name` = 'rma_table'");
		rs.next();
		String nextid = rs.getString("Auto_increment");

		// 다음 RMA number
		System.out.println("next index : " + nextid);

		objToClient.put("RMAindex", nextid);

		printStream.println(objToClient.toJSONString());

		// 2017.04.21
		// 사용하지않고 Peak 하면 다른 클라이언트가 접속했을때 중복될 위험이있음.
		// 중복위험을 제거하기 위해 dumy information을 삽입후 삭제 해주는 과정이 필요함.
		preservedRMAnumber();

	}

	public void preservedRMAnumber() {

		try {
			// save dumy information.
			String sql = "INSERT INTO `rma_table` (rmaDate, rmaOrderNumber, rmaCompanyName, siteCode) VALUES ('','','!@#','11795')";
			statement.executeUpdate(sql);

			sql = "DELETE FROM `rma_table` WHERE `rmaCompanyName` = '!@#'";
			statement.executeUpdate(sql);

		} catch (Exception e) {
			System.out.println("RMA number 기록을 위해 일부러 잘못 입력한다. 그래도 rmaNumber는 기록됨. ");
			// e.printStackTrace();
		}

	}

	// Add new Site, if exist then move on
	// return site location ID.
	private int saveSiteInformation() throws Exception {

		// company와 site명이 같은경우를 검색.
		ResultSet resultSet = statement
				.executeQuery("SELECT * FROM `site` where siteName = '" + objFromClient.get("siteName").toString()
						+ "' AND " + "companyName = '" + objFromClient.get("companyName") + "'");

		// 검색 결과가 존재하지 않을때 추가.
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
			// 기존에 있으면 등록된 사이트의 code를 반환.

			return resultSet.getInt("siteCode");
		}

	}

	// if company information dosen't exist, then Save
	// if there is , then Update!
	private void updateCompanyInformation() throws Exception {

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `company` WHERE companyName = '" + objFromClient.get("companyName").toString() + "'");

		if (resultSet.next()) {
			// 있으면 기존 정보 update.
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
		} else {

			// 없으면 Insert new.

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

	}

	// updateRMAInformation, saveRMAInformation 둘중 하나면 충분.
	private void saveRMAInformation() throws Exception {

		// add or update company information
		updateCompanyInformation();

		// add site information, and get site code
		int siteCode = saveSiteInformation();

		System.out.println("siteCode : " + siteCode);
		System.out.println("rmaNumber : " + objFromClient.get("rmaNumber").toString());

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

		saveRMAItem(objFromClient);

	}

	private void saveRMAItem(JSONObject objFromClient) throws Exception {

		
		System.out.println("saveRMAItem");
		
		String sql = "INSERT INTO `RMAitemTable` (`serialNumber`, `rmaIndex`, `rmaNumber`, `itemName`, "
				+ "`itemDescription`, `itemPrice`) VALUES (?,?,?,?,?,?)";
		
		System.out.println(sql);
		pstmt = mySQLconnection.prepareStatement(sql);

		for (int i = 0; i < Integer.parseInt(objFromClient.get("itemCount").toString()); i++) {
			
			System.out.println(objFromClient.get("itemSerialNumber"+i).toString());
			System.out.println(objFromClient.get("rmaNumber").toString());
			System.out.println(objFromClient.get("itemName"+i).toString());
			System.out.println(objFromClient.get("itemPrice"+i).toString());

			pstmt.setString(1, objFromClient.get("itemSerialNumber"+i).toString());
			pstmt.setString(2, (objFromClient.get("rmaNumber").toString()).replace("DA", ""));
			pstmt.setString(3, (objFromClient.get("rmaNumber").toString()));
			pstmt.setString(4, objFromClient.get("itemName"+i).toString());
			pstmt.setString(5, objFromClient.get("itemDescription"+i).toString());
			pstmt.setInt(6, Integer.parseInt(objFromClient.get("itemPrice"+i).toString()));

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

			JSONObject objToClient = new JSONObject();
			objToClient.put("RMAnumber", rmaNumber);
			objToClient.put("RMAcontents", rmaContents);
			objToClient.put("RMAdate", rmaDate);

			printStream.println(objToClient.toJSONString());

			System.out.println("rmaNumber : " + rmaNumber + " rmaContents : " + rmaContents);
		}

		printStream.println("end");

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

		printStream.println("end");
	}

	// prefix로 시작하는 companyName을 찾아서 client에게 반환.
	private void getCompanyNameFromMysql(String prefix) throws Exception {

		String sql = "SELECT companyName FROM company WHERE companyName LIKE '" + prefix
				+ "%' AND companyName <> '!@#'";

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {
			String companyName = resultSet.getString("companyName");
			System.out.println(companyName);

			JSONObject companyNameJSON = new JSONObject();
			companyNameJSON.put("companyName", companyName);

			printStream.println(companyNameJSON.toJSONString());
		}

		printStream.println("end");
	}

	private void getItemNameFromDatabase(String prefix) throws Exception {

		String sql = "SELECT * FROM item WHERE itemName LIKE '" + prefix + "%'";

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {

			// Integer itemCode = resultSet.getInt("itemCode");
			String itemName = resultSet.getString("itemName");
			String itemDescription = resultSet.getString("itemDescription");
			Integer itemPrice = resultSet.getInt("itemPrice");

			System.out.println(itemName + itemDescription);

			JSONObject itemNameJSON = new JSONObject();
			// itemNameJSON.put("itemCode", itemCode);
			itemNameJSON.put("itemName", itemName);
			itemNameJSON.put("itemDescription", itemDescription);
			itemNameJSON.put("itemPrice", itemPrice);

			printStream.println(itemNameJSON.toJSONString());
		}

		printStream.println("end");
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
		printStream.println(new String());
	}

	private void getRMADetailFromDatabase(String rmaNumber) throws Exception {

		String sql = "SELECT * FROM rma_table WHERE rmaNumber = '" + rmaNumber + "'";

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		JSONObject RMADetailJSON = new JSONObject();

		while (resultSet.next()) {

			RMADetailJSON.put("rmaNumber", resultSet.getString("rmaNumber"));
			RMADetailJSON.put("rmaDate", resultSet.getString("rmaDate"));

			RMADetailJSON.put("rmaOrderNumber", resultSet.getString("rmaOrderNumber"));
			RMADetailJSON.put("rmaContents", resultSet.getString("rmaContents"));
			RMADetailJSON.put("rmaBillTo", resultSet.getString("rmaBillTo"));
			RMADetailJSON.put("rmaShipTo", resultSet.getString("rmaShipTo"));
			RMADetailJSON.put("rmaTrackingNumber", resultSet.getString("rmaTrackingNumber"));

		}
		
		sql = "SELECT count(*) FROM rmaitemtable WHERE rmaNumber = '" + rmaNumber + "'";
		
		resultSet = statement.executeQuery(sql);
		int itemCount;
		
		while (resultSet.next()) {
			System.out.println("개수 : " + resultSet.getInt("count(*)"));
			
			itemCount = resultSet.getInt("count(*)");

		}
		
		

		sql = "SELECT * FROM rmaitemtable WHERE rmaNumber = '" + rmaNumber + "'";

		resultSet = statement.executeQuery(sql);

		int i = 0;
		while (resultSet.next()) {
			
			System.out.println(resultSet.getString("itemName"));
			System.out.println(resultSet.getString("SerialNumber"));
			System.out.println(resultSet.getString("itemDescription"));
			System.out.println(resultSet.getString("itemPrice"));

			RMADetailJSON.put("itemName"+i, resultSet.getString("itemName"));
			RMADetailJSON.put("serialNumber"+i, resultSet.getString("SerialNumber"));
			RMADetailJSON.put("itemDescription"+i, resultSet.getString("itemDescription"));
			RMADetailJSON.put("itemPrice"+i, resultSet.getString("itemPrice"));
			i++;

		}
		
		RMADetailJSON.put("itemCount", i);

		printStream.println(RMADetailJSON.toJSONString());

	}

}
