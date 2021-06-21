package org.ballerinalang.central.client.model;

import com.google.gson.annotations.SerializedName;

public class Modules {

    public static final String JSON_PROPERTY_ORGANIZATION = "organization";
    @SerializedName(JSON_PROPERTY_ORGANIZATION) private String organization;

    public static final String JSON_PROPERTY_NAME = "moduleName";
    @SerializedName(JSON_PROPERTY_NAME) private String moduleName;

    public static final String JSON_PROPERTY_VERSION = "version";
    @SerializedName(JSON_PROPERTY_VERSION) private String version;

    public Modules(String organization, String moduleName, String version) {
        this.organization = organization;
        this.moduleName = moduleName;
        this.version = version;
    }

    public void setOrganization(String organization) {
        this.organization = organization;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getOrganization() {
        return organization;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getVersion() {
        return version;
    }
}
