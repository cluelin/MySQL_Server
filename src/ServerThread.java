import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Executable;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException;

public class ServerThread implements Runnable {

	final int DATA_PASS_SOCK = 6000;
	final String ATTACHED_FILE_DIR = "AttachFile";

	// input/output information
	private BufferedReader bufferedReader;
	private BufferedWriter bufferedWriter;
	private PrintStream printStream;

	InputStream inputStream;

	// JSON
	private JSONParser jsonParser = new JSONParser();
	private JSONObject objFromClient;
	private JSONObject objToClient = new JSONObject();

	private Connection mySQLconnection = null;
	private Statement statement = null;
	private PreparedStatement pstmt = null;

	private Socket clientSocket;

	public ServerThread(Socket clientSocket) {

		this.clientSocket = clientSocket;

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
			inputStream = clientSocket.getInputStream();
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			printStream = new PrintStream(clientSocket.getOutputStream());

			while (true) {
				String input = bufferedReader.readLine();

				System.out.println("Thread ID : " + this + "    input : " + input);

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
				clientSocket.close();
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

			System.out.println("siteName : " + objFromClient.get("siteName").toString());
			System.out.println("companyName : " + objFromClient.get("companyName").toString());

			getSiteNameFromMysql(objFromClient.get("siteName").toString(), objFromClient.get("companyName").toString());

		} else if (action.equals("requestCompanyName")) {

			getCompanyNameFromMysql(objFromClient.get("companyName").toString());

		} else if (action.equals("requestCompanyDetail")) {

			getCompanyDetail(objFromClient.get("companyName").toString());

		} else if (action.equals("requestRMADetail")) {

			getRMADetailFromDatabase(objFromClient.get("rmaNumber").toString());

		} else if (action.equals("requestItemName")) {

			getItemNameFromDatabase(objFromClient.get("itemName").toString());

		} else if (action.equals("checkRMAnumber")) {

			JSONObject objToClient = new JSONObject();

			if (rmaNumberAlreadyUsed(objFromClient.get("rmaNumber").toString())) {
				objToClient.put("rmaNumberAlreadyUsed", true);
			} else {
				objToClient.put("rmaNumberAlreadyUsed", false);
			}

			printStream.println(objToClient);

		} else if (action.equals("validate")) {

			JSONObject itemValidateObject;

			itemValidateObject = getItemValidateObject(objFromClient);

			printStream.println(itemValidateObject);
		} else if (action.equals("SignUp")) {

			registerUser(objFromClient);
		} else if (action.equals("SignIn")) {

			signInUser(objFromClient);
		} else if (action.equals("saveAttachFileInfo")) {

			saveAttachFileInfo(objFromClient);
		} else if (action.equals("getAttachFileInfo")) {

			sendAttachFile(objFromClient);
		}
	}

	private void sendAttachFile(JSONObject attachFIleObj) {

		String fileName = attachFIleObj.get("fileName").toString();

		System.out.println("file name : " + fileName);

		File file = new File(ATTACHED_FILE_DIR + "//" + fileName);
		try {

			ServerSocket serverSock = new ServerSocket(DATA_PASS_SOCK);

			Socket dataPassSock = serverSock.accept();

			FileInputStream fileInputStream = new FileInputStream(file);
			BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);

			OutputStream outputStream = dataPassSock.getOutputStream();
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream);

			byte[] contents;
			long fileSize = file.length();
			long current = 0;

			while (current != fileSize) {
				int size = 1000;

				if (fileSize - current >= size) {
					current += size;
				} else {
					size = (int) (fileSize - current);
					current = fileSize;
				}

				contents = new byte[size];
				bufferedInputStream.read(contents, 0, size);
				bufferedOutputStream.write(contents);

				System.out.print("Sending file ... " + (current * 100) / fileSize + "% complete!");

			}

			bufferedOutputStream.flush();
			bufferedOutputStream.close();
			outputStream.close();

			fileInputStream.close();
			bufferedInputStream.close();

