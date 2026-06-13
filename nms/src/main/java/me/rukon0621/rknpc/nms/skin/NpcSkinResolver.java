package me.rukon0621.rknpc.nms.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import me.rukon0621.rknpc.api.npc.NpcSkin;
import me.rukon0621.rknpc.api.npc.NpcSkinType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 설정된 스킨 소스를 GameProfile textures 프로퍼티로 해석한다.
 */
public final class NpcSkinResolver {

    private static final URI PROFILE_API = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final String SESSION_API = "https://sessionserver.mojang.com/session/minecraft/profile/%s?unsigned=false";
    private static final URI MINESKIN_URL_API = URI.create("https://api.mineskin.org/generate/url");
    private static final URI MINESKIN_UPLOAD_API = URI.create("https://api.mineskin.org/generate/upload");
    private static final String USER_AGENT = "RkNpc/1.0";

    private final HttpClient httpClient;
    private final Path pluginFolder;
    private final Path skinCacheFile;
    private final Map<String, CompletableFuture<Optional<Property>>> pendingMineSkinRequests = new ConcurrentHashMap<>();
    private JsonObject skinCache;

    public NpcSkinResolver(HttpClient httpClient, Path pluginFolder) {
        this.httpClient = httpClient;
        this.pluginFolder = pluginFolder;
        this.skinCacheFile = pluginFolder.resolve("skin-cache.json");
        this.skinCache = loadSkinCache();
    }

