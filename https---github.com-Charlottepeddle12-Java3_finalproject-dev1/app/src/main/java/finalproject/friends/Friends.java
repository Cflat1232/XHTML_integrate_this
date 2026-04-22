package finalproject.friends;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import finalproject.Users.UserLogin;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("friendsBean")
@SessionScoped
public class Friends implements Serializable {
	private Connection conn;
	private String sendFriendUserName;
	private String acceptFriendUserName;
	private String sendMessage;
	private String acceptMessage;
	private List<String> receivedFriendRequests = new ArrayList<>();
	private List<String> friendNames = new ArrayList<>();
	private String receivedRequestsMessage;
	private int friendCount;
	private List<String> sentFriendRequests = new ArrayList<>();

	@Inject
	private UserLogin login;

	@PostConstruct
	public void openConnection() {
		try {
			Context ctx = new InitialContext();
			DataSource ds = (DataSource) ctx.lookup("java:/comp/env/jdbc/java_project");
			conn = ds.getConnection();
		} catch (NamingException | SQLException e) {
			sendMessage = e.getMessage();
		}
	}

	@PreDestroy
	public void closeConnection() {
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				sendMessage = e.getMessage();
			}
		}
	}

	public void sendFriendRequest() {
		if (login == null || login.getUserId() <= 0) {
			sendMessage = "You must be logged in to send a friend request.";
			return;
		}
		if (sendFriendUserName == null || sendFriendUserName.isBlank()) {
			sendMessage = "Enter a username.";
			return;
		}
		try (PreparedStatement findUser = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
			findUser.setString(1, sendFriendUserName);
			try (ResultSet userResult = findUser.executeQuery()) {
				if (!userResult.next()) {
					sendMessage = "Enter correct username.";
					return;
				}
				int friendId = userResult.getInt("userID");
				if (friendId == login.getUserId()) {
					sendMessage = "You cannot send a friend request to yourself.";
					return;
				}
				try (PreparedStatement checkBlock = conn.prepareStatement(
						"SELECT 1 FROM blocks WHERE (userID = ? AND blockedID = ?) OR (userID = ? AND blockedID = ?)")) {
					checkBlock.setInt(1, login.getUserId());
					checkBlock.setInt(2, friendId);
					checkBlock.setInt(3, friendId);
					checkBlock.setInt(4, login.getUserId());
					try (ResultSet blockResult = checkBlock.executeQuery()) {
						if (blockResult.next()) {
							sendMessage = "You cannot send a friend request to this user.";
							return;
						}
					}
				}
				try (PreparedStatement checkExisting = conn.prepareStatement(
						"SELECT requesterID, addresseeID, status FROM friends "
								+ "WHERE (requesterID = ? AND addresseeID = ?) OR (requesterID = ? AND addresseeID = ?)")) {
					checkExisting.setInt(1, login.getUserId());
					checkExisting.setInt(2, friendId);
					checkExisting.setInt(3, friendId);
					checkExisting.setInt(4, login.getUserId());
					try (ResultSet existing = checkExisting.executeQuery()) {
						if (existing.next()) {
							String status = existing.getString("status");
							int requesterId = existing.getInt("requesterID");
							int addresseeId = existing.getInt("addresseeID");
							if ("ACCEPTED".equalsIgnoreCase(status)) {
								sendMessage = "You are already friends.";
								return;
							}
							if (requesterId == login.getUserId() && addresseeId == friendId) {
								sendMessage = "Friend request already sent.";
								return;
							}
							if (requesterId == friendId && addresseeId == login.getUserId()) {
								sendMessage = "This user already sent you a request. Accept it instead.";
								return;
							}
						}
					}
				}

				try (PreparedStatement insertRequest = conn.prepareStatement(
						"INSERT INTO friends (requesterID, addresseeID, status) VALUES (?, ?, 'PENDING')")) {
					insertRequest.setInt(1, login.getUserId());
					insertRequest.setInt(2, friendId);
					int rowsAffected = insertRequest.executeUpdate();
					if (rowsAffected == 1) {
						sendMessage = "Friend request sent to " + sendFriendUserName + ".";
					} else {
						sendMessage = "Friend request failed.";
					}
				}
			}
		} catch (SQLException e) {
			sendMessage = e.getMessage();
		}
		loadFriendNames();
		loadReceivedFriendRequests();
		loadSentFriendRequests();
	}

	public void acceptFriendRequest() {
		if (login == null || login.getUserId() <= 0) {
			acceptMessage = "You must be logged in to accept a friend request.";
			return;
		}
		if (acceptFriendUserName == null || acceptFriendUserName.isBlank()) {
			acceptMessage = "Enter a username.";
			return;
		}
		try (PreparedStatement findUser = conn.prepareStatement("SELECT id FROM users WHERE username = ?")) {
			findUser.setString(1, acceptFriendUserName);
			try (ResultSet userResult = findUser.executeQuery()) {
				if (!userResult.next()) {
					acceptMessage = "Enter correct username.";
					return;
				}
				int senderId = userResult.getInt("userID");
				if (senderId == login.getUserId()) {
					acceptMessage = "You cannot accept your own friend request.";
					return;
			}
			try (PreparedStatement checkBlock = conn.prepareStatement(
					"SELECT 1 FROM blocks WHERE (userID = ? AND blockedID = ?) OR (userID = ? AND blockedID = ?)")) {
				checkBlock.setInt(1, login.getUserId());
				checkBlock.setInt(2, senderId);
				checkBlock.setInt(3, senderId);
				checkBlock.setInt(4, login.getUserId());
				try (ResultSet blockResult = checkBlock.executeQuery()) {
					if (blockResult.next()) {
						acceptMessage = "You cannot accept a friend request from this user.";
						return;
					}
				}
			}
			try (PreparedStatement acceptRequest = conn.prepareStatement(
						"UPDATE friends SET status = 'ACCEPTED', accepted_at = CURRENT_TIMESTAMP "
								+ "WHERE requesterID = ? AND addresseeID = ? AND status = 'PENDING'")) {
					acceptRequest.setInt(1, senderId);
					acceptRequest.setInt(2, login.getUserId());
					int rowsAffected = acceptRequest.executeUpdate();

					if (rowsAffected == 1) {
						acceptMessage = "Friend request accepted from " + acceptFriendUserName + ".";
						return;
					}
				}
				try (PreparedStatement existingFriendship = conn.prepareStatement(
						"SELECT status FROM friends "
								+ "WHERE (requesterID = ? AND addresseeID = ?) OR (requesterID = ? AND addresseeID = ?)")) {
					existingFriendship.setInt(1, login.getUserId());
					existingFriendship.setInt(2, senderId);
					existingFriendship.setInt(3, senderId);
					existingFriendship.setInt(4, login.getUserId());

					try (ResultSet statusResult = existingFriendship.executeQuery()) {
						if (statusResult.next() && "ACCEPTED".equalsIgnoreCase(statusResult.getString("status"))) {
							acceptMessage = "You are already friends.";
							return;
						}
					}
				}
				acceptMessage = "No incoming friend request from " + acceptFriendUserName + ".";
			}
		} catch (SQLException e) {
			acceptMessage = e.getMessage();
		}
		loadFriendNames();
		loadReceivedFriendRequests();
		loadSentFriendRequests();
	}

	public void loadReceivedFriendRequests() {
		receivedFriendRequests.clear();
		receivedRequestsMessage = null;
		if (login == null || login.getUserId() <= 0) {
			return;
		}
		try (PreparedStatement pendingRequests = conn.prepareStatement(
				"SELECT users.username "
						+ "FROM friends JOIN users ON friends.requesterID = users.userID "
						+ "WHERE friends.addresseeID = ? AND friends.status = 'PENDING' "
						+ "ORDER BY users.username")) {
			pendingRequests.setInt(1, login.getUserId());
			try (ResultSet result = pendingRequests.executeQuery()) {
				while (result.next()) {
					receivedFriendRequests.add(result.getString("username"));
				}
			}
		} catch (SQLException e) {
			receivedRequestsMessage = e.getMessage();
		}
	}

	public void loadFriendCount() {
		loadFriendNames();
	}

	public void loadFriendNames() {
		friendNames.clear();
		friendCount = 0;
		if (login == null || login.getUserId() <= 0) {
			return;
		}
		try (PreparedStatement friendsQuery = conn.prepareStatement(
				"SELECT CASE WHEN requesterID = ? THEN addresseeUsers.username ELSE requesterUsers.username END AS friendUsername "
						+ "FROM friends "
						+ "JOIN users requesterUsers ON friends.requesterID = requesterUsers.userID "
						+ "JOIN users addresseeUsers ON friends.addresseeID = addresseeUsers.userID "
						+ "WHERE friends.status = 'ACCEPTED' AND (friends.requesterID = ? OR friends.addresseeID = ?) "
						+ "ORDER BY friendUsername")) {
			friendsQuery.setInt(1, login.getUserId());
			friendsQuery.setInt(2, login.getUserId());
			friendsQuery.setInt(3, login.getUserId());
			try (ResultSet result = friendsQuery.executeQuery()) {
				while (result.next()) {
					friendNames.add(result.getString("friendUsername"));
				}
			}
			friendCount = friendNames.size();
		} catch (SQLException e) {
			receivedRequestsMessage = e.getMessage();
		}
	}

	public void declineFriendRequest() {
		if (login == null || login.getUserId() <= 0) {
			acceptMessage = "You must be logged in.";
			return;
		}

		if (acceptFriendUserName == null || acceptFriendUserName.isBlank()) {
			acceptMessage = "Enter a username.";
			return;
		}

		try (PreparedStatement findUser = conn.prepareStatement(
				"SELECT id FROM users WHERE username = ?")) {

			findUser.setString(1, acceptFriendUserName);

			try (ResultSet rs = findUser.executeQuery()) {
				if (!rs.next()) {
					acceptMessage = "User not found.";
					return;
				}

				int senderId = rs.getInt("userID");

				try (PreparedStatement deleteRequest = conn.prepareStatement(
						"DELETE FROM friends WHERE requesterID = ? AND addresseeID = ? AND status = 'PENDING'")) {

					deleteRequest.setInt(1, senderId);
					deleteRequest.setInt(2, login.getUserId());

					int rows = deleteRequest.executeUpdate();

					if (rows == 1) {
						acceptMessage = "Friend request declined.";
					} else {
						acceptMessage = "No request to decline.";
					}
				}
			}

		} catch (SQLException e) {
			acceptMessage = e.getMessage();
		}

		loadFriendNames();
		loadReceivedFriendRequests();
		loadSentFriendRequests();
	}
	public void loadSentFriendRequests() {
		sentFriendRequests.clear();

		if (login == null || login.getUserId() <= 0) {
			return;
		}

		try (PreparedStatement stmt = conn.prepareStatement(
				"SELECT users.username " +
				"FROM friends JOIN users ON friends.addresseeID = users.userID " +
				"WHERE friends.requesterID = ? AND friends.status = 'PENDING' " +
				"ORDER BY users.username")) {

			stmt.setInt(1, login.getUserId());

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					sentFriendRequests.add(rs.getString("username"));
				}
			}

		} catch (SQLException e) {
			sendMessage = e.getMessage();
		}
	}

	public void cancelSentFriendRequest(String username) {
		if (login == null || login.getUserId() <= 0) {
			sendMessage = "You must be logged in.";
			return;
		}

		try (PreparedStatement findUser = conn.prepareStatement(
				"SELECT id FROM users WHERE username = ?")) {

			findUser.setString(1, username);

			try (ResultSet rs = findUser.executeQuery()) {
				if (!rs.next()) {
					sendMessage = "User not found.";
					return;
				}

				int friendId = rs.getInt("userID");

				try (PreparedStatement delete = conn.prepareStatement(
						"DELETE FROM friends WHERE requesterID = ? AND addresseeID = ? AND status = 'PENDING'")) {

					delete.setInt(1, login.getUserId());
					delete.setInt(2, friendId);

					int rows = delete.executeUpdate();

					if (rows == 1) {
						sendMessage = "Friend request cancelled.";
					} else {
						sendMessage = "No pending request found.";
					}
				}
			}

		} catch (SQLException e) {
			sendMessage = e.getMessage();
		}

		loadFriendNames();
		loadReceivedFriendRequests();
		loadSentFriendRequests();
	}
	public String getSendFriendUserName() {
		return sendFriendUserName;
	}

	public List<String> getSentFriendRequests() {
		return sentFriendRequests;
	}

	public void setSendFriendUserName(String sendFriendUserName) {
		this.sendFriendUserName = sendFriendUserName;
	}

	public String getAcceptFriendUserName() {
		return acceptFriendUserName;
	}

	public void setAcceptFriendUserName(String acceptFriendUserName) {
		this.acceptFriendUserName = acceptFriendUserName;
	}

	public String getSendMessage() {
		return sendMessage;
	}

	public String getAcceptMessage() {
		return acceptMessage;
	}

	public List<String> getReceivedFriendRequests() {
		return receivedFriendRequests;
	}

	public String getReceivedRequestsMessage() {
		return receivedRequestsMessage;
	}

	public int getFriendCount() {
		return friendCount;
	}

	public List<String> getFriendNames() {
		return friendNames;
	}
}

 