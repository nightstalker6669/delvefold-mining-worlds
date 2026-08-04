package com.nightsta69.delvefold.network.codec;

import com.nightsta69.delvefold.config.importer.OreImportModels.Evidence;
import com.nightsta69.delvefold.config.importer.OreImportModels.HostKind;
import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.OreLibraryView;
import com.nightsta69.delvefold.network.model.OreLibraryView.Family;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Strict protocol-14 codec for bounded Unified Ores library pages. */
public final class OreLibraryStreamCodec {
    private OreLibraryStreamCodec() {}

    /**
     * Writes one server-authoritative library page.
     *
     * @param buffer destination buffer
     * @param view immutable bounded library page
     */
    public static void write(RegistryFriendlyByteBuf buffer, OreLibraryView view) {
        int start = buffer.writerIndex();
        buffer.writeUtf(view.catalogToken(), ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
        buffer.writeVarLong(view.expectedOreRevision());
        buffer.writeVarInt(view.page());
        buffer.writeVarInt(view.pageCount());
        buffer.writeVarInt(view.totalFamilies());
        buffer.writeUtf(view.query(), ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH);
        buffer.writeBoolean(view.showConfigured());
        buffer.writeBoolean(view.truncated());
        buffer.writeVarInt(view.families().size());
        for (Family family : view.families()) {
            writeId(buffer, family.id());
            writeId(buffer, family.material());
            writeId(buffer, family.suggestedRuleId());
            writeId(buffer, family.preferredBlockId());
            buffer.writeVarInt(writeHostKind(family.preferredHostKind()));
            buffer.writeVarInt(family.providerCount());
            buffer.writeVarInt(family.candidateCount());
            buffer.writeVarInt(family.importableCandidateCount());
            buffer.writeVarInt(writeEvidence(family.evidence()));
            buffer.writeBoolean(family.configured());
            buffer.writeBoolean(family.reviewRequired());
            buffer.writeBoolean(family.overflow());
        }
        ensureBudget(buffer.writerIndex() - start);
    }

    /**
     * Reads one library page while rejecting invalid allocation counts and enum ordinals.
     *
     * @param buffer source buffer
     * @return validated immutable library page
     */
    public static OreLibraryView read(RegistryFriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        String catalogToken = buffer.readUtf(ProtocolLimits.MAX_IMPORT_TOKEN_LENGTH);
        long revision = buffer.readVarLong();
        int page = buffer.readVarInt();
        int pageCount = buffer.readVarInt();
        int total = buffer.readVarInt();
        String query = buffer.readUtf(ProtocolLimits.MAX_ORE_LIBRARY_QUERY_LENGTH);
        boolean showConfigured = buffer.readBoolean();
        boolean truncated = buffer.readBoolean();
        int count = buffer.readVarInt();
        if (count < 0 || count > ProtocolLimits.MAX_ORE_LIBRARY_FAMILIES_PER_PAGE) {
            throw new IllegalArgumentException("Invalid ore library page size: " + count);
        }
        List<Family> families = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String id = readId(buffer);
            String material = readId(buffer);
            String suggestedRuleId = readId(buffer);
            String preferredBlock = readId(buffer);
            HostKind preferredHost = readHostKind(buffer.readVarInt());
            int providers = buffer.readVarInt();
            int candidates = buffer.readVarInt();
            int importable = buffer.readVarInt();
            int evidenceOrdinal = buffer.readVarInt();
            families.add(new Family(
                    id,
                    material,
                    suggestedRuleId,
                    preferredBlock,
                    preferredHost,
                    providers,
                    candidates,
                    importable,
                    readEvidence(evidenceOrdinal),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()));
            ensureBudget(buffer.readerIndex() - start);
        }
        return new OreLibraryView(
                catalogToken, revision, page, pageCount, total, query, showConfigured, truncated, families);
    }

    private static void writeId(RegistryFriendlyByteBuf buffer, String value) {
        buffer.writeUtf(value, ProtocolLimits.ID_LENGTH);
    }

    private static String readId(RegistryFriendlyByteBuf buffer) {
        return buffer.readUtf(ProtocolLimits.ID_LENGTH);
    }

    private static int writeEvidence(Evidence evidence) {
        return switch (evidence) {
            case ORE_LIKE_NAME -> 0;
            case COMMON_ORES_TAG -> 1;
            case CONVENTIONAL_TAG -> 2;
        };
    }

    private static Evidence readEvidence(int encoded) {
        return switch (encoded) {
            case 0 -> Evidence.ORE_LIKE_NAME;
            case 1 -> Evidence.COMMON_ORES_TAG;
            case 2 -> Evidence.CONVENTIONAL_TAG;
            default -> throw new IllegalArgumentException("Invalid ore library evidence value: " + encoded);
        };
    }

    private static int writeHostKind(HostKind hostKind) {
        return hostKind.wireId();
    }

    private static HostKind readHostKind(int encoded) {
        return HostKind.fromWireId(encoded);
    }

    private static void ensureBudget(int bytes) {
        if (bytes < 0 || bytes > ProtocolLimits.MAX_ORE_LIBRARY_NETWORK_BYTES) {
            throw new IllegalArgumentException("Unified Ores library payload exceeds its network budget");
        }
    }
}
