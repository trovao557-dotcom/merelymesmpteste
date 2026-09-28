package merelyme.virtualmenu;

import net.minecraft.class_332;
import net.minecraft.class_437;
import net.minecraft.class_465;
import net.minecraft.class_476;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_465.class)
abstract class HandledScreenMixin {
    @Inject(method = "method_2388", at = @At("HEAD"), cancellable = true)
    private void merelyme$hideVanillaLabels(
        class_332 context, int mouseX, int mouseY, CallbackInfo callback
    ) {
        if (!((Object) this instanceof class_476)) return;

        String title = ((class_437) (Object) this).method_25440().getString();
        if (merelyme.virtualmenu2.MenuSelecto.isCustom(title)) callback.cancel();
    }
}
