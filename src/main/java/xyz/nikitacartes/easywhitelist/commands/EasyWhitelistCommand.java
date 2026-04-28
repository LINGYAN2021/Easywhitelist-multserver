package xyz.nikitacartes.easywhitelist.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;
import xyz.nikitacartes.easywhitelist.EasyWhitelist;
import xyz.nikitacartes.easywhitelist.integrations.Permissions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.dedicated.command.WhitelistCommand.executeAdd;
import static net.minecraft.server.dedicated.command.WhitelistCommand.executeRemove;
import static xyz.nikitacartes.easywhitelist.EasyWhitelist.getProfileFromNickname;

public class EasyWhitelistCommand {

    public static void registerCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("easywhitelist")
                .requires(Permissions.require("easywhitelist.commands.easywhitelist.root", 3))
                .then(literal("add")
                        .requires(Permissions.require("easywhitelist.commands.easywhitelist.add", 3))
                        .then(argument("targets", word())
                                .executes(ctx -> {
                                    String name = getString(ctx, "targets");
                                    int result = executeAdd(ctx.getSource(), getProfileFromNickname(name));
                                    if (EasyWhitelist.isDatabaseEnabled()) {
                                        String uuid = Uuids.getOfflinePlayerUuid(name).toString();
                                        EasyWhitelist.databaseManager.addToWhitelist(name, uuid);
                                        ctx.getSource().sendFeedback(() -> Text.literal("[DB] Synced '" + name + "' to database whitelist."), true);
                                    }
                                    return result;
                                })
                        )
                )
                .then(literal("remove")
                        .requires(Permissions.require("easywhitelist.commands.easywhitelist.remove", 3))
                        .then(argument("targets", word())
                                .executes(ctx -> {
                                    String name = getString(ctx, "targets");
                                    int result = executeRemove(ctx.getSource(), getProfileFromNickname(name));
                                    if (EasyWhitelist.isDatabaseEnabled()) {
                                        EasyWhitelist.databaseManager.removeFromWhitelist(name);
                                        ctx.getSource().sendFeedback(() -> Text.literal("[DB] Removed '" + name + "' from database whitelist."), true);
                                    }
                                    return result;
                                })
                        )
                )
                .then(literal("sync")
                        .requires(Permissions.require("easywhitelist.commands.easywhitelist.root", 3))
                        .executes(ctx -> {
                            if (!EasyWhitelist.isDatabaseEnabled()) {
                                ctx.getSource().sendError(Text.literal("[EasyWhitelist] Database is not enabled."));
                                return 0;
                            }
                            EasyWhitelist.databaseManager.refreshCache();
                            Set<String> list = EasyWhitelist.databaseManager.getAllWhitelisted();
                            ctx.getSource().sendFeedback(() -> Text.literal("[DB] Whitelist synced. " + list.size() + " players in database."), true);
                            return 1;
                        })
                )
                .then(literal("dblist")
                        .requires(Permissions.require("easywhitelist.commands.easywhitelist.root", 3))
                        .executes(ctx -> {
                            if (!EasyWhitelist.isDatabaseEnabled()) {
                                ctx.getSource().sendError(Text.literal("[EasyWhitelist] Database is not enabled."));
                                return 0;
                            }
                            Set<String> list = EasyWhitelist.databaseManager.getAllWhitelisted();
                            if (list.isEmpty()) {
                                ctx.getSource().sendFeedback(() -> Text.literal("[DB] Database whitelist is empty."), false);
                            } else {
                                ctx.getSource().sendFeedback(() -> Text.literal("[DB] Whitelisted (" + list.size() + "): " + String.join(", ", list)), false);
                            }
                            return 1;
                        })
                )
                .then(literal("import")
                        .requires(Permissions.require("easywhitelist.commands.easywhitelist.root", 3))
                        .executes(ctx -> {
                            if (!EasyWhitelist.isDatabaseEnabled()) {
                                ctx.getSource().sendError(Text.literal("[EasyWhitelist] Database is not enabled."));
                                return 0;
                            }
                            var whitelist = ctx.getSource().getServer().getPlayerManager().getWhitelist();
                            String[] names = whitelist.getNames();
                            if (names.length == 0) {
                                ctx.getSource().sendFeedback(() -> Text.literal("[EasyWhitelist] Vanilla whitelist is empty, nothing to import."), false);
                                return 0;
                            }
                            Map<String, String> nameUuidMap = new LinkedHashMap<>();
                            for (String name : names) {
                                String uuid = Uuids.getOfflinePlayerUuid(name).toString();
                                nameUuidMap.put(name, uuid);
                            }
                            int result = EasyWhitelist.databaseManager.bulkAddToWhitelist(nameUuidMap);
                            if (result >= 0) {
                                ctx.getSource().sendFeedback(() -> Text.literal("[DB] Successfully imported " + nameUuidMap.size() + " players from vanilla whitelist into database."), true);
                            } else {
                                ctx.getSource().sendError(Text.literal("[EasyWhitelist] Failed to import whitelist. Check server logs."));
                            }
                            return 1;
                        })
                )
                .then(literal("reload")
                        .requires(Permissions.require("easywhitelist.commands.easywhitelist.root", 3))
                        .executes(ctx -> {
                            EasyWhitelist.databaseConfig = xyz.nikitacartes.easywhitelist.config.DatabaseConfig.load();
                            ctx.getSource().sendFeedback(() -> Text.literal("[EasyWhitelist] Config reloaded."), true);
                            return 1;
                        })
                )
                .then(literal("status")
                        .requires(Permissions.require("easywhitelist.commands.easywhitelist.root", 3))
                        .executes(ctx -> {
                            boolean dbEnabled = EasyWhitelist.databaseConfig != null && EasyWhitelist.databaseConfig.enabled;
                            boolean dbConnected = EasyWhitelist.isDatabaseEnabled();
                            boolean vanillaWhitelist = ctx.getSource().getServer().getPlayerManager().isWhitelistEnabled();
                            int cacheSize = dbConnected ? EasyWhitelist.databaseManager.getAllWhitelisted().size() : 0;
                            String serverId = EasyWhitelist.databaseConfig != null ? EasyWhitelist.databaseConfig.serverId : "N/A";

                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "§6[EasyWhitelist Status]\n" +
                                    "§f  Vanilla whitelist: " + (vanillaWhitelist ? "§aENABLED" : "§cDISABLED") + "\n" +
                                    "§f  DB config enabled: " + (dbEnabled ? "§aYES" : "§cNO") + "\n" +
                                    "§f  DB connected: " + (dbConnected ? "§aYES" : "§cNO") + "\n" +
                                    "§f  Server ID: §b" + serverId + "\n" +
                                    "§f  Cache entries: §b" + cacheSize
                            ), false);
                            return 1;
                        })
                )
        );
    }

}
