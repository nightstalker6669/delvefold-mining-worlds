package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.network.ProtocolLimits;
import com.nightsta69.delvefold.network.model.OreImportViews.PreviewView;
import com.nightsta69.delvefold.network.model.OreImportViews.ScanView;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Holds the local, non-authoritative interaction state for {@link DelvefoldOreImportScreen}.
 *
 * <p>Server-issued scan and commit tokens remain opaque values carried by the immutable views. This class never sends a
 * request or decides whether an import is authorized; it only models deterministic page windows, selection,
 * replacement, and client-side profile-ID feedback.
 */
final class OreImportScreenState {
    static final int SCAN_ROW_HEIGHT = 28;
    static final int PREVIEW_ROW_HEIGHT = 24;

    private static final Pattern PROFILE_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final String DEFAULT_TARGET_PROFILE_ID = "modded_ores";

    private final Set<String> existingProfileIds;
    private final Set<String> selectedGroupIds = new LinkedHashSet<>();
    private @Nullable ScanView scan;
    private @Nullable PreviewView preview;
    private int localOffset;
    private int visibleRows = 1;
    private String targetProfileId = DEFAULT_TARGET_PROFILE_ID;

    /**
     * Creates empty local importer state from the server snapshot's existing profile identifiers.
     *
     * @param existingProfileIds immutable-at-construction profile ID inventory
     */
    OreImportScreenState(List<String> existingProfileIds) {
        this.existingProfileIds = Set.copyOf(existingProfileIds);
    }

    /**
     * Replaces the active scan page and invalidates any displayed preview.
     *
     * <p>Selection is retained while paging within one scan token and cleared when a new scan session arrives. Local
     * offsets always restart at the beginning of a replacement server page.
     *
     * @param replacement immutable server-owned scan page
     */
    void acceptScan(ScanView replacement) {
        @Nullable ScanView currentScan = this.scan;
        boolean newSession = currentScan == null || !currentScan.scanToken().equals(replacement.scanToken());
        this.scan = replacement;
        this.preview = null;
        this.localOffset = 0;
        if (newSession) {
            this.selectedGroupIds.clear();
        }
    }

    /**
     * Replaces the displayed preview page without changing its originating scan or local selection.
     *
     * @param replacement immutable server-owned preview page
     */
    void acceptPreview(PreviewView replacement) {
        this.preview = replacement;
        this.localOffset = 0;
    }

    /** Returns to the retained scan page while preserving the user's selected groups and target profile ID. */
    void backToScan() {
        this.preview = null;
        this.localOffset = 0;
    }

    /**
     * Returns the active scan page.
     *
     * @return active server scan, or {@code null} before one arrives
     */
    @Nullable ScanView scan() {
        return scan;
    }

    /**
     * Returns the active preview page.
     *
     * @return active server preview, or {@code null} while displaying discovery
     */
    @Nullable PreviewView preview() {
        return preview;
    }

    /**
     * Reports whether a group is selected in the retained scan session.
     *
     * @param groupId stable server-issued group ID
     * @return whether the group is selected
     */
    boolean selected(String groupId) {
        return selectedGroupIds.contains(groupId);
    }

    /**
     * Returns the number of selected groups.
     *
     * @return selected group count
     */
    int selectedCount() {
        return selectedGroupIds.size();
    }

    /**
     * Toggles one group when the server-described group contains an importable candidate.
     *
     * @param groupId stable server-issued group ID
     * @param selectable whether the group has at least one candidate eligible for import
     * @return whether selection changed
     */
    boolean toggleGroup(String groupId, boolean selectable) {
        if (!selectable) {
            return false;
        }
        if (selectedGroupIds.remove(groupId)) {
            return true;
        }
        selectedGroupIds.add(groupId);
        return true;
    }

    /**
     * Returns selected group IDs in deterministic request order.
     *
     * @return immutable, lexicographically sorted IDs
     */
    List<String> sortedSelectedGroupIds() {
        return selectedGroupIds.stream().sorted().toList();
    }

    /**
     * Calculates and stores the visible local window for a scan page after initialization or resize.
     *
     * @param availableHeight pixel height available to group rows
     * @return bounded scan-page window
     */
    PageWindow resizeScanWindow(int availableHeight) {
        @Nullable ScanView activeScan = this.scan;
        int size = activeScan == null ? 0 : activeScan.groups().size();
        return resizeWindow(size, availableHeight, SCAN_ROW_HEIGHT);
    }

