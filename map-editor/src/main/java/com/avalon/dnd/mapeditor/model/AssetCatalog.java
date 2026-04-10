package com.avalon.dnd.mapeditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AssetCatalog {

    private final List<AssetDefinition> assets = new ArrayList<>();

    public List<AssetDefinition> getAssets() {
        return Collections.unmodifiableList(assets);
    }

    public void setAssets(List<AssetDefinition> definitions) {
        assets.clear();
        if (definitions != null) {
            assets.addAll(definitions);
        }
    }

    public void add(AssetDefinition asset) {
        if (asset != null) {
            assets.add(asset);
        }
    }

    public Optional<AssetDefinition> findById(String id) {
        if (id == null) return Optional.empty();
        return assets.stream().filter(a -> id.equals(a.getId())).findFirst();
    }

    public Optional<AssetDefinition> findByName(String name) {
        if (name == null) return Optional.empty();
        return assets.stream().filter(a -> name.equalsIgnoreCase(a.getName())).findFirst();
    }
}
