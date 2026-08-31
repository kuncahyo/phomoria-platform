package com.phomoria.effects;

import com.phomoria.debug.DebugLog;
import com.phomoria.session.PhotoSession;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhotoEffectSession {
    private final List<PhotoEffect> effects = new ArrayList<>();
    private boolean applyToAll;
    private PhotoEffect allEffect = PhotoEffect.NORMAL;

    public PhotoEffectSession(int slotCount) {
        int count = Math.max(1, slotCount);
        for (int i = 0; i < count; i++) effects.add(PhotoEffect.NORMAL);
    }

    public void setApplyToAll(boolean value) {
        applyToAll = value;
        DebugLog.info("EffectSession applyToAll=" + value);
        if (value) {
            setAllEffect(allEffect);
        }
    }

    public boolean isApplyToAll() { return applyToAll; }

    public void setAllEffect(PhotoEffect effect) {
        allEffect = effect == null ? PhotoEffect.NORMAL : effect;
        if (applyToAll) {
            for (int i = 0; i < effects.size(); i++) effects.set(i, allEffect);
        }
        DebugLog.info("EffectSession allEffect=" + allEffect);
    }

    public PhotoEffect getAllEffect() { return allEffect; }

    public void setEffect(int slotIndex, PhotoEffect effect) {
        if (slotIndex < 0 || slotIndex >= effects.size()) return;
        PhotoEffect safe = effect == null ? PhotoEffect.NORMAL : effect;
        if (applyToAll) {
            setAllEffect(safe);
            return;
        }
        effects.set(slotIndex, safe);
        DebugLog.info("EffectSession slot=" + slotIndex + " effect=" + safe);
    }

    public PhotoEffect getEffect(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= effects.size()) return PhotoEffect.NORMAL;
        return effects.get(slotIndex);
    }

    public List<PhotoEffect> getEffects() {
        return Collections.unmodifiableList(new ArrayList<>(effects));
    }

    public List<BufferedImage> process(List<BufferedImage> originals) {
        List<BufferedImage> result = new ArrayList<>();
        if (originals == null) return result;
        for (int i = 0; i < originals.size(); i++) {
            PhotoEffect effect = applyToAll ? allEffect : getEffect(i);
            result.add(PhotoEffectProcessor.apply(originals.get(i), effect));
        }
        return result;
    }

    public static PhotoEffectSession from(PhotoSession session) {
        return new PhotoEffectSession(session == null ? 1 : session.getSlotCount());
    }
}
