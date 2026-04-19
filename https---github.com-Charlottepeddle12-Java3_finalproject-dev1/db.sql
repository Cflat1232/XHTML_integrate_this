DROP DATABASE IF EXISTS javaproject;
CREATE DATABASE IF NOT EXISTS javaproject;
USE javaproject;

-- Drop tables in correct order (reverse dependency order)
DROP TABLE IF EXISTS channel_role_permissions;
DROP TABLE IF EXISTS server_member_roles;
DROP TABLE IF EXISTS messages;
DROP TABLE IF EXISTS direct_conversations;
DROP TABLE IF EXISTS channels;
DROP TABLE IF EXISTS server_roles;
DROP TABLE IF EXISTS server_invites;
DROP TABLE IF EXISTS server_members;
DROP TABLE IF EXISTS blocks;
DROP TABLE IF EXISTS friends;
DROP TABLE IF EXISTS servers;
DROP TABLE IF EXISTS users;

-- Users table (no foreign keys)
CREATE TABLE IF NOT EXISTS `users` (
  `userID` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `PW_Hash` binary(60) NOT NULL,
  `token` varchar(64) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`userID`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `token` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- Friends table (ON DELETE CASCADE - delete friend records when user deleted)
CREATE TABLE IF NOT EXISTS `friends` (
  `requesterID` int(11) NOT NULL,
  `addresseeID` int(11) NOT NULL,
  `status` ENUM('PENDING','ACCEPTED') NOT NULL DEFAULT 'PENDING',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `accepted_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`requesterID`, `addresseeID`),
  CONSTRAINT `fk_friends_requester` FOREIGN KEY (`requesterID`) REFERENCES `users` (`userID`) ON DELETE CASCADE ON UPDATE NO ACTION,
  CONSTRAINT `fk_friends_addressee` FOREIGN KEY (`addresseeID`) REFERENCES `users` (`userID`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- Blocks table (ON DELETE CASCADE - delete block records when user deleted)

CREATE TABLE IF NOT EXISTS blocks (
    userID INT NOT NULL,
    blockedID INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (userID, blockedID),
    CONSTRAINT fk_blocks_user
        FOREIGN KEY (userID) REFERENCES users(userID)
        ON DELETE CASCADE
        ON UPDATE NO ACTION,
    CONSTRAINT fk_blocks_blocked
        FOREIGN KEY (blockedID) REFERENCES users(userID)
        ON DELETE CASCADE
        ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- Servers table: supports public/private, owner is admin (references users.userID)
CREATE TABLE IF NOT EXISTS servers (
  serverID INT(11) NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) UNIQUE NOT NULL,
  ownerID INT(11) NULL,
  is_public BOOLEAN NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (serverID),
  KEY servers_owner_fk (ownerID),
  CONSTRAINT servers_owner_fk
    FOREIGN KEY (ownerID) REFERENCES users(userID) 
    ON DELETE SET NULL
    ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- Server members: tracks user membership, role, and permissions
DROP TABLE IF EXISTS server_members;
CREATE TABLE server_members (
  userID INT NOT NULL,
  serverID INT NOT NULL,
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (userID, serverID),
  KEY idx_server_members_serverID (serverID),
  CONSTRAINT fk_server_members_user
    FOREIGN KEY (userID) REFERENCES users(userID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_server_members_server
    FOREIGN KEY (serverID) REFERENCES servers(serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DROP TABLE IF EXISTS server_roles;
CREATE TABLE server_roles (
  roleID INT NOT NULL AUTO_INCREMENT,
  serverID INT NOT NULL,
  role_name VARCHAR(50) NOT NULL,
  is_system_role BOOLEAN NOT NULL DEFAULT FALSE,
  is_default_role BOOLEAN NOT NULL DEFAULT FALSE,
  can_invite BOOLEAN NOT NULL DEFAULT FALSE,
  can_kick BOOLEAN NOT NULL DEFAULT FALSE,
  can_create_channel BOOLEAN NOT NULL DEFAULT FALSE,
  can_manage_roles BOOLEAN NOT NULL DEFAULT FALSE,
  can_delete_messages BOOLEAN NOT NULL DEFAULT FALSE,
  can_delete_server BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (roleID),
  UNIQUE KEY uq_server_roles_name (serverID, role_name),
  UNIQUE KEY uq_server_roles_role_server (roleID, serverID),
  KEY idx_server_roles_serverID (serverID),
  CONSTRAINT fk_server_roles_server
    FOREIGN KEY (serverID) REFERENCES servers(serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DROP TABLE IF EXISTS server_member_roles;
CREATE TABLE server_member_roles (
  userID INT NOT NULL,
  serverID INT NOT NULL,
  roleID INT NOT NULL,
  assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (userID, serverID, roleID),
  KEY idx_server_member_roles_roleID (roleID),
  CONSTRAINT fk_server_member_roles_member
    FOREIGN KEY (userID, serverID) REFERENCES server_members(userID, serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_server_member_roles_role
    FOREIGN KEY (roleID, serverID) REFERENCES server_roles(roleID, serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

-- Server invites: tracks pending invites for private servers
DROP TABLE IF EXISTS server_invites;
CREATE TABLE server_invites (
  inviteID INT NOT NULL AUTO_INCREMENT,
  serverID INT NOT NULL,
  invitedID INT NOT NULL,
  invited_by INT NOT NULL,
  invite_status ENUM('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED') NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (inviteID),
  UNIQUE KEY uq_server_invites_target (serverID, invitedID),
  KEY idx_server_invites_inviter (invited_by),
  CONSTRAINT fk_server_invites_server
    FOREIGN KEY (serverID) REFERENCES servers(serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_server_invites_invited
    FOREIGN KEY (invitedID) REFERENCES users(userID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_server_invites_invited_by
    FOREIGN KEY (invited_by, serverID) REFERENCES server_members(userID, serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DROP TABLE IF EXISTS channels;
CREATE TABLE channels (
  channelID INT NOT NULL AUTO_INCREMENT,
  serverID INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  created_by INT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (channelID),
  UNIQUE KEY uq_channels_server_name (serverID, name),
  UNIQUE KEY uq_channels_channel_server (channelID, serverID),
  KEY idx_channels_creator (created_by),
  CONSTRAINT fk_channels_server
    FOREIGN KEY (serverID) REFERENCES servers(serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_channels_created_by
    FOREIGN KEY (created_by) REFERENCES users(userID)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DROP TABLE IF EXISTS channel_role_permissions;
CREATE TABLE channel_role_permissions (
  serverID INT NOT NULL,
  channelID INT NOT NULL,
  roleID INT NOT NULL,
  can_read BOOLEAN NOT NULL DEFAULT TRUE,
  can_write BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (channelID, roleID),
  CONSTRAINT fk_channel_role_permissions_channel
    FOREIGN KEY (channelID, serverID) REFERENCES channels(channelID, serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_channel_role_permissions_role
    FOREIGN KEY (roleID, serverID) REFERENCES server_roles(roleID, serverID)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DROP TABLE IF EXISTS direct_conversations;
CREATE TABLE direct_conversations (
  conversationID INT NOT NULL AUTO_INCREMENT,
  userOneID INT NOT NULL,
  userTwoID INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (conversationID),
  UNIQUE KEY uq_direct_conversations_pair (userOneID, userTwoID),
  CONSTRAINT fk_direct_conversations_user_one
    FOREIGN KEY (userOneID) REFERENCES users(userID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_direct_conversations_user_two
    FOREIGN KEY (userTwoID) REFERENCES users(userID)
    ON DELETE CASCADE
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DROP TABLE IF EXISTS messages;
CREATE TABLE messages (
  messageID INT NOT NULL AUTO_INCREMENT,
  channelID INT NULL,
  conversationID INT NULL,
  senderID INT NULL,
  message_text TEXT NULL,
  image_data LONGBLOB NULL,
  image_mime_type VARCHAR(100) NULL,
  sentOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  edited_at TIMESTAMP NULL DEFAULT NULL,
  deleted_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (messageID),
  KEY idx_messages_channel_sentOn (channelID, sentOn),
  KEY idx_messages_conversation_sentOn (conversationID, sentOn),
  KEY idx_messages_senderID (senderID),
  CONSTRAINT fk_messages_channel
    FOREIGN KEY (channelID) REFERENCES channels(channelID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_messages_conversation
    FOREIGN KEY (conversationID) REFERENCES direct_conversations(conversationID)
    ON DELETE CASCADE
    ON UPDATE CASCADE,
  CONSTRAINT fk_messages_sender
    FOREIGN KEY (senderID) REFERENCES users(userID)
    ON DELETE SET NULL
    ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;

DELIMITER $$

CREATE TRIGGER trg_servers_after_insert_bootstrap
AFTER INSERT ON servers
FOR EACH ROW
BEGIN
  INSERT INTO server_roles (
    serverID,
    role_name,
    is_system_role,
    is_default_role,
    can_invite,
    can_kick,
    can_create_channel,
    can_manage_roles,
    can_delete_messages,
    can_delete_server
  ) VALUES
    (NEW.serverID, 'Admin', TRUE, FALSE, TRUE, TRUE, TRUE, TRUE, TRUE, TRUE),
    (NEW.serverID, 'Member', TRUE, TRUE, FALSE, FALSE, FALSE, FALSE, FALSE, FALSE);

  INSERT INTO server_members (userID, serverID)
  VALUES (NEW.ownerID, NEW.serverID);

  INSERT INTO server_member_roles (userID, serverID, roleID)
  SELECT NEW.ownerID, NEW.serverID, roleID
  FROM server_roles
  WHERE serverID = NEW.serverID AND role_name = 'Admin';
END$$

CREATE TRIGGER trg_server_members_after_insert_assign_default_role
AFTER INSERT ON server_members
FOR EACH ROW
BEGIN
  INSERT IGNORE INTO server_member_roles (userID, serverID, roleID)
  SELECT NEW.userID, NEW.serverID, roleID
  FROM server_roles
  WHERE serverID = NEW.serverID AND is_default_role = TRUE;
END$$

CREATE TRIGGER trg_blocks_after_insert_remove_friendship
AFTER INSERT ON blocks
FOR EACH ROW
BEGIN
  DELETE FROM friends
  WHERE (requesterID = NEW.userID AND addresseeID = NEW.blockedID)
     OR (requesterID = NEW.blockedID AND addresseeID = NEW.userID);
END$$

-- 1. Prevent self-friend
CREATE TRIGGER trg_friends_no_self
BEFORE INSERT ON friends
FOR EACH ROW
BEGIN
  IF NEW.requesterID = NEW.addresseeID THEN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Cannot friend yourself';
  END IF;
END$$

-- 2. Prevent self-block
CREATE TRIGGER trg_blocks_no_self
BEFORE INSERT ON blocks
FOR EACH ROW
BEGIN
  IF NEW.userID = NEW.blockedID THEN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Cannot block yourself';
  END IF;
END$$

-- 3. Enforce conversation ordering
CREATE TRIGGER trg_direct_conversations_order
BEFORE INSERT ON direct_conversations
FOR EACH ROW
BEGIN
  IF NEW.userOneID >= NEW.userTwoID THEN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'userOneID must be less than userTwoID';
  END IF;
END$$

-- 4. Validate message target (channel XOR conversation)
CREATE TRIGGER trg_messages_target
BEFORE INSERT ON messages
FOR EACH ROW
BEGIN
  IF NOT (
    (NEW.channelID IS NOT NULL AND NEW.conversationID IS NULL) OR
    (NEW.channelID IS NULL AND NEW.conversationID IS NOT NULL)
  ) THEN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Message must have either channelID or conversationID (not both)';
  END IF;
END$$

-- 5. Validate message payload
CREATE TRIGGER trg_messages_payload
BEFORE INSERT ON messages
FOR EACH ROW
BEGIN
  IF NEW.message_text IS NULL AND NEW.image_data IS NULL THEN
    SIGNAL SQLSTATE '45000'
    SET MESSAGE_TEXT = 'Message must contain text or image';
  END IF;
END$$

DELIMITER ;
;