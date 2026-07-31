package com.nightsta69.delvefold.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigLoadResult;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.ConfigWriteResult;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import com.nightsta69.delvefold.reset.BackupMode;
import com.nightsta69.delvefold.reset.WorldBackupCatalog;
import com.nightsta69.delvefold.reset.WorldOperationPreview;
import com.nightsta69.delvefold.reset.WorldOperationRequest;
import com.nightsta69.delvefold.reset.WorldOperationResult;
import com.nightsta69.delvefold.reset.WorldOperationService;
import com.nightsta69.delvefold.reset.WorldRestoreService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Brigadier and console parity for every administration path exposed by the GUI. */
public final class DelvefoldCommands {
    private DelvefoldCommands() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        for (String name : DelvefoldCommandNames.REGISTERED_ROOTS) {
            dispatcher.register(root(name));
        }
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name)
                .then(Commands.literal("gui")
                        .requires(AdminAccess::canConfigure)
                        .executes(DelvefoldCommands::openGui))
                .then(Commands.literal("config")
                        .requires(AdminAccess::canConfigure)
                        .executes(DelvefoldCommands::openGui)
                        .then(Commands.literal("validate").executes(DelvefoldCommands::validateConfig))
                        .then(Commands.literal("reload").executes(DelvefoldCommands::reloadConfig)))
                .then(Commands.literal("initialize")
                        .requires(AdminAccess::canConfigure)
                        .then(Commands.argument("terrain", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("flat", "cavern", "wild"), builder))
                                .then(Commands.argument("ore_preset", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("balanced", "rich", "empty"), builder))
                                        .then(Commands.argument("gameplay_preset", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("safe", "hostile", "normal"), builder))
                                                .executes(DelvefoldCommands::initialize)))))
                .then(Commands.literal("status").executes(DelvefoldCommands::status))
                .then(profileCommands())
                .then(backupCommands())
                .then(oreCommands())
                .then(worldCommands());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> backupCommands() {
        LiteralArgumentBuilder<CommandSourceStack> backup = Commands.literal("backup");
        backup.requires(AdminAccess::canManageWorld);
        backup.then(Commands.literal("list").executes(DelvefoldCommands::listBackups));
        backup.then(Commands.literal("pin").then(backupArgument().executes(context -> pinBackup(context, true))));
        backup.then(Commands.literal("unpin").then(backupArgument().executes(context -> pinBackup(context, false))));
        backup.then(Commands.literal("delete").then(backupArgument()
                .then(Commands.literal("confirm").executes(DelvefoldCommands::deleteBackup))));
        backup.then(Commands.literal("restore")
                .then(Commands.literal("request").then(backupArgument().executes(DelvefoldCommands::requestRestore)))
                .then(Commands.literal("confirm").then(Commands.argument("token", StringArgumentType.word())
                        .executes(DelvefoldCommands::confirmRestore)))
                .then(Commands.literal("cancel").executes(DelvefoldCommands::cancelRestore)));
        return backup;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> backupArgument() {
        return Commands.argument("backup", StringArgumentType.word()).suggests((context, builder) -> {
            try {
                return SharedSuggestionProvider.suggest(backups(context).stream().map(backup -> backup.id()), builder);
            } catch (IOException exception) {
                return builder.buildFuture();
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> profileCommands() {
        LiteralArgumentBuilder<CommandSourceStack> profile = Commands.literal("profile");
        profile.requires(AdminAccess::canConfigure);
        profile.then(Commands.literal("list").executes(DelvefoldCommands::listProfiles));
        profile.then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        List.of("balanced", "rich", "empty"), builder))
                                .executes(context -> createProfile(context, false))
                                .then(Commands.literal("overwrite")
                                        .executes(context -> createProfile(context, true))))));
        profile.then(Commands.literal("duplicate")
                .then(profileArgument("source")
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> duplicateProfile(context, false))
                                .then(Commands.literal("overwrite")
                                        .executes(context -> duplicateProfile(context, true))))));
        profile.then(Commands.literal("save-current")
                .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> saveCurrentProfile(context, false))
                        .then(Commands.literal("overwrite")
                                .executes(context -> saveCurrentProfile(context, true)))));
        profile.then(Commands.literal("select")
                .then(profileArgument("id").executes(DelvefoldCommands::selectProfile)));
        profile.then(Commands.literal("delete")
                .then(profileArgument("id").executes(DelvefoldCommands::deleteProfile)));
        profile.then(Commands.literal("import")
                .then(Commands.argument("file", StringArgumentType.word())
                        .then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> importProfile(context, false))
                                .then(Commands.literal("overwrite")
                                        .executes(context -> importProfile(context, true))))));
        profile.then(Commands.literal("export")
                .then(profileArgument("id")
                        .then(Commands.argument("file", StringArgumentType.word())
                                .executes(DelvefoldCommands::exportProfile))));
        return profile;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> profileArgument(
            String name) {
        return Commands.argument(name, StringArgumentType.word()).suggests((context, builder) -> {
            try {
                return SharedSuggestionProvider.suggest(
                        DelvefoldConfigService.get().listProfiles().stream().map(profile -> profile.id()), builder);
            } catch (IOException exception) {
                return builder.buildFuture();
            }
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> oreCommands() {
        LiteralArgumentBuilder<CommandSourceStack> ore = Commands.literal("ore");
        ore.requires(AdminAccess::canConfigure);
        ore.then(Commands.literal("list").executes(DelvefoldCommands::listOres));
        ore.then(Commands.literal("show")
                .then(ruleArgument().executes(context -> showOre(context, StringArgumentType.getString(context, "rule")))));
        ore.then(Commands.literal("add")
                .then(Commands.argument("block", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder))
                        .then(Commands.argument("variants", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("exact", "detected"), builder))
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("common", "uncommon", "rare", "very_rare"), builder))
                                        .executes(DelvefoldCommands::addOre)))));
        ore.then(Commands.literal("enable").then(ruleArgument().executes(context -> setOreEnabled(context, true))));
        ore.then(Commands.literal("disable").then(ruleArgument().executes(context -> setOreEnabled(context, false))));
        ore.then(Commands.literal("remove").then(ruleArgument().executes(DelvefoldCommands::removeOre)));

        LiteralArgumentBuilder<CommandSourceStack> target = Commands.literal("target");
        target.then(Commands.literal("add")
                .then(ruleArgument()
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder))
                                .then(Commands.argument("replace_tag", ResourceLocationArgument.id())
                                        .executes(DelvefoldCommands::addTarget)))));
        target.then(Commands.literal("remove")
                .then(ruleArgument()
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .executes(DelvefoldCommands::removeTarget))));
        ore.then(target);

        LiteralArgumentBuilder<CommandSourceStack> band = Commands.literal("band");
        band.then(Commands.literal("add")
                .then(ruleArgument()
                        .then(Commands.argument("band", StringArgumentType.word())
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("common", "uncommon", "rare", "very_rare"), builder))
                                        .executes(DelvefoldCommands::addBand)))));
        band.then(Commands.literal("remove")
                .then(ruleArgument()
                        .then(Commands.argument("band", StringArgumentType.word())
                                .executes(DelvefoldCommands::removeBand))));
        band.then(Commands.literal("set")
                .then(ruleArgument()
                        .then(Commands.argument("band", StringArgumentType.word())
                                .then(Commands.argument("field", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("vein_size", "attempts", "min_y", "max_y", "peak_y", "plateau_min_y", "plateau_max_y", "discard"), builder))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(DelvefoldCommands::setBandField))))));
        ore.then(band);

        ore.then(Commands.literal("scan")
                .executes(context -> scanOres(context, null))
                .then(Commands.argument("namespace", StringArgumentType.word())
                        .executes(context -> scanOres(context, StringArgumentType.getString(context, "namespace")))));
        return ore;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> worldCommands() {
        LiteralArgumentBuilder<CommandSourceStack> world = Commands.literal("world");
        world.requires(AdminAccess::canManageWorld);

        LiteralArgumentBuilder<CommandSourceStack> recreateRequest = Commands.literal("request");
        recreateRequest.executes(context -> requestRecreate(context, null, BackupMode.KEEP_BACKUP));
        recreateRequest.then(Commands.argument("terrain", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("flat", "cavern", "wild"), builder))
                .executes(context -> requestRecreate(
                        context,
                        TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                        BackupMode.KEEP_BACKUP))
                .then(Commands.argument("backup", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("keep_backup", "permanent"), builder))
                        .executes(context -> requestRecreate(
                                context,
                                TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                                parseBackup(StringArgumentType.getString(context, "backup"))))));
        world.then(Commands.literal("recreate").then(recreateRequest));

        LiteralArgumentBuilder<CommandSourceStack> deleteRequest = Commands.literal("request");
        deleteRequest.executes(context -> requestDelete(context, BackupMode.KEEP_BACKUP));
        deleteRequest.then(Commands.argument("backup", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("keep_backup", "permanent"), builder))
                .executes(context -> requestDelete(context, parseBackup(StringArgumentType.getString(context, "backup")))));
        world.then(Commands.literal("delete").then(deleteRequest));
        world.then(Commands.literal("confirm")
                .then(Commands.argument("token", StringArgumentType.word()).executes(DelvefoldCommands::confirmWorldOperation)));
        world.then(Commands.literal("cancel").executes(DelvefoldCommands::cancelWorldOperation));
        return world;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> ruleArgument() {
        return Commands.argument("rule", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        DelvefoldConfigService.get().snapshot().ores().rules().stream().map(OreRule::id), builder));
    }

    private static int openGui(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!DelvefoldNetwork.openFor(player)) {
            context.getSource().sendFailure(Component.literal("You are not allowed to configure Delvefold."));
            return 0;
        }
        return 1;
    }

    private static int initialize(CommandContext<CommandSourceStack> context) {
        try {
            TerrainMode terrain = TerrainMode.parse(StringArgumentType.getString(context, "terrain"));
            OrePreset orePreset = OrePreset.parse(StringArgumentType.getString(context, "ore_preset"));
            GameplayPreset gameplay = GameplayPreset.parse(StringArgumentType.getString(context, "gameplay_preset"));
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            ConfigWriteResult result = DelvefoldConfigService.get().initialize(
                    snapshot.ores().revision(), snapshot.settings().revision(), terrain, orePreset, gameplay);
            return reportWrite(context.getSource(), result, "Delvefold initialized as " + terrain.serializedName());
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        try {
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            var settings = snapshot.settings();
            String terrain = settings.initialized() ? settings.terrainMode().serializedName() : "uninitialized";
            context.getSource().sendSuccess(() -> Component.literal(
                    "Delvefold: " + terrain
                            + ", epoch " + settings.generationEpoch()
                            + ", ore revision " + snapshot.ores().revision()
                            + ", settings revision " + settings.revision()
                            + (WorldOperationService.get().isEntryBlocked() ? ", world operation pending" : "")
            ), false);
            return 1;
        } catch (IllegalStateException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int listProfiles(CommandContext<CommandSourceStack> context) {
        try {
            var profiles = DelvefoldConfigService.get().listProfiles();
            String active = DelvefoldConfigService.get().snapshot().ores().profile();
            context.getSource().sendSuccess(() -> Component.literal(
                    "Ore profiles (active: " + active + "):"), false);
            for (var profile : profiles) {
                String flags = (profile.id().equals(active) ? "active" : "")
                        + (profile.builtIn() ? (profile.id().equals(active) ? ", built-in" : "built-in") : "")
                        + (profile.localOverride() ? ", local" : "");
                context.getSource().sendSystemMessage(Component.literal("  " + profile.id() + " — "
                        + profile.ruleCount() + " rule(s)" + (flags.isBlank() ? "" : " [" + flags + "]")));
            }
            return profiles.size();
        } catch (IOException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int createProfile(CommandContext<CommandSourceStack> context, boolean overwrite) {
        try {
            String id = StringArgumentType.getString(context, "id");
            OrePreset preset = OrePreset.parse(StringArgumentType.getString(context, "preset"));
            return reportProfile(context, DelvefoldConfigService.get().createProfileFromPreset(id, preset, overwrite));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int duplicateProfile(CommandContext<CommandSourceStack> context, boolean overwrite) {
        try {
            return reportProfile(context, DelvefoldConfigService.get().duplicateProfile(
                    StringArgumentType.getString(context, "source"),
                    StringArgumentType.getString(context, "id"), overwrite));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int saveCurrentProfile(CommandContext<CommandSourceStack> context, boolean overwrite) {
        try {
            return reportProfile(context, DelvefoldConfigService.get().saveCurrentProfileAs(
                    StringArgumentType.getString(context, "id"), overwrite));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int selectProfile(CommandContext<CommandSourceStack> context) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        String id = StringArgumentType.getString(context, "id");
        ConfigWriteResult result = DelvefoldConfigService.get().activateProfile(snapshot.ores().revision(), id);
        return reportWrite(context.getSource(), result,
                "Activated ore profile '" + id + "'. Existing chunks are unchanged.");
    }

    private static int deleteProfile(CommandContext<CommandSourceStack> context) {
        var result = DelvefoldConfigService.get().deleteProfile(StringArgumentType.getString(context, "id"));
        if (!result.deleted()) {
            context.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int importProfile(CommandContext<CommandSourceStack> context, boolean overwrite) {
        try {
            return reportProfile(context, DelvefoldConfigService.get().importProfile(
                    StringArgumentType.getString(context, "file"),
                    StringArgumentType.getString(context, "id"), overwrite));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int exportProfile(CommandContext<CommandSourceStack> context) {
        try {
            var exported = DelvefoldConfigService.get().exportProfile(
                    StringArgumentType.getString(context, "id"),
                    StringArgumentType.getString(context, "file"));
            context.getSource().sendSuccess(() -> Component.literal(
                    "Exported profile to serverconfig/delvefold/exports/" + exported.getFileName()), false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int reportProfile(
            CommandContext<CommandSourceStack> context, com.nightsta69.delvefold.config.OreProfileCatalog.ProfileWriteResult result) {
        for (ConfigIssue issue : result.issues()) {
            context.getSource().sendSystemMessage(Component.literal(
                    issue.severity() + " " + issue.path() + ": " + issue.message()));
        }
        if (!result.saved()) {
            context.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int profileFailure(CommandContext<CommandSourceStack> context, Exception exception) {
        context.getSource().sendFailure(Component.literal("Profile operation failed: " + exception.getMessage()));
        return 0;
    }

    private static int listBackups(CommandContext<CommandSourceStack> context) {
        try {
            var backups = backups(context);
            context.getSource().sendSuccess(() -> Component.literal(
                    backups.isEmpty() ? "No Delvefold backups found." : "Delvefold backups:"), false);
            for (var backup : backups) {
                context.getSource().sendSystemMessage(Component.literal("  " + backup.id() + " — "
                        + backup.operation() + ", " + backup.terrain() + ", " + humanBytes(backup.sizeBytes())
                        + (backup.pinned() ? " [pinned]" : "")
                        + (backup.restorable() ? " [restorable]" : " [archive only]")));
            }
            return backups.size();
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Could not list backups: " + exception.getMessage()));
            return 0;
        }
    }

    private static int pinBackup(CommandContext<CommandSourceStack> context, boolean pinned) {
        try {
            backupCatalog(context).setPinned(StringArgumentType.getString(context, "backup"), pinned);
            context.getSource().sendSuccess(() -> Component.literal(
                    (pinned ? "Pinned " : "Unpinned ") + StringArgumentType.getString(context, "backup")), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Backup update failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int deleteBackup(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "backup");
        try {
            if (!backupCatalog(context).delete(id)) {
                context.getSource().sendFailure(Component.literal("Backup was not deleted: " + id));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal(
                    "Permanently deleted Delvefold backup " + id), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Backup deletion failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int requestRestore(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "backup");
        WorldOperationPreview preview = WorldRestoreService.get().request(
                context.getSource().getServer(), id, context.getSource().getTextName());
        if (!preview.accepted()) {
            context.getSource().sendFailure(Component.literal(preview.message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(preview.message()
                + ". Estimated copy: " + humanBytes(preview.estimatedBytes())
                + ". Confirm with /delvefold backup restore confirm " + preview.confirmationToken()), false);
        return 1;
    }

    private static int confirmRestore(CommandContext<CommandSourceStack> context) {
        return reportOperation(context.getSource(), WorldRestoreService.get().confirm(
                context.getSource().getServer(), StringArgumentType.getString(context, "token")));
    }

    private static int cancelRestore(CommandContext<CommandSourceStack> context) {
        return reportOperation(context.getSource(), WorldRestoreService.get().cancel(context.getSource().getServer()));
    }

    private static List<WorldBackupCatalog.BackupSummary> backups(CommandContext<CommandSourceStack> context)
            throws IOException {
        return backupCatalog(context).list();
    }

    private static WorldBackupCatalog backupCatalog(CommandContext<CommandSourceStack> context) {
        return new WorldBackupCatalog(context.getSource().getServer()
                .getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
    }

    private static int validateConfig(CommandContext<CommandSourceStack> context) {
        try {
            ConfigLoadResult result = DelvefoldConfigService.get().validateDisk();
            var report = new com.nightsta69.delvefold.config.validation.ValidationReport(result.issues());
            for (ConfigIssue issue : result.issues()) {
                context.getSource().sendSystemMessage(Component.literal(issue.severity() + " " + issue.path() + ": " + issue.message()));
            }
            boolean valid = report.valid() && !result.usedFallback();
            context.getSource().sendSuccess(() -> Component.literal(
                    "Disk validation complete: " + report.errorCount() + " error(s), "
                            + report.warningCount() + " warning(s)"), false);
            return valid ? 1 : 0;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Validation failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        try {
            ConfigLoadResult result = DelvefoldConfigService.get().reload();
            for (ConfigIssue issue : result.issues()) {
                context.getSource().sendSystemMessage(Component.literal(issue.severity() + " " + issue.path() + ": " + issue.message()));
            }
            if (result.usedFallback()) {
                context.getSource().sendFailure(Component.literal("Rejected disk changes; the last known-good configuration remains active."));
                return 0;
            }
            context.getSource().sendSuccess(() -> Component.literal("Delvefold JSON configuration reloaded."), true);
            return 1;
        } catch (IOException exception) {
            context.getSource().sendFailure(Component.literal("Reload failed: " + exception.getMessage()));
            return 0;
        }
    }

    private static int listOres(CommandContext<CommandSourceStack> context) {
        OreProfileDocument document = DelvefoldConfigService.get().snapshot().ores();
        context.getSource().sendSuccess(() -> Component.literal(
                document.rules().isEmpty() ? "No configured ores." : "Configured ores (revision " + document.revision() + "):"), false);
        for (OreRule rule : document.rules()) {
            context.getSource().sendSystemMessage(Component.literal(
                    (rule.enabled() ? "[on] " : "[off] ") + rule.id() + " — " + rule.targets().size() + " target(s), " + rule.bands().size() + " band(s)"));
        }
        return document.rules().size();
    }

    private static int showOre(CommandContext<CommandSourceStack> context, String id) {
        OreRule rule = findRule(id);
        if (rule == null) {
            context.getSource().sendFailure(Component.literal("Unknown ore rule: " + id));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(rule.id() + " enabled=" + rule.enabled() + " required=" + rule.required()), false);
        for (OreTarget target : rule.targets()) {
            context.getSource().sendSystemMessage(Component.literal("  target " + target.block() + " -> #" + target.replaceTag()));
        }
        for (SpawnBand band : rule.bands()) {
            context.getSource().sendSystemMessage(Component.literal(
                    "  band " + band.id() + ": size=" + band.veinSize() + ", attempts=" + band.attemptsPerChunk()
                            + ", " + band.distribution().name().toLowerCase(Locale.ROOT) + " " + band.minY() + ".." + band.maxY()));
        }
        return 1;
    }

    private static int addOre(CommandContext<CommandSourceStack> context) {
        ResourceLocation block = ResourceLocationArgument.getId(context, "block");
        if (block == null) {
            context.getSource().sendFailure(Component.literal("Invalid block registry id."));
            return 0;
        }
        try {
            boolean detected = "detected".equalsIgnoreCase(StringArgumentType.getString(context, "variants"));
            OreRuleFactory.Rarity rarity = OreRuleFactory.Rarity.parse(StringArgumentType.getString(context, "rarity"));
            OreRule rule = OreRuleFactory.create(block, detected, rarity);
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            if (snapshot.ores().rules().stream().anyMatch(existing -> existing.id().equals(rule.id()))) {
                context.getSource().sendFailure(Component.literal("Rule already exists: " + rule.id()));
                return 0;
            }
            ConfigWriteResult result = DelvefoldConfigService.get().updateOres(snapshot.ores().revision(), document -> {
                List<OreRule> rules = new ArrayList<>(document.rules());
                rules.add(rule);
                return document.nextRevision(rules, document.profile());
            });
            return reportWrite(context.getSource(), result, "Added ore rule " + rule.id());
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int setOreEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String id = StringArgumentType.getString(context, "rule");
        return mutateRule(context, id, rule -> rule.withEnabled(enabled), (enabled ? "Enabled " : "Disabled ") + id);
    }

    private static int removeOre(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "rule");
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (findRule(id) == null) {
            context.getSource().sendFailure(Component.literal("Unknown ore rule: " + id));
            return 0;
        }
        ConfigWriteResult result = DelvefoldConfigService.get().updateOres(snapshot.ores().revision(), document ->
                document.nextRevision(document.rules().stream().filter(rule -> !rule.id().equals(id)).toList(),
                        document.profile()));
        return reportWrite(context.getSource(), result, "Removed ore rule " + id);
    }

    private static int addTarget(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String block = ResourceLocationArgument.getId(context, "block").toString();
        String tag = ResourceLocationArgument.getId(context, "replace_tag").toString();
        return mutateRule(context, ruleId, rule -> {
            List<OreTarget> targets = new ArrayList<>(rule.targets());
            targets.add(OreTarget.of(block, tag.startsWith("#") ? tag.substring(1) : tag));
            return new OreRule(rule.id(), rule.enabled(), rule.required(), rule.terrainModes(), targets, rule.biomes(), rule.bands());
        }, "Added target to " + ruleId);
    }

    private static int removeTarget(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String block = ResourceLocationArgument.getId(context, "block").toString();
        return mutateRule(context, ruleId, rule -> new OreRule(
                rule.id(), rule.enabled(), rule.required(), rule.terrainModes(),
                rule.targets().stream().filter(target -> !target.block().equals(block)).toList(),
                rule.biomes(), rule.bands()), "Removed target from " + ruleId);
    }

    private static int addBand(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        OreRuleFactory.Rarity rarity = OreRuleFactory.Rarity.parse(StringArgumentType.getString(context, "rarity"));
        OreRule template = OreRuleFactory.create(ResourceLocation.withDefaultNamespace("iron_ore"), false, rarity);
        SpawnBand band = template.bands().getFirst();
        band = new SpawnBand(bandId, band.veinSize(), band.attemptsPerChunk(), band.distribution(), band.minY(), band.maxY(),
                band.peakY(), band.plateauMinY(), band.plateauMaxY(), band.discardOnAirExposure());
        SpawnBand finalBand = band;
        return mutateRule(context, ruleId, rule -> {
            List<SpawnBand> bands = new ArrayList<>(rule.bands());
            bands.add(finalBand);
            return rule.withBands(bands);
        }, "Added band " + bandId + " to " + ruleId);
    }

    private static int removeBand(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        return mutateRule(context, ruleId, rule -> rule.withBands(
                rule.bands().stream().filter(band -> !band.id().equals(bandId)).toList()),
                "Removed band " + bandId + " from " + ruleId);
    }

    private static int setBandField(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        String field = StringArgumentType.getString(context, "field");
        double value = DoubleArgumentType.getDouble(context, "value");
        return mutateRule(context, ruleId, rule -> rule.withBands(rule.bands().stream()
                .map(band -> band.id().equals(bandId) ? withBandField(band, field, value) : band)
                .toList()), "Updated " + ruleId + '/' + bandId + ' ' + field);
    }

    private static SpawnBand withBandField(SpawnBand band, String field, double value) {
        int vein = band.veinSize();
        double attempts = band.attemptsPerChunk();
        int min = band.minY();
        int max = band.maxY();
        Integer peak = band.peakY();
        Integer plateauMin = band.plateauMinY();
        Integer plateauMax = band.plateauMaxY();
        double discard = band.discardOnAirExposure();
        switch (field) {
            case "vein_size" -> vein = requireWhole(value, field);
            case "attempts" -> attempts = value;
            case "min_y" -> min = requireWhole(value, field);
            case "max_y" -> max = requireWhole(value, field);
            case "peak_y" -> peak = requireWhole(value, field);
            case "plateau_min_y" -> plateauMin = requireWhole(value, field);
            case "plateau_max_y" -> plateauMax = requireWhole(value, field);
            case "discard" -> discard = value;
            default -> throw new IllegalArgumentException("Unknown band field: " + field);
        }
        return new SpawnBand(band.id(), vein, attempts, band.distribution(), min, max, peak, plateauMin, plateauMax, discard);
    }

    private static int requireWhole(double value, String field) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new IllegalArgumentException(field + " requires a whole number");
        }
        return (int) value;
    }

    private static int scanOres(CommandContext<CommandSourceStack> context, String namespace) {
        List<ResourceLocation> matches = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> namespace == null || namespace.equals(id.getNamespace()))
                .filter(id -> id.getPath().endsWith("_ore") || id.getPath().startsWith("ore_"))
                .sorted()
                .limit(100)
                .toList();
        context.getSource().sendSuccess(() -> Component.literal("Ore-like registered blocks (showing " + matches.size() + "):"), false);
        for (ResourceLocation match : matches) {
            context.getSource().sendSystemMessage(Component.literal("  " + match));
        }
        return matches.size();
    }

    private static int requestRecreate(CommandContext<CommandSourceStack> context, TerrainMode selected, BackupMode backup) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        TerrainMode target = selected == null ? snapshot.settings().terrainMode() : selected;
        WorldOperationRequest base = WorldOperationRequest.recreate(target);
        WorldOperationRequest request = new WorldOperationRequest(
                base.type(), base.targetTerrain(), null, null, backup, false);
        return reportPreview(context, WorldOperationService.get().request(context.getSource().getServer(), request, context.getSource().getTextName()));
    }

    private static int requestDelete(CommandContext<CommandSourceStack> context, BackupMode backup) {
        WorldOperationRequest base = WorldOperationRequest.delete();
        WorldOperationRequest request = new WorldOperationRequest(base.type(), null, null, null, backup, false);
        return reportPreview(context, WorldOperationService.get().request(context.getSource().getServer(), request, context.getSource().getTextName()));
    }

    private static int confirmWorldOperation(CommandContext<CommandSourceStack> context) {
        WorldOperationResult result = WorldOperationService.get().confirm(
                context.getSource().getServer(), StringArgumentType.getString(context, "token"));
        return reportOperation(context.getSource(), result);
    }

    private static int cancelWorldOperation(CommandContext<CommandSourceStack> context) {
        WorldOperationResult result = WorldOperationService.get().cancelConfirmed(context.getSource().getServer());
        if (!result.success()) {
            result = WorldOperationService.get().cancel(context.getSource().getServer());
        }
        return reportOperation(context.getSource(), result);
    }

    private static int reportPreview(CommandContext<CommandSourceStack> context, WorldOperationPreview preview) {
        if (!preview.accepted()) {
            context.getSource().sendFailure(Component.literal(preview.message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                preview.message() + ". Estimated size: " + humanBytes(preview.estimatedBytes())
                        + "; players to evacuate: " + preview.playersToEvacuate()
                        + ". Confirm with /delvefold world confirm " + preview.confirmationToken()), false);
        return 1;
    }

    private static int reportOperation(CommandSourceStack source, WorldOperationResult result) {
        if (!result.success()) {
            source.sendFailure(Component.literal(result.message()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(result.message()), true);
        return 1;
    }

    private static int mutateRule(
            CommandContext<CommandSourceStack> context,
            String id,
            java.util.function.UnaryOperator<OreRule> mutation,
            String successMessage
    ) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (findRule(id) == null) {
            context.getSource().sendFailure(Component.literal("Unknown ore rule: " + id));
            return 0;
        }
        try {
            ConfigWriteResult result = DelvefoldConfigService.get().updateOres(snapshot.ores().revision(), document ->
                    document.nextRevision(document.rules().stream()
                            .map(rule -> rule.id().equals(id) ? mutation.apply(rule) : rule)
                            .toList(), document.profile()));
            return reportWrite(context.getSource(), result, successMessage);
        } catch (IllegalArgumentException exception) {
            context.getSource().sendFailure(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static OreRule findRule(String id) {
        return DelvefoldConfigService.get().snapshot().ores().rules().stream()
                .filter(rule -> rule.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static int reportWrite(CommandSourceStack source, ConfigWriteResult result, String successMessage) {
        if (!result.saved()) {
            for (ConfigIssue issue : result.issues()) {
                source.sendFailure(Component.literal(issue.path() + ": " + issue.message()));
            }
            return 0;
        }
        for (ConfigIssue issue : result.issues()) {
            source.sendSystemMessage(Component.literal(issue.severity() + " " + issue.path() + ": " + issue.message()));
        }
        source.sendSuccess(() -> Component.literal(successMessage), true);
        return 1;
    }

    private static BackupMode parseBackup(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "keep_backup", "backup" -> BackupMode.KEEP_BACKUP;
            case "permanent", "no_backup" -> BackupMode.PERMANENT;
            default -> throw new IllegalArgumentException("Unknown backup mode: " + value);
        };
    }

    private static String humanBytes(long bytes) {
        if (bytes < 0) {
            return "unknown";
        }
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024.0D && unit < units.length - 1) {
            value /= 1024.0D;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
