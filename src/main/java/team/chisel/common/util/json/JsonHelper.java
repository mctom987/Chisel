package team.chisel.common.util.json;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import team.chisel.Chisel;
import team.chisel.ctm.api.texture.ICTMTexture;
import team.chisel.ctm.api.texture.IChiselFace;

public class JsonHelper {
    
    private static RuntimeException cachedException;

    private static final Gson gson = new Gson();

    private static Map<ResourceLocation, JsonObject> objectCache = new HashMap<>();
    private static Map<ResourceLocation, IChiselFace> faceCache = new HashMap<>();
    private static Map<ResourceLocation, ICTMTexture<?>> textureCache = new HashMap<>();
    
    public static final String FACE_EXTENSION = ".cf";
    public static final String TEXTURE_EXTENSION = ".ctx";
    public static final JsonObject NORMAL_TEXTURE = gson.fromJson("{\"type\": \"NORMAL\"}", JsonObject.class);
    public static final String NORMAL_FACE = "{\"textures\":[\".%s\"]}";

    private static IChiselFace createFace(ResourceLocation loc) {
        if (isValidFace(loc)) {
            JsonObject object = objectCache.get(loc);
            JsonFace face = gson.fromJson(object, JsonFace.class);
            IChiselFace cFace = face.get(loc);
            faceCache.put(loc, cFace);
            return cFace;
        }
        if (cachedException != null && cachedException.getCause() instanceof FileNotFoundException) {
            String path = loc.getResourcePath();
            if (loc.getResourcePath().indexOf('/') < 0) {
                path = '/' + path;
            }
            path = path.substring(path.lastIndexOf('/')).replace(".cf", ".ctx");
            objectCache.put(loc, gson.fromJson(String.format(NORMAL_FACE, path), JsonObject.class));
            Chisel.debug("Substituting default face json for missing file " + loc);
            clearException();
            return createFace(loc);
        }
        throw clearException();
    }

    private static ICTMTexture<?> createTexture(ResourceLocation loc) {
        if (isCombinedTexture(false, loc)) {
            JsonObject object = objectCache.get(loc);
            JsonTexture texture = gson.fromJson(object, JsonTexture.class);
            ICTMTexture<?> cTexture = texture.get(loc);
            textureCache.put(loc, cTexture);
            return cTexture;
        }
        if (cachedException != null && cachedException.getCause() instanceof FileNotFoundException) {
            objectCache.put(loc, NORMAL_TEXTURE);
            Chisel.debug("Substituting default texture json for missing file " + loc);
            clearException();
            return createTexture(loc);
        }
        throw clearException();
    }

    public static void flushCaches(){
        Chisel.debug("Flushing Json caches");
        objectCache.clear();
        faceCache.clear();
        textureCache.clear();
    }

    public static IChiselFace getOrCreateFace(ResourceLocation loc) {
        if (faceCache.containsKey(loc)) {
            return faceCache.get(loc);
        } else {
            return createFace(loc);
        }
    }

    public static ICTMTexture<?> getOrCreateTexture(ResourceLocation loc) {
        if (textureCache.containsKey(loc)) {
            return textureCache.get(loc);
        } else {
            return createTexture(loc);
        }
    }

    public static boolean isValidTexture(ResourceLocation loc) {
        ResourceLocation absolute = new ResourceLocation(loc.getResourceDomain(), "textures/blocks/" + loc.getResourcePath());
        return isValid(loc, absolute);
    }
    
    public static boolean isValidFace(ResourceLocation loc) {
        // TODO put this somewhere statically accessible
        ResourceLocation absolute = new ResourceLocation(loc.getResourceDomain(), "models/block/" + loc.getResourcePath());
        if (isValid(loc, absolute)) {
            JsonObject obj = objectCache.get(loc);
            return obj.has("textures") && !obj.has("type");
        }
        return false;
    }
    
    private static boolean isValid(ResourceLocation relative, ResourceLocation absolute) {
        clearException();
        if (objectCache.containsKey(relative)) {
            return true;
        }
        if (!isLoadable(absolute)) {
            objectCache.put(relative, NORMAL_TEXTURE);
            return true;
        }
        
        JsonObject object;

        try {
            object = gson.fromJson(new InputStreamReader(Minecraft.getMinecraft().getResourceManager().getResource(absolute).getInputStream()), JsonObject.class);
        } catch (JsonSyntaxException | IOException e) {
            cachedException = new RuntimeException("Error loading file " + absolute, e);
            return false;
        }

        if (object.has("textures") || object.has("type")) {
            objectCache.put(relative, object);
            return true;
        } else {
            throw new IllegalArgumentException(relative + " does not have a 'textures' and/or 'type' field!");
        }
    }
    
    private static boolean isLoadable(ResourceLocation loc) {
        return loc.getResourcePath().endsWith(TEXTURE_EXTENSION) || loc.getResourcePath().endsWith(FACE_EXTENSION);
    }

    public static boolean isCombinedTexture(boolean combined, ResourceLocation loc) {
        if (isValidTexture(loc)) {
            JsonObject object = objectCache.get(loc);
            boolean ret = object.has("children") && !object.has("type");
            return ret == combined;
        }
        return false;
    }
    
    public static RuntimeException clearException() {
        RuntimeException e = cachedException;
        cachedException = null;
        return e;
    }

    public static boolean isFace(ResourceLocation loc){
        return faceCache.containsKey(loc);
    }

    public static boolean isTex(ResourceLocation loc){
        return textureCache.containsKey(loc);
    }

    public static boolean isLocalPath(String path) {
        return path.startsWith("./");
    }
    
    public static String toAbsolutePath(String localPath, ResourceLocation loc) {
        String path = loc.getResourcePath();
        path = path.substring(0, path.lastIndexOf('/') + 1);
        return loc.getResourceDomain() + ":" + path + localPath.substring(2);
    }

    public static String toTexturePath(String resourcePath) {
        String s = resourcePath.replace("textures/", "").replace(TEXTURE_EXTENSION, "");
        if (!s.startsWith("blocks")) {
            s = "blocks/".concat(s);
        }
        return s;
    }
}
