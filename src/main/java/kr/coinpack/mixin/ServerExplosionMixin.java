package kr.coinpack.mixin;

import kr.coinpack.CoinPackMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerExplosion;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 산 청크 안의 블록은 폭발로 부서지지 않음 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
    @Shadow @Final private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"), cancellable = true)
    private void coinpack$protect(CallbackInfoReturnable<List<BlockPos>> cir) {
        List<BlockPos> filtered = new ArrayList<>();
        for (BlockPos p : cir.getReturnValue()) {
            if (!CoinPackMod.isClaimed(this.level, p)) filtered.add(p);
        }
        cir.setReturnValue(filtered);
    }
}
