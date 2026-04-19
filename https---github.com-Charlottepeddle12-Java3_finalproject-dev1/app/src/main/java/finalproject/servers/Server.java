package finalproject.servers;

public class Server {    
    private String name;
    private int ownerID;
    private boolean isPublic;
    private int serverID;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getOwnerID() { return ownerID; }
    public void setOwnerID(int ownerID) { this.ownerID = ownerID; }

    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public int getServerID() { return serverID; }
    public void setServerID(int serverID) { this.serverID = serverID; }

    public String getTypeLabel() {
        return isPublic ? "Public" : "Private";
    }
    public boolean getPublicServer() {
        return isPublic;
    }
}