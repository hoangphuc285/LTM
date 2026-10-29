package client.model;

public class SiteProfile {
    private String id;
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    private boolean anonymous;

    public SiteProfile() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.name = "Site mới";
        this.host = "127.0.0.1";
        this.port = 2121;
        this.username = "user01";
        this.password = "123456";
        this.anonymous = false;
    }

    public SiteProfile(String id, String name, String host, int port, String username, String password, boolean anonymous) {
        this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.anonymous = anonymous;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }

    @Override
    public String toString() {
        return name + " (" + host + ":" + port + ")";
    }
}

