package merelyme.virtualmenu;

import net.minecraft.class_2960;
import net.minecraft.class_437;
import net.minecraft.class_476;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(class_476.class)
abstract class GenericContainerScreenMixin {
    @Shadow @Final private int field_2864;

    @ModifyArg(
        method = "method_2389(Lnet/minecraft/class_332;FII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/class_332;method_25290(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/class_2960;IIFFIIII)V"
        ),
        index = 1
    )
    private class_2960 merelyme$blueMenu(class_2960 originalTexture) {
        String title = ((class_437) (Object) this).method_25440().getString();
        String texture = merelyme.virtualmenu2.MenuSelecto.texture(title, this.field_2864);
        if (texture == null) return originalTexture;
        String path = "textures/gui/menu_" + texture + ".png";
        return class_2960.method_60655("merelyme", path);
    }
}