    /**
     * Calculates and stores the visible local window for a preview page after initialization or resize.
     *
     * @param availableHeight pixel height available to diff rows
     * @return bounded preview-page window
     */
    PageWindow resizePreviewWindow(int availableHeight) {
        @Nullable PreviewView activePreview = this.preview;
        int size = activePreview == null ? 0 : activePreview.diff().size();
        return resizeWindow(size, availableHeight, PREVIEW_ROW_HEIGHT, false);
    }

    /**
     * Calculates a preview window that may contain zero rows when no complete row fits above fixed feedback chrome.
     *
     * @param availableHeight pixel height reserved exclusively for diff rows
     * @return bounded preview-page window, possibly empty despite a non-empty authoritative page
     */
    PageWindow resizePreviewWindowAllowEmpty(int availableHeight) {
        @Nullable PreviewView activePreview = this.preview;
        int size = activePreview == null ? 0 : activePreview.diff().size();
        return resizeWindow(size, availableHeight, PREVIEW_ROW_HEIGHT, true);
    }

    /**
     * Returns the current local row offset.
     *
     * @return zero-based local row offset within the active server page
     */
    int localOffset() {
        return localOffset;
    }

    /**
     * Returns the current number of locally visible rows.
     *
     * @return row capacity; zero only when a height-constrained preview cannot display one complete row
     */
    int visibleRows() {
        return visibleRows;
    }

    /**
     * Moves to the previous local window or requests the previous authoritative scan page.
     *
     * @return local redraw, server-page request, or no-op transition
     */
    PageTransition previousScanPage() {
        @Nullable ScanView activeScan = this.scan;
        return activeScan == null ? PageTransition.none() : previousPage(activeScan.page());
    }

    /**
     * Moves to the next local window or requests the next authoritative scan page.
     *
     * @return local redraw, server-page request, or no-op transition
     */
    PageTransition nextScanPage() {
        @Nullable ScanView activeScan = this.scan;
        return activeScan == null
                ? PageTransition.none()
                : nextPage(
                        activeScan.page(),
                        activeScan.pageCount(),
                        activeScan.groups().size());
    }

    /**
     * Moves to the previous local window or requests the previous authoritative preview page.
     *
     * @return local redraw, server-page request, or no-op transition
     */
    PageTransition previousPreviewPage() {
        @Nullable PreviewView activePreview = this.preview;
        return activePreview == null ? PageTransition.none() : previousPage(activePreview.page());
    }

    /**
     * Moves to the next local window or requests the next authoritative preview page.
     *
     * @return local redraw, server-page request, or no-op transition
     */
    PageTransition nextPreviewPage() {
        @Nullable PreviewView activePreview = this.preview;
        return activePreview == null
                ? PageTransition.none()
                : nextPage(
                        activePreview.page(),
                        activePreview.pageCount(),
                        activePreview.diff().size());
    }

    /**
     * Applies line or page keyboard/scroll movement and crosses a server-page boundary only at a local edge.
     *
     * @param rows signed local row delta
     * @return local redraw, server-page request, or no-op transition
     */
    PageTransition moveRows(int rows) {
        if (rows == 0) {
            return PageTransition.none();
        }
        if (this.visibleRows == 0) {
            return rows < 0
                    ? this.preview == null ? previousScanPage() : previousPreviewPage()
                    : this.preview == null ? nextScanPage() : nextPreviewPage();
        }
        int next = Math.clamp(this.localOffset + rows, 0, maximumLocalOffset());
        if (next != this.localOffset) {
            this.localOffset = next;
            return PageTransition.local();
        }
        if (rows < 0) {
            return this.preview == null ? previousScanPage() : previousPreviewPage();
        }
        return this.preview == null ? nextScanPage() : nextPreviewPage();
    }

    /**
     * Moves to a bounded absolute local offset without crossing an authoritative page boundary.
     *
     * @param offset requested local row offset
     * @return whether a redraw is required
     */
    boolean setLocalOffset(int offset) {
        int bounded = Math.clamp(offset, 0, maximumLocalOffset());
        if (bounded == this.localOffset) {
            return false;
        }
        this.localOffset = bounded;
        return true;
    }

    /**
     * Returns the largest valid local offset for the active server page and current visible-row count.
     *
     * @return non-negative final local offset
     */
    int maximumLocalOffset() {
        return Math.max(0, activeItemCount() - this.visibleRows);
    }

    /**
     * Stores the trimmed profile ID shown and submitted by the screen.
     *
     * @param value edit-box value
     */
    void setTargetProfileId(String value) {
        this.targetProfileId = value.trim();
    }

    /**
     * Returns the normalized target profile identifier.
     *
     * @return trimmed target profile ID
     */
    String targetProfileId() {
        return targetProfileId;
    }

