package kr.coinpack.mixin;

import kr.coinpack.CoinPackMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 산 청크 안에서 움직이거나, 산 청크 안으로 블록을 미는 피스톤은 작동 불가 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"), cancellable = true)
    private void coinpack$protect(Level level, BlockPos pos, Direction facing, boolean extending,
                                  CallbackInfoReturnable<Boolean> cir) {
        if (level.isClientSide()) return;

        if (CoinPackMod.isClaimed(level, pos) || (extending && CoinPackMod.isClaimed(level, pos.relative(facing)))) {
            cir.setReturnValue(false);
            return;
        }

        PistonStructureResolver resolver = new PistonStructureResolver(level, pos, facing, extending);
        if (!resolver.resolve()) return;

        Direction moveDir = extending ? facing : facing.getOpposite();
        for (BlockPos b : resolver.getToPush()) {
            if (CoinPackMod.isClaimed(level, b) || CoinPackMod.isClaimed(level, b.relative(moveDir))) {
                cir.setReturnValue(false);
                return;
            }
        }
        for (BlockPos b : resolver.getToDestroy()) {
            if (CoinPackMod.isClaimed(level, b)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