			dataPassSock.close();
			serverSock.close();

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private int getAttachFileCount(String rmaNumber, JSONObject toClientObj) {

		String sql = "SELECT fileName FROM `attached_file_info` where rmaNumber = '" + rmaNumber + "'";

		System.out.println(sql);

		int countOfAttachment = 0;
		try {

			ResultSet resultSet = statement.executeQuery(sql);

			int i = 0;

			while (resultSet.next()) {

				System.out.println("file name : " + resultSet.getString("fileName"));

				toClientObj.put("fileName" + i, resultSet.getString("fileName"));

				i++;
			}

			countOfAttachment = i;

			toClientObj.put("countOfAttachment", countOfAttachment);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return countOfAttachment;
	}

	// 문제지점.
	private void saveAttachFileInfo(JSONObject attachFileObj) {

		String rmaNumber = attachFileObj.get("rmaNumber").toString();

		String attachFileName = attachFileObj.get("attachFileName").toString();

		String sql = "INSERT INTO `attached_file_info` (rmaNumber, fileName) VALUES (?,?)";

		System.out.println(sql);

		File fileDir = new File(ATTACHED_FILE_DIR);

		if (!fileDir.exists()) {
			try {
				fileDir.mkdirs();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {

			ServerSocket serversock = new ServerSocket(DATA_PASS_SOCK);
			Socket dataPassSock = serversock.accept();

			// Initialize the FileOutputStream to the output file's full path.
			FileOutputStream fileOutputStream = new FileOutputStream(fileDir + "\\" + rmaNumber + attachFileName);
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
			InputStream dataPassInputStream = dataPassSock.getInputStream();
			DataInputStream dataInputStream = new DataInputStream(dataPassInputStream);

			byte[] contents = new byte[10000];
			// No of bytes read in one read() call
			int bytesRead = 0;
			int totalSize = 0;

			while ((bytesRead = dataInputStream.read(contents)) != -1) {
				bufferedOutputStream.write(contents, 0, bytesRead);
				totalSize += bytesRead;
				System.out.println("bytesRead : " + bytesRead);
			}

			System.out.println("totalSize : " + totalSize);

			bufferedOutputStream.flush();
			bufferedOutputStream.close();

			dataInputStream.close();

			dataPassSock.close();
			serversock.close();

		} catch (Exception e) {
			e.printStackTrace();
		}

		try {

			System.out.println("첨부파일 디비 저장 시작. ");
			pstmt = mySQLconnection.prepareStatement(sql);
			pstmt.setString(1, rmaNumber);
			pstmt.setString(2, attachFileName);
			pstmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void signInUser(JSONObject userInfoObj) {
		String stringID = userInfoObj.get("stringID").toString();

		String passWord = userInfoObj.get("passWord").toString();

		String sql = "SELECT id FROM `user_ID_Table` where id = '" + stringID + "' AND passWord = '" + passWord + "'";

		System.out.println(sql);

		JSONObject resultObj = new JSONObject();

		try {
			ResultSet resultSet = statement.executeQuery(sql);

			if (resultSet.next()) {

				resultObj.put("result", "SUCESS");
				resultObj.put("signInID", resultSet.getString("id"));

			} else {
				resultObj.put("result", "FIAL");
			}

			printStream.println(resultObj);

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private boolean checkIDvalid(String stringID) {
		boolean result = true;

		String sql = "SELECT * FROM `user_ID_Table` where id = '" + stringID + "'";

		try {
			ResultSet resultSet = statement.executeQuery(sql);

			if (resultSet.next()) {
				result = false;

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	private void registerUser(JSONObject userInfoObj) {

		String stringID = userInfoObj.get("stringID").toString();

		String passWord = userInfoObj.get("passWord").toString();

		JSONObject resultObj = new JSONObject();

		if (checkIDvalid(stringID)) {
			// 암호화는 나중에 추가하던가 말던가.
			// byte[] utf8 = passWord.getBytes("UTF-8");
			// byte[] test = DigestUtils.sha(DigestUtils.sha(utf8));
			// return "*" + convertToHex(test).toUpperCase();

			String sql = "INSERT INTO `user_ID_Table` (id, passWord) VALUES (?,?)";

			System.out.println(sql);

			try {

				pstmt = mySQLconnection.prepareStatement(sql);
				pstmt.setString(1, stringID);
				pstmt.setString(2, passWord);
				pstmt.executeUpdate();
			} catch (Exception e) {
				e.printStackTrace();
			}

			resultObj.put("result", "OK");
		} else {
			resultObj.put("result", "FAIL");

		}

		printStream.println(resultObj);

	}

	private JSONObject getItemValidateObject(JSONObject objFromClient) throws Exception {

		JSONObject validateResult = new JSONObject();
		int itemCount = Integer.parseInt(objFromClient.get("itemCount").toString());

		String sql;
		ResultSet resultSet;
		int itemNameCount;

		validateResult.put("itemNameValidation", true);
		validateResult.put("itemSerialValidation", true);

		for (int i = 0; i < itemCount; i++) {

			System.out.println("itemName + i : " + objFromClient.get("itemName" + i));

			// itemName이 list에 존재하는가..
			sql = "SELECT count(*) FROM item WHERE itemName = '" + objFromClient.get("itemName" + i).toString() + "'";

			resultSet = statement.executeQuery(sql);
			itemNameCount = -1;

			while (resultSet.next()) {
				System.out.println("개수 : " + resultSet.getInt("count(*)"));

				itemNameCount = resultSet.getInt("count(*)");

			}

			if (itemNameCount <= 0) {
				// 이름이 존재하는 애가 없음
				validateResult.put("itemNameValidation", false);
				break;
			}

			sql = "SELECT count(*) FROM rmaitemtable WHERE serialNumber = '"
					+ objFromClient.get("itemSerialNumber" + i).toString() + "'";

			resultSet = statement.executeQuery(sql);
			int itemSerialCount = -1;

			while (resultSet.next()) {
				System.out.println("개수 : " + resultSet.getInt("count(*)"));

				itemSerialCount = resultSet.getInt("count(*)");

			}

			if (itemSerialCount > 0) {
				// 시리얼넘버가 일치하는 애가 기존에 존재. 에러 발생.
				validateResult.put("itemSerialValidation", false);
				break;
			}

		}

		return validateResult;
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

		// 전달되어온 rmaNumber가 현재 rma_table에 존재하는지 확인하는 쿼리.
		// Query for verify that rmaNumber from the client exist on the server
		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `rma_table` WHERE rmaNumber = '" + objFromClient.get("rmaNumber").toString() + "'");

		if (resultSet.next()) {

			// if exist, Update that query.
			String sql = "UPDATE `rma_table` SET rmaDate = '" + objFromClient.get("rmaDate").toString() + "',"
					+ "rmaOrderNumber = '" + objFromClient.get("rmaOrderNumber").toString() + "'," + "rmaContents = '"
					+ objFromClient.get("rmaContents").toString() + "'," + "rmaBillTo = '"
					+ objFromClient.get("rmaBillTo").toString() + "'," + "rmaShipTo = '"
					+ objFromClient.get("rmaShipTo").toString() + "'," + "rmaTrackingNumber = '"
					+ objFromClient.get("rmaTrackingNumber").toString() + "',siteCode = '" + siteCode
					+ "', user_info = '" + objFromClient.get("USER_ID").toString() + "'" + "WHERE rmaNumber= '"
					+ objFromClient.get("rmaNumber").toString() + "'";

			System.out.println(sql);

			statement.executeUpdate(sql);

		} else {

			// if doesn't exist, Insert information
			String sql = "INSERT INTO `rma_table` (rmaIndex, rmaNumber, rmaDate, rmaOrderNumber, "
					+ "rmaContents, rmaBillTo, rmaShipTo, rmaTrackingNumber, "
					+ "rmaCompanyName, siteCode, user_info) VALUES (?,?,?,?,?,?,?,?,?,?,?)";

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
			pstmt.setString(11, objFromClient.get("USER_ID").toString());

			pstmt.executeUpdate();

		}

		// item 저장은 분리해둠.
		saveRMAItem(objFromClient);

	}

	private void saveRMAItem(JSONObject objFromClient) throws Exception {

		System.out.println("saveRMAItem");

		// RMA number & RMA Item Serial 이 중복되는 경우는 Update하도록.

		ResultSet resultSet;

		String sql = "INSERT INTO `RMAitemTable` (`serialNumber`, `rmaIndex`, `rmaNumber`, `itemName`, "
				+ "`itemDescription`, `itemPrice`, `receive`) VALUES (?,?,?,?,?,?,?)";

		System.out.println(sql);
		pstmt = mySQLconnection.prepareStatement(sql);

		for (int i = 0; i < Integer.parseInt(objFromClient.get("itemCount").toString()); i++) {

			resultSet = statement.executeQuery("SELECT * FROM `RMAitemTable` WHERE rmaNumber = '"
					+ objFromClient.get("rmaNumber").toString() + "' AND " + "serialNumber = '"
					+ objFromClient.get("itemSerialNumber" + i).toString() + "'");

			System.out.println(objFromClient.get("itemSerialNumber" + i).toString());
			System.out.println(objFromClient.get("rmaNumber").toString());
			System.out.println(objFromClient.get("itemName" + i).toString());
			System.out.println("item receive : " + objFromClient.get("itemReceive" + i).toString());

			if (resultSet.next()) {
				// 존재하면 업데이트.

				int itemReceive = 0;
				if (objFromClient.get("itemReceive" + i).toString().equals("true")) {
					itemReceive = 1;
				}

				sql = "UPDATE `rmaItemTable` SET serialNumber = '"
						+ objFromClient.get("itemSerialNumber" + i).toString() + "'," + "rmaIndex = '"
						+ objFromClient.get("rmaNumber").toString().replace("DA", "") + "'," + "rmaNumber = '"
						+ objFromClient.get("rmaNumber").toString() + "'," + "itemName = '"
						+ objFromClient.get("itemName" + i).toString() + "'," + "itemDescription = '"
						+ objFromClient.get("itemDescription" + i).toString() + "'," + "itemPrice = '"
						+ Integer.parseInt(objFromClient.get("itemPrice" + i).toString()) + "',receive = '"
						+ itemReceive + "' " + "WHERE serialNumber= '"
						+ objFromClient.get("itemSerialNumber" + i).toString() + "' AND rmaNumber = '"
						+ objFromClient.get("rmaNumber").toString() + "'";

				System.out.println(sql);

				statement.executeUpdate(sql);

			} else {

				// 존재하지않으면 삽입.
				try {
					pstmt.setString(1, objFromClient.get("itemSerialNumber" + i).toString());
					pstmt.setString(2, (objFromClient.get("rmaNumber").toString()).replace("DA", ""));
					pstmt.setString(3, (objFromClient.get("rmaNumber").toString()));
					pstmt.setString(4, objFromClient.get("itemName" + i).toString());
					pstmt.setString(5, objFromClient.get("itemDescription" + i).toString());
					pstmt.setInt(6, Integer.parseInt(objFromClient.get("itemPrice" + i).toString()));
					pstmt.setBoolean(7, (boolean) objFromClient.get("itemReceive" + i));

					pstmt.executeUpdate();
				} catch (NullPointerException e) {
					// 이건 행에 아이템 없다는 거니까 무시.
				} catch (MySQLIntegrityConstraintViolationException e) {
					System.out.println("제약 조건 위반!");
					e.printStackTrace();
				} catch (Exception e) {

					e.printStackTrace();
				}

			}

		}

	}

	private void searchRealatedRMAnumber() throws Exception {

		System.out.println("searchRealatedRMAnumber");

		String sql = null;

		String siteName = objFromClient.get("siteName").toString();
		String companyName = objFromClient.get("companyName").toString();
		int siteCode = -1;

		if (siteName.equals("")) {

			sql = "select * from rma_table where rmaCompanyName = '" + companyName + "'";

		} else {

			sql = "select siteCode from site where siteName = '" + siteName + "' AND companyName = '" + companyName
					+ "'";

			ResultSet resultSet = statement.executeQuery(sql);

			while (resultSet.next()) {
				siteCode = resultSet.getInt("siteCode");
			}

			sql = "select * from rma_table where rmaCompanyName = '" + companyName + "' AND siteCode = '" + siteCode
					+ "'";
		}

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {

			// 각각의 RMA number를 가진 RMA case들의 정보.
			String rmaNumber = resultSet.getString("rmaNumber");
			String rmaContents = resultSet.getString("rmaContents");
			String rmaDate = resultSet.getString("rmaDate");
			String rmaSiteCode = resultSet.getString("siteCode");

			JSONObject objToClient = new JSONObject();
			objToClient.put("RMAnumber", rmaNumber);
			objToClient.put("RMAcontents", rmaContents);
			objToClient.put("RMAdate", rmaDate);

			// 하나의 RMA case 안에 있는 여러개의 item을 조회한다.
			String rmaNumberSql = "select * from rmaItemTable where rmaNumber = '" + resultSet.getString("rmaNumber")
					+ "'";
			Statement rmaNumberStatement = mySQLconnection.createStatement();
			ResultSet rmaNumberResultSet = rmaNumberStatement.executeQuery(rmaNumberSql);

			System.out.println(rmaNumberSql);

			boolean allItemDelivered = true;

			while (rmaNumberResultSet.next()) {

				String receive = rmaNumberResultSet.getString("receive");

				if (receive != null && receive.equals("0")) {
					allItemDelivered = false;
				}

			}

			objToClient.put("RMAdelivered", allItemDelivered);

			printStream.println(objToClient.toJSONString());

		}

		printStream.println("end");

	}

	private void getSiteNameFromMysql(String prefix, String CompanyName) throws Exception {

		String sql = "SELECT siteName FROM site WHERE siteName LIKE '" + prefix + "%' AND companyName = '" + CompanyName
				+ "'";

		System.out.println(sql);

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

		String sql;
		ResultSet resultSet;

		sql = "SELECT count(*) FROM item WHERE itemName = '" + prefix + "'";

		resultSet = statement.executeQuery(sql);
		int itemCount = 0;
		boolean coinside = false;

		while (resultSet.next()) {

			itemCount = resultSet.getInt("count(*)");

			if (itemCount == 1) {
				coinside = true;
			}

		}

		sql = "SELECT * FROM item WHERE itemName LIKE '%" + prefix + "%'";

		System.out.println(sql);

		resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {

			// Integer itemCode = resultSet.getInt("itemCode");
			String itemName = resultSet.getString("itemName");

			JSONObject itemNameJSON = new JSONObject();
			// itemNameJSON.put("itemCode", itemCode);
			itemNameJSON.put("itemName", itemName);

			if (coinside) {
				String itemDescription = resultSet.getString("itemDescription");
				Integer itemPrice = resultSet.getInt("itemPrice");

				itemNameJSON.put("itemDescription", itemDescription);
				itemNameJSON.put("itemPrice", itemPrice);
				itemNameJSON.put("coinside", true);

			} else {
				itemNameJSON.put("coinside", false);
			}

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
		// printStream.println("end");
	}

	private void getRMADetailFromDatabase(String rmaNumber) throws Exception {

		String sql = "SELECT * FROM rma_table WHERE rmaNumber = '" + rmaNumber + "'";

		System.out.println(sql);

		ResultSet resultSet = statement.executeQuery(sql);

		JSONObject RMADetailJSON = new JSONObject();

		int siteCode = -1;

		while (resultSet.next()) {

			//
			RMADetailJSON.put("rmaNumber", resultSet.getString("rmaNumber"));
			RMADetailJSON.put("rmaDate", resultSet.getString("rmaDate"));
			RMADetailJSON.put("rmaOrderNumber", resultSet.getString("rmaOrderNumber"));
			RMADetailJSON.put("rmaContents", resultSet.getString("rmaContents"));
			RMADetailJSON.put("rmaBillTo", resultSet.getString("rmaBillTo"));
			RMADetailJSON.put("rmaShipTo", resultSet.getString("rmaShipTo"));
			RMADetailJSON.put("rmaTrackingNumber", resultSet.getString("rmaTrackingNumber"));
			siteCode = resultSet.getInt("siteCode");
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

			RMADetailJSON.put("itemName" + i, resultSet.getString("itemName"));
			RMADetailJSON.put("serialNumber" + i, resultSet.getString("SerialNumber"));
			RMADetailJSON.put("itemDescription" + i, resultSet.getString("itemDescription"));
			RMADetailJSON.put("itemPrice" + i, resultSet.getString("itemPrice"));
			RMADetailJSON.put("itemReceive" + i, resultSet.getString("receive"));

			i++;

		}

		RMADetailJSON.put("itemCount", i);

		sql = "SELECT * FROM site WHERE siteCode = '" + siteCode + "'";

		resultSet = statement.executeQuery(sql);

		while (resultSet.next()) {
			RMADetailJSON.put("siteName", resultSet.getString("siteName"));

		}

		getAttachFileCount(rmaNumber, RMADetailJSON);

		printStream.println(RMADetailJSON.toJSONString());

	}

	// 2017.04.24
	// 받아온 rmaNumber값이 현재 rma_table에 존재하는지 여부를 확인함.
	// 이미 사용중이라면 true를 반환함.
	public boolean rmaNumberAlreadyUsed(String rmaNumber) {

		String sql = "SELECT count(*) FROM rma_table WHERE rmaNumber = '" + rmaNumber + "'";
		System.out.println(sql);

		int count = -1;

		try {

			ResultSet resultSet = statement.executeQuery(sql);
			while (resultSet.next()) {
				System.out.println("개수 : " + resultSet.getInt("count(*)"));

				count = resultSet.getInt("count(*)");

			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		if (count >= 1) {
			return true;
		}

		return false;

	}

}
