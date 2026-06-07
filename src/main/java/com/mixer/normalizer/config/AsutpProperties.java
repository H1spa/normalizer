package com.mixer.normalizer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "asutp")
public class AsutpProperties {
    private boolean enabled;
    private String url1;
    private String url2;
    private String url3;
    private String method1 = "POST";
    private String method2 = "POST";
    private String method3 = "POST";
    private String domainName;
    private String password;
    private String lastHouseId;
    private String domainNameField = "domainName";
    private String passwordField = "password";
    private String lastHouseIdField = "LastHouseId";
    private String authBody;
    private String contextBody;
    private String dataBody;
    private String tokenHeader = "Authorization";
    private String tokenScheme = "Bearer";
    private boolean tokenBase64Encode;
    private String tokenFields = "token,data.token";
    private String tagListFields = "data,items,result";
    private String tagIdFields = "tg_id,tgId,id";
    private String tagValueField = "value";
    private String gateTags;
    private String tiltTags;
    private String gateOpenValue = "1";
    private double tiltThreshold;
    private int connectTimeoutMillis = 10000;
    private int readTimeoutMillis = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl1() {
        return url1;
    }

    public void setUrl1(String url1) {
        this.url1 = url1;
    }

    public String getUrl2() {
        return url2;
    }

    public void setUrl2(String url2) {
        this.url2 = url2;
    }

    public String getUrl3() {
        return url3;
    }

    public void setUrl3(String url3) {
        this.url3 = url3;
    }

    public String getMethod1() {
        return method1;
    }

    public void setMethod1(String method1) {
        this.method1 = method1;
    }

    public String getMethod2() {
        return method2;
    }

    public void setMethod2(String method2) {
        this.method2 = method2;
    }

    public String getMethod3() {
        return method3;
    }

    public void setMethod3(String method3) {
        this.method3 = method3;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLastHouseId() {
        return lastHouseId;
    }

    public void setLastHouseId(String lastHouseId) {
        this.lastHouseId = lastHouseId;
    }

    public String getDomainNameField() {
        return domainNameField;
    }

    public void setDomainNameField(String domainNameField) {
        this.domainNameField = domainNameField;
    }

    public String getPasswordField() {
        return passwordField;
    }

    public void setPasswordField(String passwordField) {
        this.passwordField = passwordField;
    }

    public String getLastHouseIdField() {
        return lastHouseIdField;
    }

    public void setLastHouseIdField(String lastHouseIdField) {
        this.lastHouseIdField = lastHouseIdField;
    }

    public String getAuthBody() {
        return authBody;
    }

    public void setAuthBody(String authBody) {
        this.authBody = authBody;
    }

    public String getContextBody() {
        return contextBody;
    }

    public void setContextBody(String contextBody) {
        this.contextBody = contextBody;
    }

    public String getDataBody() {
        return dataBody;
    }

    public void setDataBody(String dataBody) {
        this.dataBody = dataBody;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        this.tokenHeader = tokenHeader;
    }

    public String getTokenScheme() {
        return tokenScheme;
    }

    public void setTokenScheme(String tokenScheme) {
        this.tokenScheme = tokenScheme;
    }

    public boolean isTokenBase64Encode() {
        return tokenBase64Encode;
    }

    public void setTokenBase64Encode(boolean tokenBase64Encode) {
        this.tokenBase64Encode = tokenBase64Encode;
    }

    public String getTokenFields() {
        return tokenFields;
    }

    public void setTokenFields(String tokenFields) {
        this.tokenFields = tokenFields;
    }

    public String getTagListFields() {
        return tagListFields;
    }

    public void setTagListFields(String tagListFields) {
        this.tagListFields = tagListFields;
    }

    public String getTagIdFields() {
        return tagIdFields;
    }

    public void setTagIdFields(String tagIdFields) {
        this.tagIdFields = tagIdFields;
    }

    public String getTagValueField() {
        return tagValueField;
    }

    public void setTagValueField(String tagValueField) {
        this.tagValueField = tagValueField;
    }

    public String getGateTags() {
        return gateTags;
    }

    public void setGateTags(String gateTags) {
        this.gateTags = gateTags;
    }

    public String getTiltTags() {
        return tiltTags;
    }

    public void setTiltTags(String tiltTags) {
        this.tiltTags = tiltTags;
    }

    public String getGateOpenValue() {
        return gateOpenValue;
    }

    public void setGateOpenValue(String gateOpenValue) {
        this.gateOpenValue = gateOpenValue;
    }

    public double getTiltThreshold() {
        return tiltThreshold;
    }

    public void setTiltThreshold(double tiltThreshold) {
        this.tiltThreshold = tiltThreshold;
    }

    public int getConnectTimeoutMillis() {
        return connectTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    public int getReadTimeoutMillis() {
        return readTimeoutMillis;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public Map<Integer, String> getGateTagMap() {
        return parseTagMap(gateTags);
    }

    public Map<Integer, String> getTiltTagMap() {
        return parseTagMap(tiltTags);
    }

    public Map<String, Object> getAuthBodyMap() {
        return parseBodyMap(authBody);
    }

    public Map<String, Object> getContextBodyMap() {
        return parseBodyMap(contextBody);
    }

    public Map<String, Object> getDataBodyMap() {
        return parseBodyMap(dataBody);
    }

    public List<String> getTokenFieldList() {
        return parseList(tokenFields);
    }

    public List<String> getTagListFieldList() {
        return parseList(tagListFields);
    }

    public List<String> getTagIdFieldList() {
        return parseList(tagIdFields);
    }

    private static Map<Integer, String> parseTagMap(String value) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String part : value.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) {
                continue;
            }
            result.put(Integer.parseInt(pair[0].trim()), pair[1].trim());
        }
        return result;
    }

    private static Map<String, Object> parseBodyMap(String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String part : value.split(",")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || pair[0].isBlank()) {
                continue;
            }
            result.put(pair[0].trim(), pair[1].trim());
        }
        return result;
    }

    private static List<String> parseList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }

        for (String part : value.split(",")) {
            String item = part.trim();
            if (!item.isBlank()) {
                result.add(item);
            }
        }
        return result;
    }
}
