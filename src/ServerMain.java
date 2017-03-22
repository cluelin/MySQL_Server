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

	// ������ �ڱ� port number�� �˰������� ��.
	final public static int SERVER_PORT = 9090;

	// mySQL����ϱ����� �غ�.
	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static final String DB_URL = "jdbc:mysql://localhost:3306/RMA_DATABASE";

	// mySQL ����� �α���
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
					printStream = new PrintStream(client.getOutputStream());

					JSONParser jsonParser = new JSONParser();
					JSONObject obj;
					String input = bufferedReader.readLine();

					if (input != null) {
						// ������ action ����.
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

	// ������ ���� ����
	private void checkAction(JSONObject obj) throws Exception {

		String actionStr = obj.get("Action").toString();

		System.out.println("action : " + actionStr);

		if (actionStr == null) {
			// Action ������.
			return;
		} else if (actionStr.equals("requestRMAindex")) {
			getRMAindex();
		} else if (actionStr.equals("requestSaveRMAData")) {
			// RMA information ����
			updateRMAInformation(obj);
		} else if (actionStr.equals("requestSearchRelatedRMA")) {
			searchRealatedRMAnumber(obj);
		}
	}

	// ������ RMA number ��ȯ.
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
		// ���� �ʿ�. update�ÿ� companyName�� siteName�� not null�̸鼭 foreign key�̱⶧����
		// �̸� ����Ǿ��ִ� �̸��� ����ؼ� ����صд�..
		// ��������� �ش� RMAnumber�� ��������ʾҴٸ� �����ϴ°͵� ����ؾ��Ѵ�.
		String sql = "INSERT INTO `rma_table` (rmaDate, rmaOrderNumber, "
				+ "rmaContents, rmaBillTo, rmaShipTo, rmaTrackingNumber, "
				+ "rmaCompanyName, rmaCompanySite) VALUES ('','','','','','','companyName','SiteName')";

		statement.executeUpdate(sql);

	}

	// ����Ʈ�� ������ �߰�. ������ �Ѿ��.
	private void saveSiteExist(JSONObject obj) throws Exception {

		// sql�� ���� �غ�.
		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement
				.executeQuery("SELECT * FROM `site` where siteName = '" + obj.get("companySiteName").toString() + "'");

		if (!resultSet.next()) {

			System.out.println("����Ʈ����� , " + obj.get("companySiteName").toString());
			String sql = "INSERT INTO `site` VALUES(?)";

			pstmt = mySQLconnection.prepareStatement(sql);
			pstmt.setString(1, obj.get("companySiteName").toString());
			pstmt.executeUpdate();
		}

	}

	// ���۴� ���� ������ ����, ������ �Ѿ..
	private void saveCompanyInformation(JSONObject obj) throws Exception {

		saveSiteExist(obj);

		statement = mySQLconnection.createStatement();

		ResultSet resultSet = statement.executeQuery(
				"SELECT * FROM `company` WHERE companyName = '" + obj.get("companyName").toString() + "'");

		// ������ ����.
		if (resultSet.next()) {
			System.out.println("MySQL : companyName �̹� ����. ");
			return;
		}

		// ������ �߰�.

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

	// ���� ����
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

		// Item ���� ����

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
