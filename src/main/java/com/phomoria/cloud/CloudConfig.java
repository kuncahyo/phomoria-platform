package com.phomoria.cloud;

public final class CloudConfig {
    private String server = "http://127.0.0.1:8000";
    private String token = "";
    private boolean rememberLogin = true;
    private String userName = "";
    private String email = "";

    public String getServer() {
        return server == null ? "" : server;
    }

    public void setServer(String server) {
        this.server = server == null ? "" : server.trim();
    }

    public String getToken() {
        return token == null ? "" : token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    public boolean isRememberLogin() {
        return rememberLogin;
    }

    public void setRememberLogin(boolean rememberLogin) {
        this.rememberLogin = rememberLogin;
    }

    public String getUserName() {
        return userName == null ? "" : userName;
    }

    public void setUserName(String userName) {
        this.userName = userName == null ? "" : userName;
    }

    public String getEmail() {
        return email == null ? "" : email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email;
    }
}
