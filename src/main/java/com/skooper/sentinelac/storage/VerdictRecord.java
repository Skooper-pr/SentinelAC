package com.skooper.sentinelac.storage;

/**
 * Persistent staff verdict labeled for supervised training data.
 */
public final class VerdictRecord {

    private final int id;
    private final int flagId;
    private final String outcome; // "upheld" or "overturned"
    private final String staffMember;
    private final long timestamp;

    public VerdictRecord(int id, int flagId, String outcome, String staffMember, long timestamp) {
        this.id = id;
        this.flagId = flagId;
        this.outcome = outcome;
        this.staffMember = staffMember;
        this.timestamp = timestamp;
    }

    public int getId() {
        return id;
    }

    public int getFlagId() {
        return flagId;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getStaffMember() {
        return staffMember;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
