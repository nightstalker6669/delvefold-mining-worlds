package com.nightsta69.delvefold.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.nightsta69.delvefold.admin.AdminLocalizedComponents;
import com.nightsta69.delvefold.audit.AsyncAuditMutationTracker;
import com.nightsta69.delvefold.audit.AuditMutation;
import com.nightsta69.delvefold.audit.DelvefoldAuditService;
import com.nightsta69.delvefold.config.AdminAccess;
import com.nightsta69.delvefold.config.ConfigLoadResult;
import com.nightsta69.delvefold.config.ConfigSnapshot;
import com.nightsta69.delvefold.config.ConfigWriteResult;
import com.nightsta69.delvefold.config.DelvefoldConfigService;
import com.nightsta69.delvefold.config.model.BackupRetentionSettings;
import com.nightsta69.delvefold.config.model.GameplayPreset;
import com.nightsta69.delvefold.config.model.GeologyTheme;
import com.nightsta69.delvefold.config.model.GuideVisibility;
import com.nightsta69.delvefold.config.model.LandmarkPreset;
import com.nightsta69.delvefold.config.model.OreBandPlacement;
import com.nightsta69.delvefold.config.model.OrePreset;
import com.nightsta69.delvefold.config.model.OreProfileDocument;
import com.nightsta69.delvefold.config.model.OreRule;
import com.nightsta69.delvefold.config.model.OreTarget;
import com.nightsta69.delvefold.config.model.PortalHubSettings;
import com.nightsta69.delvefold.config.model.PortalRoutingMode;
import com.nightsta69.delvefold.config.model.PortalSettings;
import com.nightsta69.delvefold.config.model.ProvinceSettings;
import com.nightsta69.delvefold.config.model.RenewalSeedMode;
import com.nightsta69.delvefold.config.model.RenewalSettings;
import com.nightsta69.delvefold.config.model.SpawnBand;
import com.nightsta69.delvefold.config.model.TerrainMode;
import com.nightsta69.delvefold.config.model.TerrainVariant;
import com.nightsta69.delvefold.config.model.WorldIdentitySettings;
import com.nightsta69.delvefold.config.validation.ConfigIssue;
import com.nightsta69.delvefold.config.validation.ConfigIssueMessages;
import com.nightsta69.delvefold.diagnostics.DelvefoldDoctorService;
import com.nightsta69.delvefold.guide.DelvefoldGuideService;
import com.nightsta69.delvefold.guide.GuideAccessPolicy;
import com.nightsta69.delvefold.guide.GuideSnapshotService;
import com.nightsta69.delvefold.guide.GuideTextSummary;
import com.nightsta69.delvefold.network.DelvefoldNetwork;
import com.nightsta69.delvefold.reset.BackupCatalogCache;
import com.nightsta69.delvefold.reset.BackupDeletionGuard;
import com.nightsta69.delvefold.reset.BackupMode;
import com.nightsta69.delvefold.reset.BackupVerificationResult;
import com.nightsta69.delvefold.reset.BackupVerificationService;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/** Brigadier and console parity for every administration path exposed by the GUI. */
public final class DelvefoldCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    private DelvefoldCommands() {}

    /**
     * Registers command roots during NeoForge's server command-registration event.
     *
     * @param event event containing the active server dispatcher
     */
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /**
     * Adds every documented Delvefold root and alias to a Brigadier dispatcher.
     *
     * @param dispatcher target dispatcher; existing unrelated commands are untouched
     */
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
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(List.of("flat", "cavern", "wild"), builder))
                                .then(Commands.argument("ore_preset", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("balanced", "rich", "empty"), builder))
                                        .then(Commands.argument("gameplay_preset", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("safe", "hostile", "normal"), builder))
                                                .executes(DelvefoldCommands::initialize)))))
                .then(Commands.literal("status").executes(DelvefoldCommands::status))
                .then(doctorCommands())
                .then(guideCommands())
                .then(identityCommands())
                .then(portalCommands())
                .then(renewalCommands())
                .then(profileCommands())
                .then(backupCommands())
                .then(oreCommands())
                .then(worldCommands());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> doctorCommands() {
        LiteralArgumentBuilder<CommandSourceStack> doctor =
                Commands.literal("doctor").requires(AdminAccess::canConfigure).executes(DelvefoldCommands::doctor);
        doctor.then(Commands.literal("export").executes(DelvefoldCommands::exportDoctorReport));
        return doctor;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> guideCommands() {
        LiteralArgumentBuilder<CommandSourceStack> guide =
                Commands.literal("guide").executes(DelvefoldCommands::openGuide);
        guide.then(Commands.literal("visibility")
                .requires(AdminAccess::canConfigure)
                .executes(DelvefoldCommands::guideVisibilityStatus)
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(List.of("public", "operators", "disabled"), builder))
                        .executes(DelvefoldCommands::setGuideVisibility)));
        return guide;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> identityCommands() {
        LiteralArgumentBuilder<CommandSourceStack> identity = Commands.literal("identity");
        identity.requires(AdminAccess::canConfigure);
        identity.executes(DelvefoldCommands::identityStatus);
        identity.then(Commands.literal("name")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(DelvefoldCommands::setIdentityName)));
        identity.then(Commands.literal("landmarks")
                .then(Commands.argument("preset", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                List.of("pure_mining", "balanced", "abundant"), builder))
                        .executes(DelvefoldCommands::setLandmarkPreset)));
        identity.then(Commands.literal("variant")
                .then(Commands.argument("variant", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(List.of("classic", "expansive"), builder))
                        .executes(DelvefoldCommands::setUninitializedVariant)));
        identity.then(Commands.literal("geology-theme")
                .then(Commands.argument("theme", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                List.of("classic", "volcanic", "dripstone", "lush", "crystal"), builder))
                        .executes(DelvefoldCommands::setUninitializedGeologyTheme)));
        return identity;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> renewalCommands() {
        LiteralArgumentBuilder<CommandSourceStack> renewal = Commands.literal("renewal");
        renewal.requires(AdminAccess::canManageWorld);
        renewal.executes(DelvefoldCommands::renewalStatus);
        renewal.then(Commands.literal("disable").executes(DelvefoldCommands::disableRenewal));
        renewal.then(Commands.literal("configure")
                .then(Commands.argument("interval_days", IntegerArgumentType.integer(1, 3650))
                        .then(Commands.argument("warning_minutes", IntegerArgumentType.integer(1, 10080))
                                .executes(DelvefoldCommands::configureRenewal))));
        renewal.then(Commands.literal("seed-mode")
                .executes(DelvefoldCommands::renewalSeedModeStatus)
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(List.of("stable", "rotate_on_recreate"), builder))
                        .executes(DelvefoldCommands::setRenewalSeedMode)));
        return renewal;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> portalCommands() {
        LiteralArgumentBuilder<CommandSourceStack> portal = Commands.literal("portal");
        portal.requires(AdminAccess::canConfigure);
        portal.executes(DelvefoldCommands::portalStatus);
        portal.then(Commands.literal("routing")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(List.of("coordinate_linked", "central_hub"), builder))
                        .executes(DelvefoldCommands::setPortalRouting)));
        portal.then(Commands.literal("hub")
                .requires(AdminAccess::canManageWorld)
                .then(Commands.argument(
                                "x",
                                IntegerArgumentType.integer(
                                        -PortalHubSettings.MAX_ABSOLUTE_COORDINATE,
                                        PortalHubSettings.MAX_ABSOLUTE_COORDINATE))
                        .then(Commands.argument(
                                        "z",
                                        IntegerArgumentType.integer(
                                                -PortalHubSettings.MAX_ABSOLUTE_COORDINATE,
                                                PortalHubSettings.MAX_ABSOLUTE_COORDINATE))
                                .then(Commands.argument(
                                                "protection_radius",
                                                IntegerArgumentType.integer(
                                                        PortalHubSettings.MIN_PROTECTION_RADIUS,
                                                        PortalHubSettings.MAX_PROTECTION_RADIUS))
                                        .executes(DelvefoldCommands::setPortalHub)))));
        return portal;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> backupCommands() {
        LiteralArgumentBuilder<CommandSourceStack> backup = Commands.literal("backup");
        backup.requires(AdminAccess::canManageWorld);
        backup.then(Commands.literal("list").executes(DelvefoldCommands::listBackups));
        backup.then(Commands.literal("verify").then(backupArgument().executes(DelvefoldCommands::verifyBackup)));
        LiteralArgumentBuilder<CommandSourceStack> retention =
                Commands.literal("retention").executes(DelvefoldCommands::retentionStatus);
        retention.then(Commands.literal("disable").executes(DelvefoldCommands::disableRetention));
        retention.then(Commands.literal("configure")
                .then(Commands.argument("max_count", IntegerArgumentType.integer(0))
                        .then(Commands.argument("max_age_days", LongArgumentType.longArg(0L))
                                .then(Commands.argument("max_total_bytes", LongArgumentType.longArg(0L))
                                        .executes(DelvefoldCommands::configureRetention)))));
        backup.then(retention);
        backup.then(Commands.literal("pin").then(backupArgument().executes(context -> pinBackup(context, true))));
        backup.then(Commands.literal("unpin").then(backupArgument().executes(context -> pinBackup(context, false))));
        backup.then(Commands.literal("delete")
                .then(backupArgument().then(Commands.literal("confirm").executes(DelvefoldCommands::deleteBackup))));
        backup.then(Commands.literal("restore")
                .then(Commands.literal("request").then(backupArgument().executes(DelvefoldCommands::requestRestore)))
                .then(Commands.literal("confirm")
                        .then(Commands.argument("token", StringArgumentType.word())
                                .executes(DelvefoldCommands::confirmRestore)))
                .then(Commands.literal("cancel").executes(DelvefoldCommands::cancelRestore)));
        return backup;
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> backupArgument() {
        return Commands.argument("backup", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        backupSnapshot(context).backups().stream().map(WorldBackupCatalog.BackupSummary::id), builder));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> profileCommands() {
        LiteralArgumentBuilder<CommandSourceStack> profile = Commands.literal("profile");
        profile.requires(AdminAccess::canConfigure);
        profile.then(Commands.literal("list").executes(DelvefoldCommands::listProfiles));
        profile.then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.word())
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(List.of("balanced", "rich", "empty"), builder))
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
                        .then(Commands.literal("overwrite").executes(context -> saveCurrentProfile(context, true)))));
        profile.then(Commands.literal("select").then(profileArgument("id").executes(DelvefoldCommands::selectProfile)));
        profile.then(Commands.literal("delete").then(profileArgument("id").executes(DelvefoldCommands::deleteProfile)));
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
                .then(ruleArgument()
                        .executes(context -> showOre(context, StringArgumentType.getString(context, "rule")))));
        ore.then(Commands.literal("add")
                .then(Commands.argument("block", ResourceLocationArgument.id())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggestResource(BuiltInRegistries.BLOCK.keySet(), builder))
                        .then(Commands.argument("variants", StringArgumentType.word())
                                .suggests((context, builder) ->
                                        SharedSuggestionProvider.suggest(List.of("exact", "detected"), builder))
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("common", "uncommon", "rare", "very_rare"), builder))
                                        .executes(DelvefoldCommands::addOre)))));
        ore.then(Commands.literal("enable").then(ruleArgument().executes(context -> setOreEnabled(context, true))));
        ore.then(Commands.literal("disable").then(ruleArgument().executes(context -> setOreEnabled(context, false))));
        ore.then(Commands.literal("remove").then(ruleArgument().executes(DelvefoldCommands::removeOre)));

        LiteralArgumentBuilder<CommandSourceStack> target = Commands.literal("target");
        target.then(Commands.literal("add")
                .then(ruleArgument()
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.BLOCK.keySet(), builder))
                                .then(Commands.argument("replace_tag", ResourceLocationArgument.id())
                                        .executes(context -> addTarget(context, OreTarget.DEFAULT_WEIGHT))
                                        .then(Commands.argument(
                                                        "weight",
                                                        IntegerArgumentType.integer(
                                                                OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT))
                                                .executes(context -> addTarget(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "weight"))))))));
        target.then(Commands.literal("add-tag")
                .then(ruleArgument()
                        .then(Commands.argument("block_tag", ResourceLocationArgument.id())
                                .then(Commands.argument("replace_tag", ResourceLocationArgument.id())
                                        .executes(context -> addTagTarget(context, OreTarget.DEFAULT_WEIGHT))
                                        .then(Commands.argument(
                                                        "weight",
                                                        IntegerArgumentType.integer(
                                                                OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT))
                                                .executes(context -> addTagTarget(
                                                        context,
                                                        IntegerArgumentType.getInteger(context, "weight"))))))));
        target.then(Commands.literal("set-weight")
                .then(ruleArgument()
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.BLOCK.keySet(), builder))
                                .then(Commands.argument(
                                                "weight",
                                                IntegerArgumentType.integer(OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT))
                                        .executes(DelvefoldCommands::setTargetWeight)))));
        target.then(Commands.literal("set-tag-weight")
                .then(ruleArgument()
                        .then(Commands.argument("block_tag", ResourceLocationArgument.id())
                                .then(Commands.argument(
                                                "weight",
                                                IntegerArgumentType.integer(OreTarget.MIN_WEIGHT, OreTarget.MAX_WEIGHT))
                                        .executes(DelvefoldCommands::setTagTargetWeight)))));
        target.then(Commands.literal("remove")
                .then(ruleArgument()
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .executes(DelvefoldCommands::removeTarget))));
        target.then(Commands.literal("remove-tag")
                .then(ruleArgument()
                        .then(Commands.argument("block_tag", ResourceLocationArgument.id())
                                .executes(DelvefoldCommands::removeTagTarget))));
        ore.then(target);

        LiteralArgumentBuilder<CommandSourceStack> band = Commands.literal("band");
        band.then(Commands.literal("add")
                .then(ruleArgument()
                        .then(Commands.argument("band", StringArgumentType.word())
                                .then(Commands.argument("rarity", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("common", "uncommon", "rare", "very_rare"), builder))
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
                                                List.of(
                                                        "vein_size",
                                                        "attempts",
                                                        "min_y",
                                                        "max_y",
                                                        "peak_y",
                                                        "plateau_min_y",
                                                        "plateau_max_y",
                                                        "discard"),
                                                builder))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(DelvefoldCommands::setBandField))))));
        band.then(Commands.literal("placement")
                .then(ruleArgument()
                        .then(Commands.argument("band", StringArgumentType.word())
                                .then(Commands.argument("mode", StringArgumentType.word())
                                        .suggests((context, builder) ->
                                                SharedSuggestionProvider.suggest(List.of("vein", "province"), builder))
                                        .executes(DelvefoldCommands::setBandPlacement)))));
        band.then(Commands.literal("province")
                .then(ruleArgument()
                        .then(Commands.argument("band", StringArgumentType.word())
                                .then(Commands.argument("field", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                List.of(
                                                        "region_size",
                                                        "radius",
                                                        "vertical_thickness",
                                                        "density",
                                                        "work_cap"),
                                                builder))
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                                .executes(DelvefoldCommands::setProvinceField))))));
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
                .suggests((context, builder) ->
                        SharedSuggestionProvider.suggest(List.of("flat", "cavern", "wild"), builder))
                .executes(context -> requestRecreate(
                        context,
                        TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                        BackupMode.KEEP_BACKUP))
                .then(Commands.argument("backup", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(List.of("keep_backup", "permanent"), builder))
                        .executes(context -> requestRecreate(
                                context,
                                TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                                parseBackup(StringArgumentType.getString(context, "backup")))))
                .then(recreateVariantLiteral("classic", TerrainVariant.CLASSIC))
                .then(recreateVariantLiteral("expansive", TerrainVariant.EXPANSIVE)));
        world.then(Commands.literal("recreate").then(recreateRequest));

        LiteralArgumentBuilder<CommandSourceStack> deleteRequest = Commands.literal("request");
        deleteRequest.executes(context -> requestDelete(context, BackupMode.KEEP_BACKUP));
        deleteRequest.then(Commands.argument("backup", StringArgumentType.word())
                .suggests((context, builder) ->
                        SharedSuggestionProvider.suggest(List.of("keep_backup", "permanent"), builder))
                .executes(context ->
                        requestDelete(context, parseBackup(StringArgumentType.getString(context, "backup")))));
        world.then(Commands.literal("delete").then(deleteRequest));
        world.then(Commands.literal("confirm")
                .then(Commands.argument("token", StringArgumentType.word())
                        .executes(DelvefoldCommands::confirmWorldOperation)));
        world.then(Commands.literal("cancel").executes(DelvefoldCommands::cancelWorldOperation));
        return world;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> recreateVariantLiteral(
            String name, TerrainVariant variant) {
        LiteralArgumentBuilder<CommandSourceStack> variantCommand = Commands.literal(name)
                .executes(context -> requestRecreate(
                        context,
                        TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                        variant,
                        BackupMode.KEEP_BACKUP))
                .then(Commands.argument("backup", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(List.of("keep_backup", "permanent"), builder))
                        .executes(context -> requestRecreate(
                                context,
                                TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                                variant,
                                parseBackup(StringArgumentType.getString(context, "backup")))));
        for (GeologyTheme theme : GeologyTheme.values()) {
            variantCommand.then(recreateThemeLiteral(theme.serializedName(), variant, theme));
        }
        return variantCommand;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> recreateThemeLiteral(
            String name, TerrainVariant variant, GeologyTheme theme) {
        return Commands.literal(name)
                .executes(context -> requestRecreate(
                        context,
                        TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                        variant,
                        theme,
                        BackupMode.KEEP_BACKUP))
                .then(Commands.argument("backup", StringArgumentType.word())
                        .suggests((context, builder) ->
                                SharedSuggestionProvider.suggest(List.of("keep_backup", "permanent"), builder))
                        .executes(context -> requestRecreate(
                                context,
                                TerrainMode.parse(StringArgumentType.getString(context, "terrain")),
                                variant,
                                theme,
                                parseBackup(StringArgumentType.getString(context, "backup")))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> ruleArgument() {
        return Commands.argument("rule", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        DelvefoldConfigService.get().snapshot().ores().rules().stream()
                                .map(OreRule::id),
                        builder));
    }

    private static int openGui(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!DelvefoldNetwork.openFor(player)) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.configure_denied"));
            return 0;
        }
        return 1;
    }

    private static int doctor(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var server = source.getServer();
        var unusedDoctorCompletion = DelvefoldDoctorService.get()
                .renderedLinesAsync(server)
                .whenComplete((lines, failure) -> server.execute(() -> {
                    if (!sourceStillAvailable(source)) {
                        return;
                    }
                    if (failure != null) {
                        LOGGER.error("Could not prepare the Delvefold Doctor report", failure);
                        source.sendFailure(Component.translatable("message.delvefold.doctor.failed"));
                        return;
                    }
                    for (String line : lines) {
                        source.sendSystemMessage(AdminLocalizedComponents.resolve(line));
                    }
                }));
        source.sendSuccess(() -> Component.translatable("message.delvefold.doctor.started"), false);
        return 1;
    }

    private static int exportDoctorReport(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var server = source.getServer();
        var unusedExportCompletion = DelvefoldDoctorService.get()
                .exportAsync(server)
                .whenComplete((path, failure) -> server.execute(() -> {
                    if (!sourceStillAvailable(source)) {
                        return;
                    }
                    if (failure != null) {
                        LOGGER.error("Could not export the Delvefold Doctor report", failure);
                        source.sendFailure(Component.translatable("message.delvefold.doctor.export_failed"));
                        return;
                    }
                    source.sendSuccess(
                            () -> Component.translatable(
                                    "message.delvefold.doctor.exported",
                                    path.getFileName().toString()),
                            false);
                }));
        source.sendSuccess(() -> Component.translatable("message.delvefold.doctor.export_started"), false);
        return 1;
    }

    private static int openGuide(CommandContext<CommandSourceStack> context) {
        ConfigSnapshot config;
        try {
            config = DelvefoldConfigService.get().snapshot();
        } catch (IllegalStateException exception) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.guide.unavailable"));
            return 0;
        }
        GuideVisibility visibility = config.settings().guideVisibility();
        boolean operator = AdminAccess.canConfigure(context.getSource());
        if (!GuideAccessPolicy.allows(visibility, operator)) {
            context.getSource()
                    .sendFailure(Component.translatable(
                            visibility == GuideVisibility.DISABLED
                                    ? "message.delvefold.guide.disabled"
                                    : "message.delvefold.guide.operators_only"));
            return 0;
        }
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            DelvefoldGuideService.OpenResult result = DelvefoldGuideService.openFor(player);
            if (!result.opened()) {
                context.getSource().sendFailure(result.message());
                return 0;
            }
            return 1;
        }
        return GuideSnapshotService.current()
                .map(snapshot -> {
                    for (String line : GuideTextSummary.lines(snapshot)) {
                        context.getSource().sendSuccess(() -> AdminLocalizedComponents.resolve(line), false);
                    }
                    return 1;
                })
                .orElseGet(() -> {
                    context.getSource().sendFailure(Component.translatable("message.delvefold.guide.unavailable"));
                    return 0;
                });
    }

    private static int guideVisibilityStatus(CommandContext<CommandSourceStack> context) {
        try {
            GuideVisibility visibility =
                    DelvefoldConfigService.get().snapshot().settings().guideVisibility();
            context.getSource()
                    .sendSuccess(
                            () -> Component.translatable(
                                    "message.delvefold.guide.visibility",
                                    Component.translatable(
                                            "option.delvefold.guide_visibility." + visibility.serializedName())),
                            false);
            return 1;
        } catch (IllegalStateException exception) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.guide.unavailable"));
            return 0;
        }
    }

    private static int setGuideVisibility(CommandContext<CommandSourceStack> context) {
        try {
            GuideVisibility visibility = GuideVisibility.parse(StringArgumentType.getString(context, "mode"));
            ConfigSnapshot before = DelvefoldConfigService.get().snapshot();
            ConfigWriteResult result = asAuditActor(
                    context,
                    () -> DelvefoldConfigService.get()
                            .updateSettings(
                                    before.settings().revision(),
                                    settings -> settings.withGuideVisibility(visibility)));
            return reportWrite(
                    context.getSource(),
                    result,
                    Component.translatable(
                            "message.delvefold.command.guide_visibility_set", visibility.serializedName()));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Could not update Delvefold guide visibility", exception);
            context.getSource()
                    .sendFailure(Component.translatable("message.delvefold.command.guide_visibility_failed"));
            return 0;
        }
    }

    private static int initialize(CommandContext<CommandSourceStack> context) {
        try {
            TerrainMode terrain = TerrainMode.parse(StringArgumentType.getString(context, "terrain"));
            OrePreset orePreset = OrePreset.parse(StringArgumentType.getString(context, "ore_preset"));
            GameplayPreset gameplay = GameplayPreset.parse(StringArgumentType.getString(context, "gameplay_preset"));
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            ConfigWriteResult result = asAuditActor(
                    context,
                    () -> DelvefoldConfigService.get()
                            .initialize(
                                    snapshot.ores().revision(),
                                    snapshot.settings().revision(),
                                    terrain,
                                    orePreset,
                                    gameplay,
                                    snapshot.settings().identity()));
            return reportWrite(
                    context.getSource(),
                    result,
                    Component.translatable("message.delvefold.command.initialized", terrain.serializedName()));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid Delvefold initialization command", exception);
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.initialize_invalid"));
            return 0;
        }
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        try {
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            var settings = snapshot.settings();
            Component terrain = settings.initialized()
                    ? Component.literal(java.util.Objects.requireNonNull(settings.terrainMode(), "initialized terrain")
                            .serializedName())
                    : Component.translatable("message.delvefold.command.status_uninitialized");
            Component pending = WorldOperationService.get().isEntryBlocked()
                    ? Component.translatable("message.delvefold.command.status_pending")
                    : Component.empty();
            context.getSource()
                    .sendSuccess(
                            () -> Component.translatable(
                                    "message.delvefold.command.status",
                                    terrain,
                                    settings.generationEpoch(),
                                    settings.identity().renewal().seedMode().serializedName(),
                                    snapshot.ores().revision(),
                                    settings.revision(),
                                    pending),
                            false);
            return 1;
        } catch (IllegalStateException exception) {
            LOGGER.warn("Could not read Delvefold status", exception);
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.status_failed"));
            return 0;
        }
    }

    private static int identityStatus(CommandContext<CommandSourceStack> context) {
        var settings = DelvefoldConfigService.get().snapshot().settings();
        var identity = settings.identity();
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "message.delvefold.command.identity_status",
                                identity.displayName(),
                                identity.terrainVariant().serializedName(),
                                identity.geologyTheme().serializedName(),
                                identity.landmarkPreset().serializedName()),
                        false);
        return 1;
    }

    private static int portalStatus(CommandContext<CommandSourceStack> context) {
        PortalSettings portal =
                DelvefoldConfigService.get().snapshot().settings().portal();
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "message.delvefold.command.portal_status",
                                portal.routingMode().serializedName(),
                                portal.hub().x(),
                                portal.hub().z(),
                                portal.hub().protectionRadius()),
                        false);
        return 1;
    }

    private static int setPortalRouting(CommandContext<CommandSourceStack> context) {
        PortalRoutingMode mode = PortalRoutingMode.parse(StringArgumentType.getString(context, "mode"));
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        ConfigWriteResult result = asAuditActor(
                context,
                () -> DelvefoldConfigService.get()
                        .updateSettings(snapshot.settings().revision(), settings -> {
                            PortalSettings current = settings.portal();
                            return settings.withPortal(new PortalSettings(
                                    current.enabled(),
                                    current.allowFromOverworldOnly(),
                                    current.cooldownSeconds(),
                                    current.coordinateScale(),
                                    mode,
                                    current.hub()));
                        }));
        return reportWrite(
                context.getSource(),
                result,
                Component.translatable("message.delvefold.command.portal_routing_set", mode.serializedName()));
    }

    private static int setPortalHub(CommandContext<CommandSourceStack> context) {
        PortalHubSettings hub = new PortalHubSettings(
                IntegerArgumentType.getInteger(context, "x"),
                IntegerArgumentType.getInteger(context, "z"),
                IntegerArgumentType.getInteger(context, "protection_radius"));
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        ConfigWriteResult result = asAuditActor(
                context,
                () -> DelvefoldConfigService.get()
                        .updateSettings(snapshot.settings().revision(), settings -> {
                            PortalSettings current = settings.portal();
                            return settings.withPortal(new PortalSettings(
                                    current.enabled(),
                                    current.allowFromOverworldOnly(),
                                    current.cooldownSeconds(),
                                    current.coordinateScale(),
                                    current.routingMode(),
                                    hub));
                        }));
        return reportWrite(
                context.getSource(),
                result,
                Component.translatable(
                        "message.delvefold.command.portal_hub_set", hub.x(), hub.z(), hub.protectionRadius()));
    }

    private static int setIdentityName(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name").trim();
        return updateIdentity(
                context,
                identity -> identity.withDisplayName(name),
                Component.translatable("message.delvefold.command.identity_name_set", name));
    }

    private static int setLandmarkPreset(CommandContext<CommandSourceStack> context) {
        LandmarkPreset preset = LandmarkPreset.valueOf(
                StringArgumentType.getString(context, "preset").toUpperCase(Locale.ROOT));
        boolean enabled = preset != LandmarkPreset.PURE_MINING;
        return updateIdentity(
                context,
                identity -> identity.withLandmarks(preset, enabled, enabled, enabled),
                Component.translatable("message.delvefold.command.landmark_preset_set"));
    }

    private static int setUninitializedVariant(CommandContext<CommandSourceStack> context) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (snapshot.settings().initialized()) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.terrain_variant_locked"));
            return 0;
        }
        TerrainVariant variant = TerrainVariant.valueOf(
                StringArgumentType.getString(context, "variant").toUpperCase(Locale.ROOT));
        return updateIdentity(
                context,
                identity -> identity.withTerrainVariant(variant),
                Component.translatable("message.delvefold.command.terrain_variant_set", variant.serializedName()));
    }

    private static int setUninitializedGeologyTheme(CommandContext<CommandSourceStack> context) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (snapshot.settings().initialized()) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.geology_theme_locked"));
            return 0;
        }
        GeologyTheme theme = GeologyTheme.parse(StringArgumentType.getString(context, "theme"));
        return updateIdentity(
                context,
                identity -> identity.withGeologyTheme(theme),
                Component.translatable("message.delvefold.command.geology_theme_set", theme.serializedName()));
    }

    private static int renewalStatus(CommandContext<CommandSourceStack> context) {
        RenewalSettings renewal =
                DelvefoldConfigService.get().snapshot().settings().identity().renewal();
        Component status = renewal.enabled()
                ? Component.translatable(
                        "message.delvefold.command.renewal_enabled",
                        renewal.intervalDays(),
                        renewal.warningMinutes(),
                        renewal.nextRenewalAtEpochMillis())
                : Component.translatable("message.delvefold.command.renewal_disabled_state");
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "message.delvefold.command.renewal_status",
                                status,
                                renewal.seedMode().serializedName()),
                        false);
        return 1;
    }

    private static int renewalSeedModeStatus(CommandContext<CommandSourceStack> context) {
        RenewalSeedMode mode = DelvefoldConfigService.get()
                .snapshot()
                .settings()
                .identity()
                .renewal()
                .seedMode();
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "message.delvefold.command.renewal_seed_status", mode.serializedName()),
                        false);
        return 1;
    }

    private static int setRenewalSeedMode(CommandContext<CommandSourceStack> context) {
        RenewalSeedMode mode = RenewalSeedMode.parse(StringArgumentType.getString(context, "mode"));
        return updateIdentity(
                context,
                identity -> {
                    RenewalSettings current = identity.renewal();
                    return identity.withRenewal(new RenewalSettings(
                            current.enabled(),
                            current.intervalDays(),
                            current.warningMinutes(),
                            current.nextRenewalAtEpochMillis(),
                            mode));
                },
                Component.translatable("message.delvefold.command.renewal_seed_set", mode.serializedName()));
    }

    private static int configureRenewal(CommandContext<CommandSourceStack> context) {
        int days = IntegerArgumentType.getInteger(context, "interval_days");
        int warning = IntegerArgumentType.getInteger(context, "warning_minutes");
        RenewalSeedMode seedMode = DelvefoldConfigService.get()
                .snapshot()
                .settings()
                .identity()
                .renewal()
                .seedMode();
        RenewalSettings renewal =
                new RenewalSettings(true, days, warning, 0L, seedMode).scheduledFrom(System.currentTimeMillis());
        return updateIdentity(
                context,
                identity -> identity.withRenewal(renewal),
                Component.translatable("message.delvefold.command.renewal_configured", days));
    }

    private static int disableRenewal(CommandContext<CommandSourceStack> context) {
        return updateIdentity(
                context,
                identity -> {
                    RenewalSettings current = identity.renewal();
                    return identity.withRenewal(new RenewalSettings(
                            false, current.intervalDays(), current.warningMinutes(), 0L, current.seedMode()));
                },
                Component.translatable("message.delvefold.command.renewal_disabled_success"));
    }

    private static int updateIdentity(
            CommandContext<CommandSourceStack> context,
            java.util.function.UnaryOperator<WorldIdentitySettings> update,
            Component success) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        ConfigWriteResult result = asAuditActor(
                context,
                () -> DelvefoldConfigService.get()
                        .updateSettings(
                                snapshot.settings().revision(),
                                settings -> settings.withIdentity(update.apply(settings.identity()))));
        return reportWrite(context.getSource(), result, success);
    }

    private static int listProfiles(CommandContext<CommandSourceStack> context) {
        try {
            var profiles = DelvefoldConfigService.get().listProfiles();
            String active = DelvefoldConfigService.get().snapshot().ores().profile();
            context.getSource()
                    .sendSuccess(
                            () -> Component.translatable("message.delvefold.command.profile_list_header", active),
                            false);
            for (var profile : profiles) {
                context.getSource()
                        .sendSystemMessage(Component.translatable(
                                "message.delvefold.command.profile_list_row",
                                profile.id(),
                                profile.ruleCount(),
                                profile.id().equals(active),
                                profile.builtIn(),
                                profile.localOverride()));
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
            return reportProfile(
                    context,
                    asAuditActorIo(
                            context,
                            () -> DelvefoldConfigService.get().createProfileFromPreset(id, preset, overwrite)));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int duplicateProfile(CommandContext<CommandSourceStack> context, boolean overwrite) {
        try {
            return reportProfile(
                    context,
                    asAuditActorIo(
                            context,
                            () -> DelvefoldConfigService.get()
                                    .duplicateProfile(
                                            StringArgumentType.getString(context, "source"),
                                            StringArgumentType.getString(context, "id"),
                                            overwrite)));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int saveCurrentProfile(CommandContext<CommandSourceStack> context, boolean overwrite) {
        try {
            return reportProfile(
                    context,
                    asAuditActorIo(
                            context,
                            () -> DelvefoldConfigService.get()
                                    .saveCurrentProfileAs(StringArgumentType.getString(context, "id"), overwrite)));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int selectProfile(CommandContext<CommandSourceStack> context) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        String id = StringArgumentType.getString(context, "id");
        ConfigWriteResult result = asAuditActor(
                context,
                () -> DelvefoldConfigService.get()
                        .activateProfile(snapshot.ores().revision(), id));
        return reportWrite(
                context.getSource(), result, Component.translatable("message.delvefold.command.profile_activated", id));
    }

    private static int deleteProfile(CommandContext<CommandSourceStack> context) {
        var result = asAuditActor(
                context, () -> DelvefoldConfigService.get().deleteProfile(StringArgumentType.getString(context, "id")));
        if (!result.deleted()) {
            context.getSource().sendFailure(AdminLocalizedComponents.resolve(result.message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> AdminLocalizedComponents.resolve(result.message()), true);
        return 1;
    }

    private static int importProfile(CommandContext<CommandSourceStack> context, boolean overwrite) {
        try {
            return reportProfile(
                    context,
                    asAuditActorIo(
                            context,
                            () -> DelvefoldConfigService.get()
                                    .importProfile(
                                            StringArgumentType.getString(context, "file"),
                                            StringArgumentType.getString(context, "id"),
                                            overwrite)));
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int exportProfile(CommandContext<CommandSourceStack> context) {
        try {
            var exported = DelvefoldConfigService.get()
                    .exportProfile(
                            StringArgumentType.getString(context, "id"), StringArgumentType.getString(context, "file"));
            context.getSource()
                    .sendSuccess(
                            () -> Component.translatable(
                                    "message.delvefold.command.profile_exported", exported.getFileName()),
                            false);
            return 1;
        } catch (IOException | IllegalArgumentException exception) {
            return profileFailure(context, exception);
        }
    }

    private static int reportProfile(
            CommandContext<CommandSourceStack> context,
            com.nightsta69.delvefold.config.OreProfileCatalog.ProfileWriteResult result) {
        for (ConfigIssue issue : result.issues()) {
            context.getSource().sendSystemMessage(ConfigIssueMessages.component(issue));
        }
        if (!result.saved()) {
            context.getSource().sendFailure(AdminLocalizedComponents.resolve(result.message()));
            return 0;
        }
        context.getSource().sendSuccess(() -> AdminLocalizedComponents.resolve(result.message()), true);
        return 1;
    }

    private static int profileFailure(CommandContext<CommandSourceStack> context, Exception exception) {
        LOGGER.warn("Delvefold profile command failed", exception);
        context.getSource().sendFailure(Component.translatable("message.delvefold.command.profile_failed_safe"));
        return 0;
    }

    private static int listBackups(CommandContext<CommandSourceStack> context) {
        BackupCatalogCache.Snapshot catalog = backupSnapshot(context);
        var backups = catalog.backups();
        if (catalog.refreshing()) {
            context.getSource()
                    .sendSystemMessage(Component.translatable("message.delvefold.command.backup_catalog_refreshing"));
        }
        if (!catalog.lastError().isBlank()) {
            context.getSource()
                    .sendFailure(Component.translatable(
                            "message.delvefold.command.backup_list_failed",
                            AdminLocalizedComponents.resolve(catalog.lastError())));
        }
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                backups.isEmpty()
                                        ? "message.delvefold.command.backup_list_empty"
                                        : "message.delvefold.command.backup_list_header"),
                        false);
        for (var backup : backups) {
            Component pinState = backup.pinned()
                    ? Component.translatable("message.delvefold.command.backup_pinned")
                    : Component.empty();
            Component restoreState = Component.translatable(
                    backup.restorable()
                            ? "message.delvefold.command.backup_restorable"
                            : "message.delvefold.command.backup_archive_only");
            context.getSource()
                    .sendSystemMessage(Component.translatable(
                            "message.delvefold.command.backup_row",
                            backup.id(),
                            backup.operation(),
                            backup.terrain(),
                            humanBytes(backup.sizeBytes()),
                            pinState,
                            restoreState));
        }
        return backups.isEmpty() && catalog.refreshing() ? 1 : backups.size();
    }

    private static int retentionStatus(CommandContext<CommandSourceStack> context) {
        BackupRetentionSettings retention =
                DelvefoldConfigService.get().snapshot().settings().backupRetention();
        context.getSource()
                .sendSuccess(
                        () -> retention.enabled()
                                ? Component.translatable(
                                        "message.delvefold.command.retention_enabled",
                                        retention.maxCount(),
                                        retention.maxAgeDays(),
                                        retention.maxTotalBytes())
                                : Component.translatable("message.delvefold.command.retention_disabled"),
                        false);
        return 1;
    }

    private static int verifyBackup(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "backup");
        BackupCatalogCache.Snapshot catalog = backupSnapshot(context);
        WorldBackupCatalog.BackupSummary summary = catalog.backups().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElse(null);
        if (summary == null) {
            context.getSource()
                    .sendFailure(Component.translatable(
                            catalog.refreshing()
                                    ? "message.delvefold.command.backup_catalog_wait"
                                    : "message.delvefold.command.backup_unknown",
                            id));
            return 0;
        }
        var server = context.getSource().getServer();
        var saveRoot = saveRoot(context);
        BackupVerificationService verification = BackupVerificationService.forSave(saveRoot);
        if (verification.isInFlight(id)) {
            context.getSource()
                    .sendSuccess(
                            () -> Component.translatable("message.delvefold.command.backup_verify_running", id), false);
            return 1;
        }
        CommandSourceStack source = context.getSource();
        String auditActor = auditActor(source);
        var future = summary.legacy()
                ? AsyncAuditMutationTracker.get()
                        .startTracked(
                                saveRoot,
                                () -> verification.validateLegacyAndCreateManifestAsync(id),
                                (result, failure) -> {
                                    if (failure == null
                                            && result.status() == BackupVerificationResult.Status.LEGACY_UPGRADED) {
                                        audit(
                                                auditActor,
                                                AuditMutation.Operation.BACKUP_MANIFEST_CREATED,
                                                AuditMutation.ObjectType.BACKUP,
                                                id,
                                                -1L,
                                                -1L);
                                    }
                                })
                : verification.verifyAsync(id);
        var unusedVerificationCompletion = future.whenComplete((result, failure) -> server.execute(() -> {
            var unusedRefreshAfterVerification = BackupCatalogCache.get().invalidateAndRefresh(saveRoot);
            if (source.getEntity() instanceof ServerPlayer requestedBy
                    && server.getPlayerList().getPlayer(requestedBy.getUUID()) == null) {
                return;
            }
            if (failure != null) {
                LOGGER.error("Backup verification worker failed for {}", id, failure);
                source.sendFailure(Component.translatable("message.delvefold.backup_verification.internal_error", id));
                return;
            }
            Component message = AdminLocalizedComponents.resolve(result.message());
            if (result.successful()) {
                source.sendSuccess(() -> message, true);
            } else {
                source.sendFailure(message);
            }
        }));
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                summary.legacy()
                                        ? "message.delvefold.command.backup_legacy_started"
                                        : "message.delvefold.command.backup_verify_started",
                                id),
                        false);
        return 1;
    }

    private static int disableRetention(CommandContext<CommandSourceStack> context) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        ConfigWriteResult result = asAuditActor(
                context,
                () -> DelvefoldConfigService.get()
                        .updateSettings(
                                snapshot.settings().revision(),
                                settings -> settings.withBackupRetention(BackupRetentionSettings.defaults())));
        return reportWrite(
                context.getSource(),
                result,
                Component.translatable("message.delvefold.command.retention_disabled_success"));
    }

    private static int configureRetention(CommandContext<CommandSourceStack> context) {
        BackupRetentionSettings retention = new BackupRetentionSettings(
                true,
                IntegerArgumentType.getInteger(context, "max_count"),
                LongArgumentType.getLong(context, "max_age_days"),
                LongArgumentType.getLong(context, "max_total_bytes"));
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        ConfigWriteResult result = asAuditActor(
                context,
                () -> DelvefoldConfigService.get()
                        .updateSettings(
                                snapshot.settings().revision(), settings -> settings.withBackupRetention(retention)));
        return reportWrite(
                context.getSource(), result, Component.translatable("message.delvefold.command.retention_configured"));
    }

    private static int pinBackup(CommandContext<CommandSourceStack> context, boolean pinned) {
        try {
            String id = StringArgumentType.getString(context, "backup");
            WorldBackupCatalog catalog = backupCatalog(context);
            boolean changed = catalog.setPinned(id, pinned);
            var unusedRefreshAfterPinChange = BackupCatalogCache.get().invalidateAndRefresh(saveRoot(context));
            if (changed) {
                audit(
                        context.getSource(),
                        pinned ? AuditMutation.Operation.BACKUP_PINNED : AuditMutation.Operation.BACKUP_UNPINNED,
                        AuditMutation.ObjectType.BACKUP,
                        id,
                        -1L,
                        -1L);
            }
            context.getSource()
                    .sendSuccess(
                            () -> Component.translatable(
                                    pinned
                                            ? "message.delvefold.command.backup_pin_success"
                                            : "message.delvefold.command.backup_unpin_success",
                                    id),
                            true);
            return 1;
        } catch (IOException exception) {
            LOGGER.warn("Could not update a Delvefold backup pin", exception);
            context.getSource()
                    .sendFailure(Component.translatable("message.delvefold.command.backup_update_failed_safe"));
            return 0;
        }
    }

    private static int deleteBackup(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "backup");
        var server = context.getSource().getServer();
        var source = context.getSource();
        var requestedBy = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : null;
        var actor = auditActor(source);
        var saveRoot = saveRoot(context);
        var deletion = AsyncAuditMutationTracker.get()
                .startTracked(
                        saveRoot,
                        () -> BackupCatalogCache.get().deleteAndRefreshAsync(server, id),
                        (deleted, failure) -> {
                            if (failure == null && Boolean.TRUE.equals(deleted)) {
                                audit(
                                        actor,
                                        AuditMutation.Operation.BACKUP_DELETED,
                                        AuditMutation.ObjectType.BACKUP,
                                        id,
                                        -1L,
                                        -1L);
                            }
                        });
        var unusedDeletionCompletion = deletion.whenComplete((deleted, failure) -> server.execute(() -> {
            if (requestedBy != null && server.getPlayerList().getPlayer(requestedBy) == null) {
                return;
            }
            if (failure != null) {
                LOGGER.error("Could not delete Delvefold backup {}", id, failure);
                source.sendFailure(backupDeleteFailure(id, failure));
            } else if (!Boolean.TRUE.equals(deleted)) {
                source.sendFailure(Component.translatable("message.delvefold.command.backup_not_deleted", id));
            } else {
                source.sendSuccess(
                        () -> Component.translatable("message.delvefold.command.backup_delete_success", id), true);
            }
        }));
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable("message.delvefold.command.backup_delete_started", id), false);
        return 1;
    }

    private static int requestRestore(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "backup");
        BackupCatalogCache.Snapshot catalog = backupSnapshot(context);
        WorldBackupCatalog.BackupSummary summary = catalog.backups().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElse(null);
        if (summary == null) {
            context.getSource()
                    .sendFailure(Component.translatable(
                            catalog.refreshing()
                                    ? "message.delvefold.command.backup_catalog_wait"
                                    : "message.delvefold.command.backup_unknown",
                            id));
            return 0;
        }
        WorldOperationPreview preview = WorldRestoreService.get()
                .requestCached(
                        context.getSource().getServer(), id, context.getSource().getTextName(), summary);
        if (!preview.accepted()) {
            context.getSource().sendFailure(AdminLocalizedComponents.resolve(preview.message()));
            return 0;
        }
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "message.delvefold.command.backup_restore_preview",
                                AdminLocalizedComponents.resolve(preview.message()),
                                humanBytes(preview.estimatedBytes()),
                                preview.confirmationToken()),
                        false);
        return 1;
    }

    private static int confirmRestore(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        String backupId = WorldRestoreService.get().selectedBackupId(server);
        WorldOperationResult result =
                WorldRestoreService.get().confirm(server, StringArgumentType.getString(context, "token"));
        if (result.success()) {
            audit(
                    context.getSource(),
                    AuditMutation.Operation.BACKUP_RESTORE_ACCEPTED,
                    AuditMutation.ObjectType.BACKUP,
                    backupId,
                    -1L,
                    -1L);
        }
        return reportOperation(context.getSource(), result);
    }

    private static int cancelRestore(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        String backupId = WorldRestoreService.get().selectedBackupId(server);
        WorldOperationResult result = WorldRestoreService.get().cancel(server);
        if (result.success()) {
            audit(
                    context.getSource(),
                    AuditMutation.Operation.BACKUP_RESTORE_CANCELLED,
                    AuditMutation.ObjectType.BACKUP,
                    backupId,
                    -1L,
                    -1L);
        }
        return reportOperation(context.getSource(), result);
    }

    private static BackupCatalogCache.Snapshot backupSnapshot(CommandContext<CommandSourceStack> context) {
        return BackupCatalogCache.get().snapshot(saveRoot(context));
    }

    private static java.nio.file.Path saveRoot(CommandContext<CommandSourceStack> context) {
        return context.getSource()
                .getServer()
                .getWorldPath(LevelResource.ROOT)
                .toAbsolutePath()
                .normalize();
    }

    private static WorldBackupCatalog backupCatalog(CommandContext<CommandSourceStack> context) {
        return new WorldBackupCatalog(saveRoot(context));
    }

    private static int validateConfig(CommandContext<CommandSourceStack> context) {
        try {
            ConfigLoadResult result = DelvefoldConfigService.get().validateDisk();
            var report = new com.nightsta69.delvefold.config.validation.ValidationReport(result.issues());
            for (ConfigIssue issue : result.issues()) {
                context.getSource().sendSystemMessage(ConfigIssueMessages.component(issue));
            }
            boolean valid = report.valid() && !result.usedFallback();
            context.getSource()
                    .sendSuccess(
                            () -> Component.translatable(
                                    "message.delvefold.command.validation_complete",
                                    report.errorCount(),
                                    report.warningCount()),
                            false);
            return valid ? 1 : 0;
        } catch (IOException exception) {
            LOGGER.warn("Could not validate Delvefold configuration", exception);
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.validation_failed_safe"));
            return 0;
        }
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        try {
            ConfigLoadResult result =
                    asAuditActorIo(context, () -> DelvefoldConfigService.get().reload());
            for (ConfigIssue issue : result.issues()) {
                context.getSource().sendSystemMessage(ConfigIssueMessages.component(issue));
            }
            if (result.usedFallback()) {
                context.getSource().sendFailure(Component.translatable("message.delvefold.command.reload_rejected"));
                return 0;
            }
            context.getSource()
                    .sendSuccess(() -> Component.translatable("message.delvefold.command.reload_success"), true);
            return 1;
        } catch (IOException exception) {
            LOGGER.warn("Could not reload Delvefold configuration", exception);
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.reload_failed_safe"));
            return 0;
        }
    }

    private static int listOres(CommandContext<CommandSourceStack> context) {
        OreProfileDocument document = DelvefoldConfigService.get().snapshot().ores();
        context.getSource()
                .sendSuccess(
                        () -> document.rules().isEmpty()
                                ? Component.translatable("message.delvefold.command.ore_list_empty")
                                : Component.translatable(
                                        "message.delvefold.command.ore_list_header", document.revision()),
                        false);
        for (OreRule rule : document.rules()) {
            context.getSource()
                    .sendSystemMessage(Component.translatable(
                            "message.delvefold.command.ore_list_row",
                            Component.translatable(
                                    rule.enabled()
                                            ? "message.delvefold.command.state_on"
                                            : "message.delvefold.command.state_off"),
                            rule.id(),
                            rule.targets().size(),
                            rule.bands().size()));
        }
        return document.rules().size();
    }

    private static int showOre(CommandContext<CommandSourceStack> context, String id) {
        OreRule rule = findRule(id);
        if (rule == null) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.unknown_ore_rule", id));
            return 0;
        }
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "message.delvefold.command.ore_rule_status",
                                rule.id(),
                                rule.enabled(),
                                rule.required()),
                        false);
        for (OreTarget target : rule.targets()) {
            context.getSource()
                    .sendSystemMessage(Component.translatable(
                            "message.delvefold.command.ore_target",
                            target.sourceId(),
                            target.replaceTag(),
                            target.weight()));
        }
        for (SpawnBand band : rule.bands()) {
            Component details;
            ProvinceSettings province = band.province();
            if (band.placement() == OreBandPlacement.PROVINCE && province != null) {
                details = Component.translatable(
                        "message.delvefold.command.ore_province_details",
                        province.regionSize(),
                        province.radius(),
                        province.verticalThickness(),
                        province.density(),
                        province.perChunkWorkCap());
            } else {
                details = Component.translatable(
                        "message.delvefold.command.ore_vein_details", band.veinSize(), band.attemptsPerChunk());
            }
            context.getSource()
                    .sendSystemMessage(Component.translatable(
                            "message.delvefold.command.ore_band",
                            band.id(),
                            details,
                            band.distribution().name().toLowerCase(Locale.ROOT),
                            band.minY(),
                            band.maxY()));
        }
        return 1;
    }

    private static int addOre(CommandContext<CommandSourceStack> context) {
        ResourceLocation block = ResourceLocationArgument.getId(context, "block");
        if (block == null) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.invalid_block_id"));
            return 0;
        }
        try {
            boolean detected = "detected".equalsIgnoreCase(StringArgumentType.getString(context, "variants"));
            OreRuleFactory.Rarity rarity = OreRuleFactory.Rarity.parse(StringArgumentType.getString(context, "rarity"));
            OreRule rule = OreRuleFactory.create(block, detected, rarity);
            ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
            if (snapshot.ores().rules().stream()
                    .anyMatch(existing -> existing.id().equals(rule.id()))) {
                context.getSource()
                        .sendFailure(Component.translatable("message.delvefold.command.rule_exists", rule.id()));
                return 0;
            }
            ConfigWriteResult result = asAuditActor(
                    context,
                    () -> DelvefoldConfigService.get()
                            .updateOres(snapshot.ores().revision(), document -> {
                                List<OreRule> rules = new ArrayList<>(document.rules());
                                rules.add(rule);
                                return document.nextRevision(rules, document.profile());
                            }));
            return reportWrite(
                    context.getSource(),
                    result,
                    Component.translatable("message.delvefold.command.ore_rule_added", rule.id()));
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid Delvefold ore-add command", exception);
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.ore_add_invalid"));
            return 0;
        }
    }

    private static int setOreEnabled(CommandContext<CommandSourceStack> context, boolean enabled) {
        String id = StringArgumentType.getString(context, "rule");
        return mutateRule(
                context,
                id,
                rule -> rule.withEnabled(enabled),
                Component.translatable(
                        enabled
                                ? "message.delvefold.command.ore_rule_enabled"
                                : "message.delvefold.command.ore_rule_disabled",
                        id));
    }

    private static int removeOre(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "rule");
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (findRule(id) == null) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.unknown_ore_rule", id));
            return 0;
        }
        ConfigWriteResult result = asAuditActor(
                context,
                () -> DelvefoldConfigService.get()
                        .updateOres(
                                snapshot.ores().revision(),
                                document -> document.nextRevision(
                                        document.rules().stream()
                                                .filter(rule -> !rule.id().equals(id))
                                                .toList(),
                                        document.profile())));
        return reportWrite(
                context.getSource(), result, Component.translatable("message.delvefold.command.ore_rule_removed", id));
    }

    private static int addTarget(CommandContext<CommandSourceStack> context, int weight) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String block = ResourceLocationArgument.getId(context, "block").toString();
        String tag = ResourceLocationArgument.getId(context, "replace_tag").toString();
        return mutateRule(
                context,
                ruleId,
                rule -> {
                    List<OreTarget> targets = new ArrayList<>(rule.targets());
                    targets.add(OreTarget.of(block, tag.startsWith("#") ? tag.substring(1) : tag, weight));
                    return new OreRule(
                            rule.id(),
                            rule.enabled(),
                            rule.required(),
                            rule.terrainModes(),
                            targets,
                            rule.biomes(),
                            rule.bands());
                },
                Component.translatable("message.delvefold.command.ore_target_added", ruleId, weight));
    }

    private static int removeTarget(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String block = ResourceLocationArgument.getId(context, "block").toString();
        return mutateRule(
                context,
                ruleId,
                rule -> new OreRule(
                        rule.id(),
                        rule.enabled(),
                        rule.required(),
                        rule.terrainModes(),
                        rule.targets().stream()
                                .filter(target -> !target.block().equals(block))
                                .toList(),
                        rule.biomes(),
                        rule.bands()),
                Component.translatable("message.delvefold.command.ore_target_removed", ruleId));
    }

    private static int addTagTarget(CommandContext<CommandSourceStack> context, int weight) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String blockTag = ResourceLocationArgument.getId(context, "block_tag").toString();
        String replaceTag =
                ResourceLocationArgument.getId(context, "replace_tag").toString();
        return mutateRule(
                context,
                ruleId,
                rule -> {
                    List<OreTarget> targets = new ArrayList<>(rule.targets());
                    targets.add(OreTarget.ofTag(blockTag, replaceTag, weight));
                    return new OreRule(
                            rule.id(),
                            rule.enabled(),
                            rule.required(),
                            rule.terrainModes(),
                            targets,
                            rule.biomes(),
                            rule.bands());
                },
                Component.translatable("message.delvefold.command.ore_tag_target_added", blockTag, ruleId, weight));
    }

    private static int setTargetWeight(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String block = ResourceLocationArgument.getId(context, "block").toString();
        int weight = IntegerArgumentType.getInteger(context, "weight");
        return mutateRule(
                context,
                ruleId,
                rule -> replaceTargetWeight(rule, block, false, weight),
                Component.translatable("message.delvefold.command.ore_target_weight_set", block, weight, ruleId));
    }

    private static int setTagTargetWeight(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String blockTag = ResourceLocationArgument.getId(context, "block_tag").toString();
        int weight = IntegerArgumentType.getInteger(context, "weight");
        return mutateRule(
                context,
                ruleId,
                rule -> replaceTargetWeight(rule, blockTag, true, weight),
                Component.translatable("message.delvefold.command.ore_tag_weight_set", blockTag, weight, ruleId));
    }

    private static OreRule replaceTargetWeight(OreRule rule, String sourceId, boolean tagDriven, int weight) {
        long matches = rule.targets().stream()
                .filter(target -> tagDriven
                        ? target.blockTag().equals(sourceId)
                        : !target.tagDriven() && target.block().equals(sourceId))
                .count();
        if (matches == 0) {
            throw new IllegalArgumentException(
                    "Unknown " + (tagDriven ? "output tag: #" : "target block: ") + sourceId);
        }
        if (matches > 1) {
            throw new IllegalArgumentException("Ambiguous " + (tagDriven ? "output tag: #" : "target block: ")
                    + sourceId + "; multiple targets use that source. Edit the specific target in canonical JSON.");
        }
        List<OreTarget> targets = rule.targets().stream()
                .map(target -> (tagDriven
                                ? target.blockTag().equals(sourceId)
                                : !target.tagDriven() && target.block().equals(sourceId))
                        ? target.withWeight(weight)
                        : target)
                .toList();
        return new OreRule(
                rule.id(), rule.enabled(), rule.required(), rule.terrainModes(), targets, rule.biomes(), rule.bands());
    }

    private static int removeTagTarget(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String blockTag = ResourceLocationArgument.getId(context, "block_tag").toString();
        return mutateRule(
                context,
                ruleId,
                rule -> new OreRule(
                        rule.id(),
                        rule.enabled(),
                        rule.required(),
                        rule.terrainModes(),
                        rule.targets().stream()
                                .filter(target -> !target.blockTag().equals(blockTag))
                                .toList(),
                        rule.biomes(),
                        rule.bands()),
                Component.translatable("message.delvefold.command.ore_tag_target_removed", ruleId));
    }

    private static int addBand(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        OreRuleFactory.Rarity rarity = OreRuleFactory.Rarity.parse(StringArgumentType.getString(context, "rarity"));
        OreRule template = OreRuleFactory.create(ResourceLocation.withDefaultNamespace("iron_ore"), false, rarity);
        SpawnBand band = template.bands().getFirst();
        band = new SpawnBand(
                bandId,
                band.veinSize(),
                band.attemptsPerChunk(),
                band.distribution(),
                band.minY(),
                band.maxY(),
                band.peakY(),
                band.plateauMinY(),
                band.plateauMaxY(),
                band.discardOnAirExposure());
        SpawnBand finalBand = band;
        return mutateRule(
                context,
                ruleId,
                rule -> {
                    List<SpawnBand> bands = new ArrayList<>(rule.bands());
                    bands.add(finalBand);
                    return rule.withBands(bands);
                },
                Component.translatable("message.delvefold.command.ore_band_added", bandId, ruleId));
    }

    private static int removeBand(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        return mutateRule(
                context,
                ruleId,
                rule -> rule.withBands(rule.bands().stream()
                        .filter(band -> !band.id().equals(bandId))
                        .toList()),
                Component.translatable("message.delvefold.command.ore_band_removed", bandId, ruleId));
    }

    private static int setBandField(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        String field = StringArgumentType.getString(context, "field");
        double value = DoubleArgumentType.getDouble(context, "value");
        return mutateRule(
                context,
                ruleId,
                rule -> rule.withBands(rule.bands().stream()
                        .map(band -> band.id().equals(bandId) ? withBandField(band, field, value) : band)
                        .toList()),
                Component.translatable("message.delvefold.command.ore_band_field_set", ruleId, bandId, field));
    }

    private static int setBandPlacement(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        OreBandPlacement placement = OreBandPlacement.parse(StringArgumentType.getString(context, "mode"));
        return mutateRule(
                context,
                ruleId,
                rule -> rule.withBands(rule.bands().stream()
                        .map(band -> band.id().equals(bandId) ? withBandPlacement(band, placement) : band)
                        .toList()),
                Component.translatable(
                        "message.delvefold.command.ore_band_placement_set",
                        ruleId,
                        bandId,
                        placement.serializedName()));
    }

    private static SpawnBand withBandPlacement(SpawnBand band, OreBandPlacement placement) {
        if (placement == OreBandPlacement.PROVINCE) {
            ProvinceSettings configuredProvince = band.province();
            return new SpawnBand(
                    band.id(),
                    1,
                    0.0D,
                    band.distribution(),
                    band.minY(),
                    band.maxY(),
                    band.peakY(),
                    band.plateauMinY(),
                    band.plateauMaxY(),
                    band.discardOnAirExposure(),
                    OreBandPlacement.PROVINCE,
                    configuredProvince == null ? ProvinceSettings.defaults() : configuredProvince);
        }
        int veinSize = band.placement() == OreBandPlacement.VEIN ? band.veinSize() : 8;
        double attempts = band.placement() == OreBandPlacement.VEIN ? band.attemptsPerChunk() : 8.0D;
        return new SpawnBand(
                band.id(),
                veinSize,
                attempts,
                band.distribution(),
                band.minY(),
                band.maxY(),
                band.peakY(),
                band.plateauMinY(),
                band.plateauMaxY(),
                band.discardOnAirExposure(),
                OreBandPlacement.VEIN,
                null);
    }

    private static int setProvinceField(CommandContext<CommandSourceStack> context) {
        String ruleId = StringArgumentType.getString(context, "rule");
        String bandId = StringArgumentType.getString(context, "band");
        String field = StringArgumentType.getString(context, "field");
        double value = DoubleArgumentType.getDouble(context, "value");
        return mutateRule(
                context,
                ruleId,
                rule -> rule.withBands(rule.bands().stream()
                        .map(band -> band.id().equals(bandId) ? withProvinceField(band, field, value) : band)
                        .toList()),
                Component.translatable("message.delvefold.command.ore_province_field_set", ruleId, bandId, field));
    }

    private static SpawnBand withProvinceField(SpawnBand band, String field, double value) {
        if (band.placement() != OreBandPlacement.PROVINCE) {
            throw new IllegalArgumentException(
                    "Band " + band.id() + " is not a province; set its placement to province first");
        }
        ProvinceSettings configuredProvince = band.province();
        ProvinceSettings current = configuredProvince == null ? ProvinceSettings.defaults() : configuredProvince;
        int regionSize = current.regionSize();
        int radius = current.radius();
        int thickness = current.verticalThickness();
        double density = current.density();
        int workCap = current.perChunkWorkCap();
        switch (field) {
            case "region_size" -> regionSize = requireWhole(value, field);
            case "radius" -> radius = requireWhole(value, field);
            case "vertical_thickness" -> thickness = requireWhole(value, field);
            case "density" -> density = value;
            case "work_cap" -> workCap = requireWhole(value, field);
            default -> throw new IllegalArgumentException("Unknown province field: " + field);
        }
        return new SpawnBand(
                band.id(),
                1,
                0.0D,
                band.distribution(),
                band.minY(),
                band.maxY(),
                band.peakY(),
                band.plateauMinY(),
                band.plateauMaxY(),
                band.discardOnAirExposure(),
                OreBandPlacement.PROVINCE,
                new ProvinceSettings(regionSize, radius, thickness, density, workCap));
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
        return new SpawnBand(
                band.id(),
                vein,
                attempts,
                band.distribution(),
                min,
                max,
                peak,
                plateauMin,
                plateauMax,
                discard,
                band.placement(),
                band.province());
    }

    private static int requireWhole(double value, String field) {
        if (!Double.isFinite(value) || value != Math.rint(value)) {
            throw new IllegalArgumentException(field + " requires a whole number");
        }
        return (int) value;
    }

    private static int scanOres(CommandContext<CommandSourceStack> context, @Nullable String namespace) {
        List<ResourceLocation> matches = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> namespace == null || namespace.equals(id.getNamespace()))
                .filter(id -> id.getPath().endsWith("_ore") || id.getPath().startsWith("ore_"))
                .sorted()
                .limit(100)
                .toList();
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable("message.delvefold.command.ore_scan_header", matches.size()),
                        false);
        for (ResourceLocation match : matches) {
            context.getSource()
                    .sendSystemMessage(Component.translatable("message.delvefold.command.ore_scan_row", match));
        }
        return matches.size();
    }

    private static int requestRecreate(
            CommandContext<CommandSourceStack> context, @Nullable TerrainMode selected, BackupMode backup) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        return requestRecreate(context, selected, snapshot.settings().identity().terrainVariant(), backup);
    }

    private static int requestRecreate(
            CommandContext<CommandSourceStack> context,
            @Nullable TerrainMode selected,
            TerrainVariant variant,
            BackupMode backup) {
        return requestRecreate(context, selected, variant, null, backup);
    }

    private static int requestRecreate(
            CommandContext<CommandSourceStack> context,
            @Nullable TerrainMode selected,
            TerrainVariant variant,
            @Nullable GeologyTheme theme,
            BackupMode backup) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        @Nullable TerrainMode target = selected == null ? snapshot.settings().terrainMode() : selected;
        GeologyTheme targetTheme =
                theme == null ? snapshot.settings().identity().geologyTheme() : theme;
        WorldOperationRequest base = WorldOperationRequest.recreate(target, variant, targetTheme);
        WorldOperationRequest request = new WorldOperationRequest(
                base.type(),
                base.targetTerrain(),
                base.targetVariant(),
                base.targetGeologyTheme(),
                null,
                null,
                backup,
                false);
        return reportPreview(
                context,
                WorldOperationService.get()
                        .request(
                                context.getSource().getServer(),
                                request,
                                context.getSource().getTextName()));
    }

    private static int requestDelete(CommandContext<CommandSourceStack> context, BackupMode backup) {
        WorldOperationRequest base = WorldOperationRequest.delete();
        WorldOperationRequest request = new WorldOperationRequest(base.type(), null, null, null, null, backup, false);
        return reportPreview(
                context,
                WorldOperationService.get()
                        .request(
                                context.getSource().getServer(),
                                request,
                                context.getSource().getTextName()));
    }

    private static int confirmWorldOperation(CommandContext<CommandSourceStack> context) {
        WorldOperationResult result = WorldOperationService.get()
                .confirm(context.getSource().getServer(), StringArgumentType.getString(context, "token"));
        if (result.success()) {
            long revision = DelvefoldConfigService.get().snapshot().settings().revision();
            audit(
                    context.getSource(),
                    AuditMutation.Operation.WORLD_OPERATION_ACCEPTED,
                    AuditMutation.ObjectType.WORLD,
                    "mining_world",
                    revision,
                    revision);
        }
        return reportOperation(context.getSource(), result);
    }

    private static int cancelWorldOperation(CommandContext<CommandSourceStack> context) {
        WorldOperationResult result =
                WorldOperationService.get().cancelConfirmed(context.getSource().getServer());
        if (!result.success()) {
            result = WorldOperationService.get().cancel(context.getSource().getServer());
        }
        if (result.success()) {
            long revision = DelvefoldConfigService.get().snapshot().settings().revision();
            audit(
                    context.getSource(),
                    AuditMutation.Operation.WORLD_OPERATION_CANCELLED,
                    AuditMutation.ObjectType.WORLD,
                    "mining_world",
                    revision,
                    revision);
        }
        return reportOperation(context.getSource(), result);
    }

    private static int reportPreview(CommandContext<CommandSourceStack> context, WorldOperationPreview preview) {
        if (!preview.accepted()) {
            context.getSource().sendFailure(AdminLocalizedComponents.resolve(preview.message()));
            return 0;
        }
        context.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                "message.delvefold.command.world_preview",
                                AdminLocalizedComponents.resolve(preview.message()),
                                humanBytes(preview.estimatedBytes()),
                                preview.playersToEvacuate(),
                                preview.confirmationToken()),
                        false);
        return 1;
    }

    private static int reportOperation(CommandSourceStack source, WorldOperationResult result) {
        if (!result.success()) {
            source.sendFailure(AdminLocalizedComponents.resolve(result.message()));
            return 0;
        }
        source.sendSuccess(() -> AdminLocalizedComponents.resolve(result.message()), true);
        return 1;
    }

    private static int mutateRule(
            CommandContext<CommandSourceStack> context,
            String id,
            java.util.function.UnaryOperator<OreRule> mutation,
            Component successMessage) {
        ConfigSnapshot snapshot = DelvefoldConfigService.get().snapshot();
        if (findRule(id) == null) {
            context.getSource().sendFailure(Component.translatable("message.delvefold.command.unknown_ore_rule", id));
            return 0;
        }
        try {
            ConfigWriteResult result = asAuditActor(
                    context,
                    () -> DelvefoldConfigService.get()
                            .updateOres(
                                    snapshot.ores().revision(),
                                    document -> document.nextRevision(
                                            document.rules().stream()
                                                    .map(rule -> rule.id().equals(id) ? mutation.apply(rule) : rule)
                                                    .toList(),
                                            document.profile())));
            return reportWrite(context.getSource(), result, successMessage);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Invalid Delvefold ore-rule mutation for {}", id, exception);
            context.getSource()
                    .sendFailure(Component.translatable("message.delvefold.command.ore_mutation_invalid", id));
            return 0;
        }
    }

    private static @Nullable OreRule findRule(String id) {
        return DelvefoldConfigService.get().snapshot().ores().rules().stream()
                .filter(rule -> rule.id().equals(id))
                .findFirst()
                .orElse(null);
    }

    private static int reportWrite(CommandSourceStack source, ConfigWriteResult result, Component successMessage) {
        if (!result.saved()) {
            for (ConfigIssue issue : result.issues()) {
                source.sendFailure(ConfigIssueMessages.component(issue));
            }
            return 0;
        }
        for (ConfigIssue issue : result.issues()) {
            source.sendSystemMessage(ConfigIssueMessages.component(issue));
        }
        source.sendSuccess(() -> successMessage, true);
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

    private static String auditActor(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player
                ? player.getGameProfile().getName()
                : "console";
    }

    // Closing the intentionally unused scope restores the thread-local audit actor.
    @SuppressWarnings("try")
    private static <T> T asAuditActor(
            CommandContext<CommandSourceStack> context, java.util.function.Supplier<T> action) {
        try (DelvefoldAuditService.ActorScope ignored =
                DelvefoldAuditService.get().pushActor(auditActor(context.getSource()))) {
            return action.get();
        }
    }

    // Closing the intentionally unused scope restores the thread-local audit actor.
    @SuppressWarnings("try")
    private static <T> T asAuditActorIo(CommandContext<CommandSourceStack> context, IoAuditCall<T> action)
            throws IOException {
        try (DelvefoldAuditService.ActorScope ignored =
                DelvefoldAuditService.get().pushActor(auditActor(context.getSource()))) {
            return action.run();
        }
    }

    @FunctionalInterface
    private interface IoAuditCall<T> {
        T run() throws IOException;
    }

    private static boolean sourceStillAvailable(CommandSourceStack source) {
        return !(source.getEntity() instanceof ServerPlayer player)
                || source.getServer().getPlayerList().getPlayer(player.getUUID()) != null;
    }

    private static Component backupDeleteFailure(String backupId, Throwable failure) {
        BackupDeletionGuard.DeletionRejectedException rejection = deletionRejection(failure);
        if (rejection == null) {
            return Component.translatable("message.delvefold.command.backup_delete_failed_safe", backupId);
        }
        String key =
                switch (rejection.reason()) {
                    case REFERENCED -> "message.delvefold.backup_delete.referenced";
                    case IN_PROGRESS -> "message.delvefold.backup_delete.in_progress";
                    case SESSION_CLOSED -> "message.delvefold.backup_delete.session_closed";
                };
        return Component.translatable(key, backupId);
    }

    // Throwable cause cycles are identity cycles; value equality may recurse or conflate distinct causes.
    @SuppressWarnings("ReferenceEquality")
    private static BackupDeletionGuard.@Nullable DeletionRejectedException deletionRejection(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof BackupDeletionGuard.DeletionRejectedException rejection) {
                return rejection;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    private static void audit(
            CommandSourceStack source,
            AuditMutation.Operation operation,
            AuditMutation.ObjectType objectType,
            String objectId,
            long oldRevision,
            long newRevision) {
        audit(auditActor(source), operation, objectType, objectId, oldRevision, newRevision);
    }

    private static void audit(
            String actor,
            AuditMutation.Operation operation,
            AuditMutation.ObjectType objectType,
            String objectId,
            long oldRevision,
            long newRevision) {
        try {
            DelvefoldAuditService.get()
                    .record(new AuditMutation(actor, operation, objectType, objectId, oldRevision, newRevision));
        } catch (IllegalArgumentException ignored) {
            // Audit validation must never roll back an accepted gameplay or administration action.
        }
    }
}
