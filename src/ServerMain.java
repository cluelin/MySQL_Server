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
				Socket client = serverSocket.accept();
				System.out.println("Sever : connected");

				new Thread(new ServerThread(client)).start();
				
			}

		} catch (Exception e) {
			e.printStackTrace();
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

		// 2017.04.20
		// 이 파트가 필요한지 모르겠음.
		// 지워도 동작할거같은데..? 고려해봐야겠음.
		try {
			// save dumy information.
			String sql = "INSERT INTO `rma_table` (rmaDate, rmaOrderNumber, rmaCompanyName, siteCode) VALUES ('','','!@#','!@#')";
			statement.executeUpdate(sql);
			removePreservedRMAnumber();

		} catch (Exception e) {
			System.out.println("RMA number 기록을 위해 일부러 잘못 입력한다. 그래도 rmaNumber는 기록됨. ");
		}

	}

	public void removePreservedRMAnumber() throws Exception {

		String sql = "DELETE FROM `rma_table` WHERE `rmaCompanyName` = '!@#'";

		// statement = mySQLconnection.createStatement();
		statement.executeUpdate(sql);

	}

	// Add new Site, if exist then move on
	// return site location ID.
	private int saveSite() throws Exception {

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

	private void saveRMAItem(JSONObject objFromClient) throws Exception {

		String sql = "INSERT INTO `RMAitemTable` (`serialNumber`, `rmaIndex`, `rmaNumber`, `itemName`) VALUES (?,?,?,?)";
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
