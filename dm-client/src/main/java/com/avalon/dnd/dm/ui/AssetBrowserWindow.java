package com.avalon.dnd.dm.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class AssetBrowserWindow {

    private static final Map<String, Image> THUMBNAIL_CACHE = new java.util.LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > 256;
        }
    };

    private AssetBrowserWindow() {}

    public static void showTokenBrowser(Window owner, List<JsonNode> catalog, Consumer<JsonNode> onSelect) {
        show(owner, "Token Explorer", catalog, AssetBrowserWindow::isTokenAsset, onSelect, true, 1080, 720, Modality.WINDOW_MODAL);
    }

    public static void showObjectBrowser(Window owner, List<JsonNode> catalog, Consumer<JsonNode> onSelect) {
        show(owner, "Object Browser", catalog, AssetBrowserWindow::isObjectAsset, onSelect, true, 1120, 760, Modality.WINDOW_MODAL);
    }

    private static void show(Window owner,
                             String title,
                             List<JsonNode> catalog,
                             Predicate<JsonNode> filter,
                             Consumer<JsonNode> onSelect,
                             boolean closeOnSelect,
                             double width,
                             double height,
                             Modality modality) {
        Stage stage = new Stage();
        stage.setTitle(title);

        Modality effectiveModality = modality;
        if (owner == null && modality == Modality.WINDOW_MODAL) {
            effectiveModality = Modality.NONE;
        }

        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(effectiveModality);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));

        TextField searchField = new TextField();
        searchField.setPromptText("Search assets...");

        TreeView<String> categoryTree = new TreeView<>();
        categoryTree.setShowRoot(true);
        categoryTree.setPrefWidth(260);

        ListView<JsonNode> assetList = new ListView<>();
        assetList.setCellFactory(list -> new AssetCell());

        Label countLabel = new Label();
        Label hintLabel = new Label("Double-click an asset to select it");

        List<JsonNode> rawAssets = catalog == null ? List.of() : catalog.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing((JsonNode a) -> safeCategory(assetCategory(a)), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(a -> safeText(assetName(a)), String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<JsonNode> renderableAssets = rawAssets.stream()
                .filter(AssetBrowserWindow::isRenderableAsset)
                .toList();
        List<JsonNode> allAssets = !renderableAssets.isEmpty() ? renderableAssets : rawAssets;

        List<JsonNode> filteredByType = allAssets.stream().filter(filter).toList();
        if (!allAssets.isEmpty() && filteredByType.isEmpty()) {
            filteredByType = allAssets;
        }
        List<JsonNode> browserAssets = filteredByType;

        TreeItem<String> rootItem = buildCategoryTree(browserAssets);
        categoryTree.setRoot(rootItem);
        if (rootItem != null) {
            categoryTree.getSelectionModel().select(rootItem);
        }

        Runnable refreshList = () -> {
            String search = safeText(searchField.getText()).toLowerCase(Locale.ROOT);
            TreeItem<String> selectedItem = categoryTree.getSelectionModel().getSelectedItem();
            String selectedCategory = selectedItem == null ? "" : selectedCategoryPath(selectedItem);
            List<JsonNode> filtered = new ArrayList<>();
            for (JsonNode asset : browserAssets) {
                if (!selectedCategory.isBlank() && !categoryMatches(asset, selectedCategory)) {
                    continue;
                }
                if (!search.isBlank() && !matchesSearch(asset, search)) {
                    continue;
                }
                filtered.add(asset);
            }
            assetList.setItems(FXCollections.observableArrayList(filtered));
            countLabel.setText(filtered.size() + " asset(s)");
            if (!filtered.isEmpty() && assetList.getSelectionModel().getSelectedItem() == null) {
                assetList.getSelectionModel().selectFirst();
            }
        };

        searchField.textProperty().addListener((obs, oldV, newV) -> refreshList.run());
        categoryTree.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> refreshList.run());

        assetList.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() >= 2) {
                JsonNode selected = assetList.getSelectionModel().getSelectedItem();
                if (selected != null && onSelect != null) {
                    onSelect.accept(selected);
                    if (closeOnSelect) {
                        stage.close();
                    }
                }
            }
        });

        Button chooseButton = new Button("Select");
        chooseButton.setDefaultButton(true);
        chooseButton.setOnAction(e -> {
            JsonNode selected = assetList.getSelectionModel().getSelectedItem();
            if (selected != null && onSelect != null) {
                onSelect.accept(selected);
                if (closeOnSelect) {
                    stage.close();
                }
            }
        });

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());

        HBox bottomBar = new HBox(10, countLabel, new Separator(), hintLabel, new Separator(), chooseButton, closeButton);
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(10, 0, 0, 0));
        HBox.setHgrow(countLabel, Priority.NEVER);
        HBox.setHgrow(hintLabel, Priority.ALWAYS);

        VBox left = new VBox(8, new Label("Catalogs"), categoryTree);
        left.setPrefWidth(260);
        VBox.setVgrow(categoryTree, Priority.ALWAYS);

        VBox center = new VBox(8, new Label("Assets"), assetList, bottomBar);
        VBox.setVgrow(assetList, Priority.ALWAYS);

        root.setTop(searchField);
        BorderPane.setMargin(searchField, new Insets(0, 0, 10, 0));
        root.setLeft(left);
        root.setCenter(center);

        Scene scene = new Scene(root, width, height);
        stage.setScene(scene);
        refreshList.run();
        stage.show();
        stage.toFront();
        stage.requestFocus();
    }

    private static TreeItem<String> buildCategoryTree(List<JsonNode> assets) {
        TreeItem<String> root = new TreeItem<>("All");
        root.setExpanded(true);
        Map<String, TreeItem<String>> nodes = new LinkedHashMap<>();
        nodes.put("", root);

        for (JsonNode asset : assets) {
            String[] segments = splitCategoryPath(assetCategory(asset));
            String path = "";
            TreeItem<String> parent = root;
            for (String segment : segments) {
                if (segment.isBlank()) continue;
                path = path.isBlank() ? segment : path + "/" + segment;
                TreeItem<String> node = nodes.get(path.toLowerCase(Locale.ROOT));
                if (node == null) {
                    node = new TreeItem<>(segment);
                    parent.getChildren().add(node);
                    nodes.put(path.toLowerCase(Locale.ROOT), node);
                }
                parent = node;
            }
        }

        root.getChildren().sort(Comparator.comparing(TreeItem::getValue, String.CASE_INSENSITIVE_ORDER));
        sortRecursively(root);
        return root;
    }

    private static void sortRecursively(TreeItem<String> item) {
        item.getChildren().sort(Comparator.comparing(TreeItem::getValue, String.CASE_INSENSITIVE_ORDER));
        for (TreeItem<String> child : item.getChildren()) {
            sortRecursively(child);
        }
    }

    private static String selectedCategoryPath(TreeItem<String> selected) {
        if (selected == null) {
            return "";
        }
        if (selected.getParent() == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        TreeItem<String> current = selected;
        while (current != null && current.getParent() != null) {
            parts.add(0, current.getValue());
            current = current.getParent();
        }
        return String.join("/", parts).toLowerCase(Locale.ROOT);
    }

    private static String[] splitCategoryPath(String category) {
        if (category == null || category.isBlank()) {
            return new String[] { "Uncategorized" };
        }
        return category.trim().split("\\s*[\\/\\\\>|:]\\s*");
    }


    private static boolean categoryMatches(JsonNode asset, String selectedPath) {
        if (selectedPath == null || selectedPath.isBlank()) {
            return true;
        }
        String category = safeCategory(assetCategory(asset)).toLowerCase(Locale.ROOT);
        return category.equals(selectedPath) || category.startsWith(selectedPath + "/") || category.contains(selectedPath);
    }

    private static boolean matchesSearch(JsonNode asset, String search) {
        return contains(assetName(asset), search)
                || contains(assetCategory(asset), search)
                || contains(assetId(asset), search)
                || contains(assetKind(asset), search)
                || contains(assetImageUrl(asset), search);
    }

    private static boolean contains(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    private static boolean isTokenAsset(JsonNode asset) {
        String kind = assetKind(asset);
        if (kind == null) return false;
        String lower = kind.toLowerCase(Locale.ROOT);
        return lower.contains("token") || lower.contains("spawn");
    }

    private static boolean isObjectAsset(JsonNode asset) {
        String kind = assetKind(asset);
        String category = assetCategory(asset);
        String probe = ((kind == null ? "" : kind) + " " + (category == null ? "" : category)).toLowerCase(Locale.ROOT);
        return probe.contains("object") || probe.contains("decor") || probe.contains("wall") || probe.contains("door") || probe.contains("terrain") || probe.contains("furniture") || probe.contains("scenery") || probe.contains("container") || probe.contains("prop");
    }

    private static String safeCategory(String category) {
        return category == null || category.isBlank() ? "Uncategorized" : category.trim();
    }

    private static String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private static String assetId(JsonNode node) {
        return firstText(node, "id", "assetId", "key", "slug", "fileName", "filename");
    }

    private static String assetName(JsonNode node) {
        return firstText(node, "name", "title", "displayName", "label", "caption");
    }

    private static String assetCategory(JsonNode node) {
        return firstText(node, "category", "group", "pack", "kind", "type");
    }

    private static String assetKind(JsonNode node) {
        return firstText(node, "kind", "type", "placementKind");
    }

    private static String assetImageUrl(JsonNode node) {
        return firstText(node, "imageUrl", "imagePath", "image", "path", "file", "src", "url", "filename", "fileName", "assetPath", "sprite", "thumbnail");
    }

    private static boolean isRenderableAsset(JsonNode node) {
        String image = assetImageUrl(node);
        if (image == null || image.isBlank()) {
            return false;
        }
        String cleaned = image.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        return !cleaned.endsWith("/");
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) return null;
        for (String key : keys) {
            if (node.hasNonNull(key)) {
                String text = node.get(key).asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String sizeText(JsonNode node) {
        int gridSize = intValue(node, 1, "gridSize");
        int width = intValue(node, 1, "width", "defaultWidth", "w");
        int height = intValue(node, 1, "height", "defaultHeight", "h");
        if (gridSize > 1) {
            return gridSize + "×" + gridSize;
        }
        return width + "×" + height;
    }

    private static int intValue(JsonNode node, int defaultValue, String... keys) {
        if (node == null || !node.isObject()) return defaultValue;
        for (String key : keys) {
            if (node.hasNonNull(key)) {
                try {
                    return node.get(key).asInt(defaultValue);
                } catch (Exception ignored) {
                }
            }
        }
        return defaultValue;
    }

    private static Image loadThumbnailImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        String resolved = resolveImageSource(imageUrl.trim());
        if (resolved == null || resolved.isBlank()) {
            return null;
        }

        synchronized (THUMBNAIL_CACHE) {
            Image cached = THUMBNAIL_CACHE.get(resolved);
            if (cached != null && !cached.isError()) {
                return cached;
            }
        }

        try {
            Image image = new Image(resolved, 42, 42, true, true, true);
            if (image.isError()) {
                return null;
            }
            synchronized (THUMBNAIL_CACHE) {
                THUMBNAIL_CACHE.put(resolved, image);
            }
            return image;
        } catch (IllegalArgumentException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String resolveImageSource(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("jar:") ||
                trimmed.startsWith("file:") || trimmed.startsWith("data:")) {
            return encodeUrl(trimmed);
        }

        String cleaned = trimmed.replace('\\', '/');
        String noSlash = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        if (!isCatalogAssetCandidate(noSlash)) {
            return null;
        }

        for (String candidate : candidateAssetPaths(noSlash)) {
            Path local = resolveProjectPath(candidate);
            if (local != null) {
                return local.toUri().toString();
            }
        }

        return null;
    }

    private static java.util.List<String> candidateAssetPaths(String noSlash) {
        java.util.ArrayList<String> candidates = new java.util.ArrayList<>();
        if (noSlash.startsWith("uploads/assets/tokens/") || noSlash.startsWith("uploads/assets/objects/")) {
            candidates.add(noSlash);
        } else {
            candidates.add("uploads/assets/tokens/" + noSlash);
            candidates.add("uploads/assets/objects/" + noSlash);
        }
        return candidates;
    }

    private static boolean isCatalogAssetCandidate(String noSlash) {
        if (noSlash == null || noSlash.isBlank()) {
            return false;
        }
        String lower = noSlash.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".tif") || lower.endsWith(".tiff");
    }

    private static Path resolveProjectPath(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }

        String cleaned = relative.startsWith("/") ? relative.substring(1) : relative;
        Path projectRoot = findProjectRoot();
        if (projectRoot != null) {
            Path candidate = projectRoot.resolve(cleaned).normalize();
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        Path direct = Path.of(cleaned);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        return null;
    }


    private static Path findProjectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path current = cwd; current != null; current = current.getParent()) {
            if ((Files.exists(current.resolve("settings.gradle"))
                    || Files.exists(current.resolve("settings.gradle.kts"))
                    || Files.exists(current.resolve("gradlew"))
                    || Files.exists(current.resolve("gradlew.bat")))
                    && Files.isDirectory(current.resolve("uploads/assets"))) {
                return current;
            }
        }
        return null;
    }

    private static String encodeUrl(String url) {
        if (url == null) return null;
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) {
                return url;
            }
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) {
                return url;
            }

            String base = url.substring(0, pathStart);
            String path = url.substring(pathStart);
            String[] segments = path.split("/", -1);
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) sb.append('/');
                sb.append(encodePathSegment(segments[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return url;
        }
    }

    private static String encodePathSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return segment == null ? "" : segment;
        }
        try {
            return new URI(null, null, "/" + segment, null).toASCIIString().substring(1);
        } catch (Exception e) {
            byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int v = b & 0xFF;
                if ((v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z') ||
                        (v >= '0' && v <= '9') || v == '-' || v == '_' || v == '.' || v == '~' || v == '+') {
                    sb.append((char) v);
                } else {
                    sb.append(String.format("%%%02X", v));
                }
            }
            return sb.toString();
        }
    }

    private static final class AssetCell extends ListCell<JsonNode> {
        private final ImageView imageView = new ImageView();
        private final Label title = new Label();
        private final Label subtitle = new Label();
        private final VBox textBox = new VBox(2, title, subtitle);
        private final HBox root = new HBox(10, imageView, textBox);

        private AssetCell() {
            imageView.setFitWidth(42);
            imageView.setFitHeight(42);
            imageView.setPreserveRatio(true);
            title.setStyle("-fx-font-weight: bold;");
            subtitle.setStyle("-fx-text-fill: -fx-text-inner-color; -fx-opacity: 0.7;");
            root.setAlignment(Pos.CENTER_LEFT);
            textBox.setFillWidth(true);
        }

        @Override
        protected void updateItem(JsonNode item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                imageView.setImage(null);
                return;
            }

            String name = assetName(item);
            title.setText((name == null || name.isBlank()) ? Objects.toString(assetId(item), "Unnamed asset") : name);
            subtitle.setText((assetKind(item) == null ? "" : assetKind(item)) + "  •  " + safeCategory(assetCategory(item)) + "  •  " + sizeText(item));
            imageView.setImage(loadThumbnailImage(assetImageUrl(item)));
            setText(null);
            setGraphic(root);
        }
    }
}
