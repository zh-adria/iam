package com.iam.sdk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IamIntrospectionResponse {
    private boolean active;
    @JsonProperty("client_id")
    private String clientId;
    private String sub;
    private String scope;
    private Long exp;
    private Long uid;
    private String tenant;
    private List<String> roles;
    private List<String> perms;
    private final Map<String, Object> extra = new HashMap<>();

    public boolean isActive() {
        return active;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSub() {
        return sub;
    }

    public String getScope() {
        return scope;
    }

    public Long getExp() {
        return exp;
    }

    public Long getUid() {
        return uid;
    }

    public String getTenant() {
        return tenant;
    }

    public List<String> getRoles() {
        return roles == null ? List.of() : roles;
    }

    public List<String> getPerms() {
        return perms == null ? List.of() : perms;
    }

    @JsonAnySetter
    public void putExtra(String key, Object value) {
        extra.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getExtra() {
        return extra;
    }
}
