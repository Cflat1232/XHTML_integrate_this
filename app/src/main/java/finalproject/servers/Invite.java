package finalproject.servers;

import java.io.Serializable;

public class Invite implements Serializable {
    private int inviteID;
    private String serverName;
    private String inviterName;

    public int getInviteID() {
        return inviteID;
    }
    public void setInviteID(int inviteID) {
        this.inviteID = inviteID;
    }
    public String getServerName() {
        return serverName;
    }
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }
    public String getInviterName() {
        return inviterName;
    }
    public void setInviterName(String inviterName) {
        this.inviterName = inviterName;
    }
}
