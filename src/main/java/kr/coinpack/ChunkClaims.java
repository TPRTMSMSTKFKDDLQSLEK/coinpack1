package kr.coinpack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 산 청크 목록 저장소 (config/coinpack_claims.json). 목록에 없는 청크 = 빈 땅. */
public final class ChunkClaims {
    public static final class Claim {
        public String deedId;     // 땅 문서 ID (종이 아이템 안에 들어 있음)
        public String ownerName;  // 마지막으로 문서를 들고 있던 사람 이름 (안내 메시지용)
        public String holder;     // 마지막으로 문서를 들고 있던 사람 UUID
        public String state;      // PLAYER(누가 들고 있음) / CONTAINER(상자 안) / WORLD(바닥/액자)
        public String dim;        // 이 청크의 차원
        public int cx, cz;        // 이 청크 좌표
        public String lastDim;    // 문서가 마지막으로 확인된 청크 (CONTAINER/WORLD일 때)
        public int lastCx, lastCz;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("coinpack_claims.json");
    private static Map<String, Claim> claims = new HashMap<>();

    public static String key(String dimension, int chunkX, int chunkZ) {
        return dimension + "|" + chunkX + "|" + chunkZ;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Map<String, Claim> m = GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, Claim>>() {}.getType());
                if (m != null) claims = m;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(claims));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Claim get(String key) { return claims.get(key); }

    public static void put(Claim c) {
        claims.put(key(c.dim, c.cx, c.cz), c);
        save();
    }

    public static void remove(Claim c) {
        claims.remove(key(c.dim, c.cx, c.cz));
        save();
    }

    /** 순회 중 수정해도 안전하도록 복사본 반환 */
    public static List<Claim> all() { return new ArrayList<>(claims.values()); }
}
