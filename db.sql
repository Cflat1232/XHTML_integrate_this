-- H2 Database Schema for Java Project
-- Compatible with H2 embedded database

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
CREATE TABLE users (
  userID INT IDENTITY PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  PW_Hash BINARY(60) NOT NULL,
  token VARCHAR(64) NOT NULL UNIQUE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Friends table (ON DELETE CASCADE - delete friend records when user deleted)
CREATE TABLE friends (
  requesterID INT NOT NULL,
  addresseeID INT NOT NULL,
  status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  accepted_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (requesterID, addresseeID),
  FOREIGN KEY (requesterID) REFERENCES users(userID) ON DELETE CASCADE,
  FOREIGN KEY (addresseeID) REFERENCES users(userID) ON DELETE CASCADE
);

-- Blocks table (ON DELETE CASCADE - delete block records when user deleted)
CREATE TABLE blocks (
    userID INT NOT NULL,
    blockedID INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (userID, blockedID),
    FOREIGN KEY (userID) REFERENCES users(userID) ON DELETE CASCADE,
    FOREIGN KEY (blockedID) REFERENCES users(userID) ON DELETE CASCADE
);

-- Servers table: supports public/private, owner is admin (references users.userID)
CREATE TABLE servers (
  serverID INT IDENTITY PRIMARY KEY,
  name VARCHAR(100) UNIQUE NOT NULL,
  ownerID INT NULL,
  is_public BOOLEAN NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ownerID) REFERENCES users(userID) ON DELETE SET NULL
);

-- Server members: tracks user membership, role, and permissions
CREATE TABLE server_members (
  userID INT NOT NULL,
  serverID INT NOT NULL,
  joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (userID, serverID),
  FOREIGN KEY (userID) REFERENCES users(userID) ON DELETE CASCADE,
  FOREIGN KEY (serverID) REFERENCES servers(serverID) ON DELETE CASCADE
);

CREATE TABLE server_roles (
  roleID INT IDENTITY PRIMARY KEY,
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
  UNIQUE (serverID, role_name),
  FOREIGN KEY (serverID) REFERENCES servers(serverID) ON DELETE CASCADE
);

CREATE TABLE server_member_roles (
  userID INT NOT NULL,
  serverID INT NOT NULL,
  roleID INT NOT NULL,
  assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (userID, serverID, roleID),
  FOREIGN KEY (userID, serverID) REFERENCES server_members(userID, serverID) ON DELETE CASCADE,
  FOREIGN KEY (roleID, serverID) REFERENCES server_roles(roleID, serverID) ON DELETE CASCADE
);

-- Server invites: tracks pending invites for private servers
CREATE TABLE server_invites (
  inviteID INT IDENTITY PRIMARY KEY,
  serverID INT NOT NULL,
  invitedID INT NOT NULL,
  invited_by INT NOT NULL,
  invite_status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (invite_status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED')),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  responded_at TIMESTAMP NULL DEFAULT NULL,
  UNIQUE (serverID, invitedID),
  FOREIGN KEY (serverID) REFERENCES servers(serverID) ON DELETE CASCADE,
  FOREIGN KEY (invitedID) REFERENCES users(userID) ON DELETE CASCADE,
  FOREIGN KEY (invited_by, serverID) REFERENCES server_members(userID, serverID) ON DELETE CASCADE
);

CREATE TABLE channels (
  channelID INT IDENTITY PRIMARY KEY,
  serverID INT NOT NULL,
  name VARCHAR(100) NOT NULL,
  created_by INT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (serverID, name),
  FOREIGN KEY (serverID) REFERENCES servers(serverID) ON DELETE CASCADE,
  FOREIGN KEY (created_by) REFERENCES users(userID) ON DELETE SET NULL
);

CREATE TABLE channel_role_permissions (
  serverID INT NOT NULL,
  channelID INT NOT NULL,
  roleID INT NOT NULL,
  can_read BOOLEAN NOT NULL DEFAULT TRUE,
  can_write BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (channelID, roleID),
  FOREIGN KEY (channelID, serverID) REFERENCES channels(channelID, serverID) ON DELETE CASCADE,
  FOREIGN KEY (roleID, serverID) REFERENCES server_roles(roleID, serverID) ON DELETE CASCADE
);

CREATE TABLE direct_conversations (
  conversationID INT IDENTITY PRIMARY KEY,
  userOneID INT NOT NULL,
  userTwoID INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (userOneID, userTwoID),
  FOREIGN KEY (userOneID) REFERENCES users(userID) ON DELETE CASCADE,
  FOREIGN KEY (userTwoID) REFERENCES users(userID) ON DELETE CASCADE
);

CREATE TABLE messages (
  messageID INT IDENTITY PRIMARY KEY,
  channelID INT NULL,
  conversationID INT NULL,
  senderID INT NULL,
  message_text CLOB NULL,
  image_data BLOB NULL,
  image_mime_type VARCHAR(100) NULL,
  sentOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  edited_at TIMESTAMP NULL DEFAULT NULL,
  deleted_at TIMESTAMP NULL DEFAULT NULL,
  FOREIGN KEY (channelID) REFERENCES channels(channelID) ON DELETE CASCADE,
  FOREIGN KEY (conversationID) REFERENCES direct_conversations(conversationID) ON DELETE CASCADE,
  FOREIGN KEY (senderID) REFERENCES users(userID) ON DELETE SET NULL,
  CHECK ((channelID IS NOT NULL AND conversationID IS NULL) OR (channelID IS NULL AND conversationID IS NOT NULL)),
  CHECK (message_text IS NOT NULL OR image_data IS NOT NULL)
);

CREATE TABLE direct_conversations (
  conversationID INT IDENTITY PRIMARY KEY,
  userOneID INT NOT NULL,
  userTwoID INT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (userOneID, userTwoID),
  FOREIGN KEY (userOneID) REFERENCES users(userID) ON DELETE CASCADE,
  FOREIGN KEY (userTwoID) REFERENCES users(userID) ON DELETE CASCADE
);

CREATE TABLE messages (
  messageID INT IDENTITY PRIMARY KEY,
  channelID INT NULL,
  conversationID INT NULL,
  senderID INT NULL,
  message_text CLOB NULL,
  image_data BLOB NULL,
  image_mime_type VARCHAR(100) NULL,
  sentOn TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  edited_at TIMESTAMP NULL DEFAULT NULL,
  deleted_at TIMESTAMP NULL DEFAULT NULL,
  FOREIGN KEY (channelID) REFERENCES channels(channelID) ON DELETE CASCADE,
  FOREIGN KEY (conversationID) REFERENCES direct_conversations(conversationID) ON DELETE CASCADE,
  FOREIGN KEY (senderID) REFERENCES users(userID) ON DELETE SET NULL,
  CHECK ((channelID IS NOT NULL AND conversationID IS NULL) OR (channelID IS NULL AND conversationID IS NOT NULL)),
  CHECK (message_text IS NOT NULL OR image_data IS NOT NULL)
);