    /**
     * Classifies the current target profile ID for localized client feedback.
     *
     * @return validation disposition; only {@link ProfileIdIssue#NONE} permits creation
     */
    ProfileIdIssue profileIdIssue() {
        String id = this.targetProfileId.trim();
        if (id.isEmpty()) {
            return ProfileIdIssue.EMPTY;
        }
        if (id.length() > ProtocolLimits.ID_LENGTH) {
            return ProfileIdIssue.TOO_LONG;
        }
        if (!PROFILE_ID.matcher(id).matches()) {
            return ProfileIdIssue.INVALID;
        }
        if (this.existingProfileIds.contains(id)) {
            return ProfileIdIssue.EXISTS;
        }
        return ProfileIdIssue.NONE;
    }

    /**
     * Applies local affordance checks to the active server-authoritative preview.
     *
     * @return whether the Create button may submit the active commit token
     */
    boolean canCreate() {
        @Nullable PreviewView activePreview = this.preview;
        return activePreview != null
                && activePreview.valid()
                && activePreview.addedRuleCount() > 0
                && profileIdIssue() == ProfileIdIssue.NONE;
    }

    private PageWindow resizeWindow(int itemCount, int availableHeight, int rowHeight) {
        return resizeWindow(itemCount, availableHeight, rowHeight, false);
    }

    private PageWindow resizeWindow(int itemCount, int availableHeight, int rowHeight, boolean allowEmpty) {
        if (allowEmpty && availableHeight < rowHeight) {
            this.visibleRows = 0;
            this.localOffset = 0;
            return new PageWindow(0, 0, 0);
        }
        int usableHeight = Math.max(rowHeight, availableHeight);
        this.visibleRows = Math.max(1, Math.min(itemCount, usableHeight / rowHeight));
        this.localOffset = Math.clamp(this.localOffset, 0, Math.max(0, itemCount - this.visibleRows));
        return new PageWindow(
                this.localOffset, Math.min(itemCount, this.localOffset + this.visibleRows), this.visibleRows);
    }

    private PageTransition previousPage(int page) {
        if (this.localOffset > 0) {
            this.localOffset = Math.max(0, this.localOffset - this.visibleRows);
            return PageTransition.local();
        }
        return page > 0 ? PageTransition.request(page - 1) : PageTransition.none();
    }

    private PageTransition nextPage(int page, int pageCount, int itemCount) {
        if (this.visibleRows == 0) {
            return page + 1 < pageCount ? PageTransition.request(page + 1) : PageTransition.none();
        }
        if (this.localOffset + this.visibleRows < itemCount) {
            this.localOffset = Math.min(this.localOffset + this.visibleRows, Math.max(0, itemCount - this.visibleRows));
            return PageTransition.local();
        }
        return page + 1 < pageCount ? PageTransition.request(page + 1) : PageTransition.none();
    }

    private int activeItemCount() {
        @Nullable PreviewView activePreview = this.preview;
        @Nullable ScanView activeScan = this.scan;
        return activePreview == null
                ? activeScan == null ? 0 : activeScan.groups().size()
                : activePreview.diff().size();
    }

    /** Target-profile validation states mapped to translated feedback by the screen. */
    enum ProfileIdIssue {
        /** The target profile ID is locally valid and does not collide with the snapshot inventory. */
        NONE,
        /** The normalized target profile ID is empty. */
        EMPTY,
        /** The target profile ID exceeds the protocol's identifier bound. */
        TOO_LONG,
        /** The target profile ID contains characters outside the stable command/profile grammar. */
        INVALID,
        /** The target profile ID already exists in the server snapshot. */
        EXISTS
    }

    /**
     * Bounded local slice rendered from one authoritative server page.
     *
     * @param startInclusive first visible local item index
     * @param endExclusive exclusive final visible local item index
     * @param visibleRows number of rows reserved by layout; a constrained preview may reserve zero
     */
    record PageWindow(int startInclusive, int endExclusive, int visibleRows) {}

    /**
     * Result of a local navigation attempt.
     *
     * @param localChanged whether widgets must be rebuilt for a new local offset
     * @param requestedPage non-negative authoritative page to request, or {@code -1} when no request is needed
     */
    record PageTransition(boolean localChanged, int requestedPage) {
        private static PageTransition none() {
            return new PageTransition(false, -1);
        }

        private static PageTransition local() {
            return new PageTransition(true, -1);
        }

        private static PageTransition request(int page) {
            return new PageTransition(false, page);
        }

        /**
         * Reports whether navigation crossed an authoritative server-page boundary.
         *
         * @return whether the screen must issue a token-bearing page request
         */
        boolean requestsServerPage() {
            return requestedPage >= 0;
        }
    }
}
