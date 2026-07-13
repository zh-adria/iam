package com.iam.sdk;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class IamAuthorizationCheckResponse {
    private boolean allowed;
    private String permission;
    private Long uid;
    private String sub;
    private String tenant;
    private final Map<String, Object> extra = new HashMap<>();

    public boolean isAllowed() {
        return allowed;
    }

    public String getPermission() {
        return permission;
    }

    public Long getUid() {
        return uid;
    }

    public String getSub() {
        return sub;
    }

    public String getTenant() {
        return tenant;
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
