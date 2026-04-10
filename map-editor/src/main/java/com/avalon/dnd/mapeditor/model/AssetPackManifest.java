package com.avalon.dnd.mapeditor.model;

import java.util.ArrayList;
import java.util.List;

public class AssetPackManifest {

    private String packId;
    private String name;
    private String description;
    private final List<AssetDefinition> assets = new ArrayList<>();

    public String getPackId() { return packId; }
    public void setPackId(String packId) { this.packId = packId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<AssetDefinition> getAssets() { return assets; }
}
