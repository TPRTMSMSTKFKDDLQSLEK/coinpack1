package kr.coinpack;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CoinPackMod implements ModInitializer {
    public static final int PRICE = 10;
    /** 크리에이티브 모드 플레이어는 제한을 무시 (관리자용). 원치 않으면 false */
    public static final boolean CREATIVE_BYPASS = true;
    /** 땅 문서 존재 검사 주기 (틱). 20 = 1초 */
    private static final int VALIDATE_INTERVAL = 20;

    /** 직전 틱에 "왼손에 코인 10개 이상"이었던 플레이어 (왼손 바꾸기 순간만 감지하기 위함) */
    private final Set<UUID> hadCoinsInOffhand = new HashSet<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> ChunkClaims.load());

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // ---- 구매: 코인 10개를 들고 F(왼손 바꾸기) ----
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ItemStack off = p.getOffhandItem();
                boolean has = isCoin(off) && off.getCount() >= PRICE;
                boolean had = hadCoinsInOffhand.contains(p.getUUID());
                if (has && !had) tryBuy(p);
                if (has) hadCoinsInOffhand.add(p.getUUID()); else hadCoinsInOffhand.remove(p.getUUID());
            }
            // ---- 땅 문서가 사라졌는지 주기적으로 검사 ----
            if (server.getTickCount() % VALIDATE_INTERVAL == 0) validateClaims(server);
        });

        // ---- 보호: 산 청크에서는 주인만 가능 (빈 땅은 누구나 가능) ----
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> canAct(player, level, pos));

        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) ->
                canAct(player, level, pos) ? InteractionResult.PASS : InteractionResult.FAIL);

        // 블록 설치 / 우클릭 상호작용 (상자, 문, 레버, 양동이 등)
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!canAct(player, level, hit.getBlockPos())) return InteractionResult.FAIL;
            // 블록/양동이를 들고 있으면 "실제로 놓이는 위치"(클릭한 면 바깥쪽)도 검사 -> 청크 경계 우회 방지
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof BlockItem || held.getItem() instanceof BucketItem) {
                if (!canAct(player, level, hit.getBlockPos().relative(hit.getDirection()))) return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 엔티티 상호작용 / 공격 (액자, 갑옷 거치대, 동물 등)
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                canAct(player, level, entity.blockPosition()) ? InteractionResult.PASS : InteractionResult.FAIL);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                (entity instanceof Player) || canAct(player, level, entity.blockPosition())
                        ? InteractionResult.PASS : InteractionResult.FAIL);
    }

    // =====================================================================
    //  믹스인에서 쓰는 공용 함수: 이 위치가 "산 청크"인가?
    // =====================================================================
    public static boolean isClaimed(Level level, BlockPos pos) {
        if (level.isClientSide()) return false;
        return ChunkClaims.get(ChunkClaims.key(level.dimension().toString(), pos.getX() >> 4, pos.getZ() >> 4)) != null;
    }

    // =====================================================================
    //  구매
    // =====================================================================
    private void tryBuy(ServerPlayer p) {
        Level level = p.level();
        String dim = level.dimension().toString();
        int cx = p.blockPosition().getX() >> 4;
        int cz = p.blockPosition().getZ() >> 4;

        ChunkClaims.Claim existing = ChunkClaims.get(ChunkClaims.key(dim, cx, cz));
        if (existing != null) {
            if (hasRights(p, existing)) {
                p.sendSystemMessage(Component.literal("§e이미 당신의 청크입니다."));
            } else {
                p.sendSystemMessage(Component.literal("§c이미 §f" + existing.ownerName + "§c 님이 소유한 청크입니다."));
            }
            return;
        }

        p.getOffhandItem().shrink(PRICE);

        String deedId = UUID.randomUUID().toString();
        ChunkClaims.Claim c = new ChunkClaims.Claim();
        c.deedId = deedId;
        c.ownerName = p.getName().getString();
        c.holder = p.getUUID().toString();
        c.state = "PLAYER";
        c.dim = dim;
        c.cx = cx;
        c.cz = cz;
        ChunkClaims.put(c);

        ItemStack deed = new ItemStack(Items.PAPER);
        CompoundTag tag = new CompoundTag();
        tag.putString("coinpack_deed", deedId);
        deed.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        deed.set(DataComponents.CUSTOM_NAME, Component.literal("§6땅 문서 §7(" + cx + ", " + cz + ")"));
        if (!p.getInventory().add(deed)) p.drop(deed, false);

        p.sendSystemMessage(Component.literal("§a청크 (" + cx + ", " + cz + ") 를 구매했습니다! §7땅 문서를 잃어버리면 청크는 빈 땅이 됩니다. 산 청크 안의 상자에 보관해도 됩니다."));
    }

    // =====================================================================
    //  권한 검사
    // =====================================================================
    /** true = 허용, false = 차단. 서버 쪽에서만 판단(클라이언트는 항상 통과). */
    private boolean canAct(Player player, Level level, BlockPos pos) {
        if (level.isClientSide()) return true;
        if (CREATIVE_BYPASS && player.isCreative()) return true;

        ChunkClaims.Claim c = ChunkClaims.get(ChunkClaims.key(level.dimension().toString(), pos.getX() >> 4, pos.getZ() >> 4));
        if (c == null) return true;               // 아무도 안 산 청크 = 모두 허용
        if (hasRights(player, c)) return true;

        if (player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("§c" + c.ownerName + " 님의 청크입니다."), true);
        }
        return false;
    }

    /** 문서를 들고 있거나, 문서가 (산 청크 안) 상자에 보관 중이고 내가 마지막 소유자일 때 */
    private static boolean hasRights(Player player, ChunkClaims.Claim c) {
        if (ownsDeed(player, c.deedId)) return true;
        return "CONTAINER".equals(c.state) && player.getUUID().toString().equals(c.holder);
    }

    private static boolean ownsDeed(Player player, String deedId) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (deedId.equals(deedIdOf(inv.getItem(i)))) return true;
        }
        return deedId.equals(deedIdOf(player.getOffhandItem()));
    }

    // =====================================================================
    //  땅 문서 존재 검사 (사라지면 빈 땅)
    // =====================================================================
    private static final class Loc {
        final String kind;          // PLAYER / CONTAINER / WORLD / MENU
        UUID uuid; String name;     // PLAYER
        String dim; int cx, cz;     // CONTAINER / WORLD
        Loc(String kind) { this.kind = kind; }
    }

    private void validateClaims(MinecraftServer server) {
        List<ChunkClaims.Claim> all = ChunkClaims.all();
        if (all.isEmpty()) return;

        Map<String, Loc> found = new HashMap<>();

        // 1) 접속 중인 플레이어의 인벤토리 + 엔더상자
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Inventory inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) putPlayer(found, deedIdOf(inv.getItem(i)), p);
            putPlayer(found, deedIdOf(p.getOffhandItem()), p);
            Container ender = p.getEnderChestInventory();
            for (int i = 0; i < ender.getContainerSize(); i++) putPlayer(found, deedIdOf(ender.getItem(i)), p);
        }

        // 2) 월드: 바닥에 떨어진 아이템, 액자, 산 청크 안의 상자류
        for (ServerLevel lv : server.getAllLevels()) {
            String dim = lv.dimension().toString();

            for (Entity e : lv.getAllEntities()) {
                ItemStack s = ItemStack.EMPTY;
                if (e instanceof ItemEntity ie) s = ie.getItem();
                else if (e instanceof ItemFrame f) s = f.getItem();
                String id = deedIdOf(s);
                if (id != null && !found.containsKey(id)) {
                    Loc l = new Loc("WORLD");
                    l.dim = dim; l.cx = e.blockPosition().getX() >> 4; l.cz = e.blockPosition().getZ() >> 4;
                    found.put(id, l);
                }
            }

            for (ChunkClaims.Claim c : all) {
                if (!c.dim.equals(dim)) continue;
                LevelChunk chunk = lv.getChunkSource().getChunkNow(c.cx, c.cz);
                if (chunk == null) continue; // 로드 안 된 청크는 검사 불가
                for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) {
                    if (!(be instanceof Container cont)) continue;
                    for (int i = 0; i < cont.getContainerSize(); i++) {
                        String id = deedIdOf(cont.getItem(i));
                        if (id != null && !found.containsKey(id)) {
                            Loc l = new Loc("CONTAINER");
                            l.dim = dim; l.cx = c.cx; l.cz = c.cz;
                            found.put(id, l);
                        }
                    }
                }
            }
        }

        // 3) 열려 있는 창(커서에 든 아이템, 제작대/모루 칸 등)에 있는 것도 "존재함"으로 인정 (위치 갱신은 안 함)
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String id = deedIdOf(p.containerMenu.getCarried());
            if (id != null && !found.containsKey(id)) found.put(id, new Loc("MENU"));
            for (Slot slot : p.containerMenu.slots) {
                id = deedIdOf(slot.getItem());
                if (id != null && !found.containsKey(id)) found.put(id, new Loc("MENU"));
            }
        }

        // 4) 결과 반영
        boolean changed = false;
        for (ChunkClaims.Claim c : all) {
            Loc l = found.get(c.deedId);
            if (l != null) {
                switch (l.kind) {
                    case "PLAYER" -> {
                        if (!"PLAYER".equals(c.state) || !l.uuid.toString().equals(c.holder)) changed = true;
                        c.state = "PLAYER"; c.holder = l.uuid.toString(); c.ownerName = l.name;
                    }
                    case "CONTAINER", "WORLD" -> {
                        if (!l.kind.equals(c.state) || !l.dim.equals(c.lastDim) || l.cx != c.lastCx || l.cz != c.lastCz) changed = true;
                        c.state = l.kind; c.lastDim = l.dim; c.lastCx = l.cx; c.lastCz = l.cz;
                    }
                    default -> { /* MENU: 변경 없음 */ }
                }
                continue;
            }

            // 못 찾음 -> 확인할 수 없는 상황이면 유지, 아니면 문서가 사라진 것
            boolean keep;
            if (!"PLAYER".equals(c.state)) {
                // 상자/바닥에 있었는데 그 청크가 지금 로드되어 있지 않으면 확인 불가 -> 유지
                ServerLevel lv = findLevel(server, c.lastDim);
                keep = lv != null && lv.getChunkSource().getChunkNow(c.lastCx, c.lastCz) == null;
            } else {
                // 누군가 들고 있었는데 그 사람이 오프라인이면 확인 불가 -> 유지
                keep = c.holder != null && server.getPlayerList().getPlayer(UUID.fromString(c.holder)) == null;
            }
            if (!keep) {
                ChunkClaims.remove(c);
                if (c.holder != null) {
                    ServerPlayer hp = server.getPlayerList().getPlayer(UUID.fromString(c.holder));
                    if (hp != null) hp.sendSystemMessage(Component.literal(
                            "§c땅 문서가 사라져 청크 (" + c.cx + ", " + c.cz + ") 가 빈 땅이 되었습니다."));
                }
            }
        }
        if (changed) ChunkClaims.save();
    }

    private static void putPlayer(Map<String, Loc> found, String id, ServerPlayer p) {
        if (id == null) return;
        Loc existing = found.get(id);
        if (existing != null && "PLAYER".equals(existing.kind)) return;
        Loc l = new Loc("PLAYER");
        l.uuid = p.getUUID();
        l.name = p.getName().getString();
        found.put(id, l);
    }

    private static ServerLevel findLevel(MinecraftServer server, String dim) {
        if (dim == null) return null;
        for (ServerLevel lv : server.getAllLevels()) {
            if (lv.dimension().toString().equals(dim)) return lv;
        }
        return null;
    }

    // =====================================================================
    //  아이템 판별
    // =====================================================================
    /** 땅 문서면 문서 ID, 아니면 null */
    private static String deedIdOf(ItemStack s) {
        if (s == null || s.isEmpty() || !s.is(Items.PAPER)) return null;
        CustomData cd = s.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return null;
        String id = cd.copyTag().getStringOr("coinpack_deed", "");
        return id.isEmpty() ? null : id;
    }

    private static boolean isCoin(ItemStack s) {
        if (s.isEmpty() || !s.is(Items.GOLD_NUGGET)) return false;
        CustomModelData cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
        return cmd != null && cmd.strings().contains("coin");
    }
}
