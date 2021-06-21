package org.ballerinalang.central.client.model;

import com.google.gson.annotations.SerializedName;

public class ResolvedModules {

    public static final String JSON_PROPERTY_ORGANIZATION = "organization";
    public static final String JSON_PROPERTY_NAME = "moduleName";
    public static final String JSON_PROPERTY_VERSION = "version";
    public static final String JSON_PACKAGE_NAME = "packageName";

    @SerializedName(JSON_PROPERTY_ORGANIZATION)
    private String organization;

    @SerializedName(JSON_PROPERTY_NAME)
    private String moduleName;

    @SerializedName(JSON_PROPERTY_VERSION)
    private String version;

    @SerializedName(JSON_PACKAGE_NAME)
    private String packageVersion;

    public String getOrganization() {
        return organization;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getVersion() {
        return version;
    }

    public String getPackageVersion() {
        return packageVersion;
    }
}
