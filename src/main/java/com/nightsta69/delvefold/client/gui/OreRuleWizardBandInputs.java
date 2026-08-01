package com.nightsta69.delvefold.client.gui;

import com.nightsta69.delvefold.network.model.AdminSnapshot;

/**
 * Mutable text entered for the currently displayed ore band.
 *
 * <p>The wizard deliberately retains text instead of eagerly coercing it to numbers. That distinction lets an invalid,
 * unsaved value survive a server-snapshot refresh so the player can correct it without losing work.
 */
final class OreRuleWizardBandInputs {
    private int bandIndex = -1;
    private String bandId = "";
    private String veinSize = "";
    private String attempts = "";
    private String minY = "";
    private String maxY = "";
    private String peakY = "";
    private String plateauMin = "";
    private String plateauMax = "";
    private String airDiscard = "";

    /**
     * Initializes the raw fields from a committed band unless they already belong to the requested index.
     *
     * @param requestedBandIndex zero-based band index being displayed
     * @param band committed band used to initialize the fields
     */
    void prime(int requestedBandIndex, AdminSnapshot.OreBandDraft band) {
        if (this.bandIndex == requestedBandIndex) {
            return;
        }
        this.bandIndex = requestedBandIndex;
        this.bandId = band.id();
        this.veinSize = Integer.toString(band.veinSize());
        this.attempts = Double.toString(band.attemptsPerChunk());
        this.minY = Integer.toString(band.minY());
        this.maxY = Integer.toString(band.maxY());
        this.peakY = Integer.toString(band.peakY());
        this.plateauMin = Integer.toString(band.plateauMinY());
        this.plateauMax = Integer.toString(band.plateauMaxY());
        this.airDiscard = Double.toString(band.discardOnAirExposure());
    }

    /** Marks the text as stale so the next displayed band primes fresh committed values. */
    void invalidateBand() {
        this.bandIndex = -1;
    }

    /**
     * Copies every raw field, including its ownership index, from another wizard state.
     *
     * @param source raw inputs to copy
     */
    void copyFrom(OreRuleWizardBandInputs source) {
        this.bandIndex = source.bandIndex;
        this.bandId = source.bandId;
        this.veinSize = source.veinSize;
        this.attempts = source.attempts;
        this.minY = source.minY;
        this.maxY = source.maxY;
        this.peakY = source.peakY;
        this.plateauMin = source.plateauMin;
        this.plateauMax = source.plateauMax;
        this.airDiscard = source.airDiscard;
    }

    /**
     * Returns an immutable point-in-time view for pure validation.
     *
     * @return all raw band values in widget order
     */
    Values values() {
        return new Values(
                this.bandId,
                this.veinSize,
                this.attempts,
                this.minY,
                this.maxY,
                this.peakY,
                this.plateauMin,
                this.plateauMax,
                this.airDiscard);
    }

    String bandId() {
        return this.bandId;
    }

    void setBandId(String value) {
        this.bandId = value;
    }

    String veinSize() {
        return this.veinSize;
    }

    void setVeinSize(String value) {
        this.veinSize = value;
    }

    String attempts() {
        return this.attempts;
    }

    void setAttempts(String value) {
        this.attempts = value;
    }

    String minY() {
        return this.minY;
    }

    void setMinY(String value) {
        this.minY = value;
    }

    String maxY() {
        return this.maxY;
    }

    void setMaxY(String value) {
        this.maxY = value;
    }

    String peakY() {
        return this.peakY;
    }

    void setPeakY(String value) {
        this.peakY = value;
    }

    String plateauMin() {
        return this.plateauMin;
    }

    void setPlateauMin(String value) {
        this.plateauMin = value;
    }

    String plateauMax() {
        return this.plateauMax;
    }

    void setPlateauMax(String value) {
        this.plateauMax = value;
    }

    String airDiscard() {
        return this.airDiscard;
    }

    void setAirDiscard(String value) {
        this.airDiscard = value;
    }

    /**
     * Immutable raw values consumed by band validation.
     *
     * @param bandId band identifier text
     * @param veinSize vein-size text
     * @param attempts attempts-per-chunk text
     * @param minY minimum-height text
     * @param maxY maximum-height text
     * @param peakY triangle-peak text
     * @param plateauMin trapezoid plateau-minimum text
     * @param plateauMax trapezoid plateau-maximum text
     * @param airDiscard air-exposure discard-probability text
     */
    record Values(
            String bandId,
            String veinSize,
            String attempts,
            String minY,
            String maxY,
            String peakY,
            String plateauMin,
            String plateauMax,
            String airDiscard) {}
}
