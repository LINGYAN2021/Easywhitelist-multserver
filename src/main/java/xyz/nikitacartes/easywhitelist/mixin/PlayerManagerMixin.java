package xyz.nikitacartes.easywhitelist.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nikitacartes.easywhitelist.EasyWhitelist;

import java.net.SocketAddress;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    private static final Logger LOGGER = LogManager.getLogger("EasyWhitelist");

    @Shadow @Final private BannedPlayerList bannedProfiles;

    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void onCheckCanJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        String name = profile.getName();
        LOGGER.info("[EasyWhitelist] checkCanJoin called for '{}', DB enabled: {}", name, EasyWhitelist.isDatabaseEnabled());

        if (EasyWhitelist.isDatabaseEnabled() && name != null) {
            // Don't bypass ban checks
            if (bannedProfiles.contains(profile)) {
                LOGGER.info("[EasyWhitelist] '{}' is banned, skipping DB whitelist.", name);
                return; // Let vanilla handle the ban
            }

            if (EasyWhitelist.databaseManager.isWhitelisted(name)) {
                LOGGER.info("[EasyWhitelist] '{}' ALLOWED via database whitelist.", name);
                cir.setReturnValue(null); // null = allow to join
            } else {
                LOGGER.info("[EasyWhitelist] '{}' NOT in database whitelist.", name);
            }
        }
    }

    @Inject(method = "isWhitelisted", at = @At("HEAD"), cancellable = true)
    private void onIsWhitelisted(GameProfile profile, CallbackInfoReturnable<Boolean> cir) {
        if (EasyWhitelist.isDatabaseEnabled()) {
            String name = profile.getName();
            if (name != null) {
                boolean allowed = EasyWhitelist.databaseManager.isWhitelisted(name);
                LOGGER.info("[EasyWhitelist] isWhitelisted for '{}': {}", name, allowed ? "ALLOWED" : "DENIED");
                cir.setReturnValue(allowed);
            }
        }
    }
}
