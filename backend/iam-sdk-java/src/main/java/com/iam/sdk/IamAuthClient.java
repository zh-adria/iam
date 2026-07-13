package com.iam.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

public class IamAuthClient {
    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public IamAuthClient(String baseUrl) {
        this(URI.create(baseUrl), HttpClient.newHttpClient(), new ObjectMapper());
    }

    public IamAuthClient(URI baseUri, HttpClient httpClient, ObjectMapper objectMapper) {
        String normalized = baseUri.toString();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.baseUri = URI.create(normalized);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public URI authorizeUrl(String clientId, String redirectUri, String scope, String state,
                            String codeChallenge, String nonce) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("response_type", "code");
        params.put("client_id", clientId);
        params.put("redirect_uri", redirectUri);
        params.put("scope", scope);
        params.put("state", state);
        params.put("code_challenge", codeChallenge);
        params.put("code_challenge_method", codeChallenge == null ? null : "S256");
        params.put("nonce", nonce);
        return resolve("/oauth/authorize?" + form(params));
    }

    public IamTokenResponse authorizationCode(String clientId, String clientSecret,
                                              String code, String redirectUri, String codeVerifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("code", code);
        form.put("redirect_uri", redirectUri);
        form.put("code_verifier", codeVerifier);
        return postForm("/oauth/token", form, IamTokenResponse.class);
    }

    public IamTokenResponse clientCredentials(String clientId, String clientSecret, String scope) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("scope", scope);
        return postForm("/oauth/token", form, IamTokenResponse.class);
    }

    public IamTokenResponse refresh(String clientId, String clientSecret, String refreshToken) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        form.put("refresh_token", refreshToken);
        return postForm("/oauth/token", form, IamTokenResponse.class);
    }

    public IamIntrospectionResponse introspect(String token, String clientId, String clientSecret) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("token", token);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        return postForm("/oauth/introspect", form, IamIntrospectionResponse.class);
    }

    public void revoke(String token, String clientId, String clientSecret) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("token", token);
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        postForm("/oauth/revoke", form, Map.class);
    }

    public IamAuthorizationCheckResponse checkPermission(String accessToken, String permission, Object target) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("permission", permission);
        body.put("target", target);
        Map<?, ?> envelope = postJson("/api/authz/check", accessToken, body, Map.class);
        Object success = envelope.get("success");
        if (Boolean.FALSE.equals(success)) {
            throw new IamSdkException("IAM authorization check failed: " + envelope.get("message"));
        }
        return objectMapper.convertValue(envelope.get("data"), IamAuthorizationCheckResponse.class);
    }

    private <T> T postForm(String path, Map<String, String> params, Class<T> responseType) {
        HttpRequest request = HttpRequest.newBuilder(resolve(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IamSdkException("IAM request failed: HTTP " + response.statusCode() + " " + response.body());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new IamSdkException("Failed to parse IAM response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IamSdkException("IAM request interrupted", e);
        }
    }

    private <T> T postJson(String path, String bearerToken, Object body, Class<T> responseType) {
        try {
            HttpRequest request = HttpRequest.newBuilder(resolve(path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + bearerToken)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IamSdkException("IAM request failed: HTTP " + response.statusCode() + " " + response.body());
            }
            return objectMapper.readValue(response.body(), responseType);
        } catch (IOException e) {
            throw new IamSdkException("Failed to parse IAM response", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IamSdkException("IAM request interrupted", e);
        }
    }

    private URI resolve(String path) {
        return URI.create(baseUri + path);
    }

    private static String form(Map<String, String> params) {
        StringJoiner joiner = new StringJoiner("&");
        params.forEach((key, value) -> {
            if (value != null) {
                joiner.add(urlEncode(key) + "=" + urlEncode(value));
            }
        });
        return joiner.toString();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
