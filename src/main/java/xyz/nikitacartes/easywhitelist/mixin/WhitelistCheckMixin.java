package xyz.nikitacartes.easywhitelist.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.Whitelist;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nikitacartes.easywhitelist.EasyWhitelist;

@Mixin(Whitelist.class)
public class WhitelistCheckMixin {

    @Inject(method = "isAllowed", at = @At("HEAD"), cancellable = true)
    private void onIsAllowed(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        if (EasyWhitelist.isDatabaseEnabled()) {
            String name = profile.getName();
            if (name != null) {
                // Database is the sole authority — always return definitive result
                cir.setReturnValue(EasyWhitelist.databaseManager.isWhitelisted(name));
            }
        }
    }
}
