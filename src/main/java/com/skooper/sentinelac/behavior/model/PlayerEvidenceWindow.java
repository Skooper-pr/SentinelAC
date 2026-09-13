package com.skooper.sentinelac.behavior.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Rolling temporal window of behavioral interaction samples for a single player.
 */
public final class PlayerEvidenceWindow {

    private final UUID playerId;
    private final List<ClickSample> clickSamples;
    private final List<AimSample> aimSamples;

    public PlayerEvidenceWindow(UUID playerId, List<ClickSample> clickSamples, List<AimSample> aimSamples) {
        this.playerId = playerId;
        this.clickSamples = Collections.unmodifiableList(new ArrayList<>(clickSamples));
        this.aimSamples = Collections.unmodifiableList(new ArrayList<>(aimSamples));
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public List<ClickSample> getClickSamples() {
        return clickSamples;
    }

    public List<AimSample> getAimSamples() {
        return aimSamples;
    }

    public double[] getClickIntervals() {
        double[] intervals = new double[clickSamples.size()];
        for (int i = 0; i < clickSamples.size(); i++) {
            intervals[i] = clickSamples.get(i).getIntervalMs();
        }
        return intervals;
    }

    public double[] getYawDeltas() {
        double[] deltas = new double[aimSamples.size()];
        for (int i = 0; i < aimSamples.size(); i++) {
            deltas[i] = aimSamples.get(i).getYawDelta();
        }
        return deltas;
    }
}