    public CompletableFuture<Optional<Property>> resolve(NpcSkin skin) {
        if (skin.type() == NpcSkinType.MIRROR) {
            // MIRROR는 PacketNpcRenderer에서 보는 플레이어별로 처리한다.
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return switch (skin.type()) {
            case NAME -> resolveName(skin.value());
            case URL -> resolveUrl(skin.value());
            case IMAGE -> resolveImage(skin.value());
            case MIRROR -> CompletableFuture.completedFuture(Optional.empty());
        };
    }

    private CompletableFuture<Optional<Property>> resolveName(String name) {
        HttpRequest uuidRequest = HttpRequest.newBuilder(PROFILE_API.resolve(name))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        return httpClient.sendAsync(uuidRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenCompose(response -> {
                    if (response.statusCode() != 200 || response.body().isBlank()) {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }
                    String uuid = JsonParser.parseString(response.body()).getAsJsonObject().get("id").getAsString();
                    HttpRequest profileRequest = HttpRequest.newBuilder(URI.create(SESSION_API.formatted(uuid)))
                            .timeout(Duration.ofSeconds(5))
                            .GET()
                            .build();
                    return httpClient.sendAsync(profileRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                            .thenApply(profileResponse -> {
                                if (profileResponse.statusCode() != 200 || profileResponse.body().isBlank()) {
                                    return Optional.<Property>empty();
                                }
                                JsonObject profile = JsonParser.parseString(profileResponse.body()).getAsJsonObject();
                                for (var property : profile.getAsJsonArray("properties")) {
                                    JsonObject object = property.getAsJsonObject();
                                    if ("textures".equals(object.get("name").getAsString())) {
                                        String signature = object.has("signature") ? object.get("signature").getAsString() : null;
                                        return Optional.of(new Property("textures", object.get("value").getAsString(), signature));
                                    }
                                }
                                return Optional.<Property>empty();
                            });
                });
    }

    private CompletableFuture<Optional<Property>> resolveUrl(String url) {
        String cacheKey = "url:" + sha256(url.getBytes(StandardCharsets.UTF_8));
        Optional<Property> cached = readCachedTexture(cacheKey);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        return pendingMineSkinRequests.computeIfAbsent(cacheKey, key -> requestUrlSkin(url)
                .thenApply(texture -> {
                    texture.ifPresent(property -> writeCachedTexture(key, property));
                    return texture;
                })
                .whenComplete((ignored, throwable) -> pendingMineSkinRequests.remove(key)));
    }

    private CompletableFuture<Optional<Property>> requestUrlSkin(String url) {
        String body = "{\"url\":\"" + escapeJson(url) + "\",\"visibility\":0}";
        HttpRequest request = HttpRequest.newBuilder(MINESKIN_URL_API)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::readMineSkinTexture)
                .exceptionally(throwable -> Optional.empty());
    }

    private CompletableFuture<Optional<Property>> resolveImage(String fileName) {
        return CompletableFuture.supplyAsync(() -> {
            Path imagePath = pluginFolder.resolve("skins").resolve(fileName).normalize();
            // IMAGE 경로는 플러그인 skins 폴더 내부로 제한한다.
            if (!imagePath.startsWith(pluginFolder.resolve("skins").normalize())) {
                return Optional.empty();
            }
            if (!Files.isRegularFile(imagePath)) {
                return Optional.empty();
            }
            try {
                byte[] bytes = Files.readAllBytes(imagePath);
                String cacheKey = "image:" + sha256(bytes);
                Optional<Property> cached = readCachedTexture(cacheKey);
                if (cached.isPresent()) {
                    return cached;
                }
                // 같은 이미지가 동시에 로딩될 때 MineSkin 요청이 중복되지 않도록 진행 중인 작업을 공유한다.
                return pendingMineSkinRequests.computeIfAbsent(cacheKey, key -> requestImageSkin(fileName, bytes)
                        .thenApply(texture -> {
                            texture.ifPresent(property -> writeCachedTexture(key, property));
                            return texture;
                        })
                        .whenComplete((ignored, throwable) -> pendingMineSkinRequests.remove(key))).join();
            } catch (IOException e) {
                return Optional.empty();
            }
        });
    }

    private CompletableFuture<Optional<Property>> requestImageSkin(String fileName, byte[] bytes) {
        String boundary = multipartBoundary(fileName);
        HttpRequest request = HttpRequest.newBuilder(MINESKIN_UPLOAD_API)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(multipartBody(fileName, bytes, boundary))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::readMineSkinTexture)
                .exceptionally(throwable -> Optional.empty());
    }

    private Optional<Property> readMineSkinTexture(HttpResponse<String> response) {
        if (response.statusCode() != 200 || response.body().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject texture = findMineSkinTexture(root);
            if (texture == null || !texture.has("value") || !texture.has("signature")) {
                return Optional.empty();
            }
            return Optional.of(new Property(
                    "textures",
                    texture.get("value").getAsString(),
                    texture.get("signature").getAsString()
            ));
        } catch (IllegalStateException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private JsonObject findMineSkinTexture(JsonObject root) {
        JsonObject data = object(root, "data");
        JsonObject texture = object(data, "texture");
        if (texture != null) {
            return texture;
        }
        JsonObject rootTexture = object(root, "texture");
        return object(rootTexture, "data");
    }

    private JsonObject object(JsonObject root, String key) {
        if (root == null || !root.has(key) || !root.get(key).isJsonObject()) {
            return null;
        }
        return root.getAsJsonObject(key);
    }

    private HttpRequest.BodyPublisher multipartBody(String fileName, byte[] fileBytes, String boundary) {
        List<byte[]> parts = new ArrayList<>();
        addPart(parts, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"visibility\"\r\n\r\n"
                + "0\r\n");
        addPart(parts, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + escapeHeader(fileName) + "\"\r\n"
                + "Content-Type: image/png\r\n\r\n");
        parts.add(fileBytes);
        addPart(parts, "\r\n--" + boundary + "--\r\n");
        return HttpRequest.BodyPublishers.ofByteArrays(parts);
    }

    private synchronized Optional<Property> readCachedTexture(String key) {
        if (!skinCache.has(key) || !skinCache.get(key).isJsonObject()) {
            return Optional.empty();
        }
        JsonObject texture = skinCache.getAsJsonObject(key);
        if (!texture.has("value") || !texture.has("signature")) {
            return Optional.empty();
        }
        return Optional.of(new Property(
                "textures",
                texture.get("value").getAsString(),
                texture.get("signature").getAsString()
        ));
    }

    private synchronized void writeCachedTexture(String key, Property property) {
        JsonObject texture = new JsonObject();
        texture.addProperty("value", property.value());
        texture.addProperty("signature", property.signature());
        skinCache.add(key, texture);
        saveSkinCache();
    }

    private JsonObject loadSkinCache() {
        if (!Files.isRegularFile(skinCacheFile)) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(Files.readString(skinCacheFile, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | IllegalStateException e) {
            return new JsonObject();
        }
    }

    private void saveSkinCache() {
        try {
            Files.createDirectories(pluginFolder);
            Files.writeString(skinCacheFile, skinCache.toString(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private void addPart(List<byte[]> parts, String value) {
        parts.add(value.getBytes(StandardCharsets.UTF_8));
    }

    private String multipartBoundary(String fileName) {
        return "RkNpcSkin" + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(fileName.getBytes(StandardCharsets.UTF_8));
    }

    private String escapeHeader(String value) {
        return value.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }
}
