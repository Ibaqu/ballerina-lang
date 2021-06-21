package org.ballerinalang.central.client.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class ModuleResolutionRequestSchema {

    public static final String JSON_PROPERTY_MODULES = "modules";
    @SerializedName(JSON_PROPERTY_MODULES) private List<Modules> modules = new ArrayList<>();

    public ModuleResolutionRequestSchema addModulesItem(Modules modulesItem) {
        this.modules.add(modulesItem);
        return this;
    }
}
