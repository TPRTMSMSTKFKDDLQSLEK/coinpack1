package kr.coinpack.mixin;

import kr.coinpack.CoinPackMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 산 청크 안에서는 불이 퍼지거나 블록을 태우지 못함 (산 청크 안의 불은 바로 꺼짐) */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void coinpack$extinguish(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (CoinPackMod.isClaimed(level, pos)) {
            level.removeBlock(pos, false);
            ci.cancel();
        }
    }

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void coinpack$noBurn(Level level, BlockPos pos, int chance, RandomSource random, int age, CallbackInfo ci) {
        if (CoinPackMod.isClaimed(level, pos)) ci.cancel();
    }
}
