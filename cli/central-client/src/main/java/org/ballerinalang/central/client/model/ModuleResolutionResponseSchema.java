package org.ballerinalang.central.client.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ModuleResolutionResponseSchema {

    public static final String JSON_RESOLVED_MODULES = "resolvedModules";
    @SerializedName(JSON_RESOLVED_MODULES) private List<ResolvedModules> resolvedModules = new ArrayList<>();


    public static final String JSON_UNRESOLVED_MODULES = "unresolvedModules";
    @SerializedName(JSON_RESOLVED_MODULES) private List<ResolvedModules> unresolvedModules = new ArrayList<>();

    public List<ResolvedModules> getResolvedModules() {
        return resolvedModules;
    }
}


