package kr.coinpack.mixin;

import kr.coinpack.CoinPackMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 산 청크 안으로는 물/용암이 흘러 들어가지 못함 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {
    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void coinpack$noFlow(LevelAccessor level, BlockPos pos, BlockState state, Direction dir, FluidState fluid, CallbackInfo ci) {
        if (level instanceof ServerLevel sl && CoinPackMod.isClaimed(sl, pos)) ci.cancel();
    }
}
