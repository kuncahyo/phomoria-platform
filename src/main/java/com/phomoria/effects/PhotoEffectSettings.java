package com.phomoria.effects;

import java.util.EnumSet;
import java.util.Set;

public final class PhotoEffectSettings {
    private boolean enabled = false;
    private Set<PhotoEffect> enabledEffects = EnumSet.of(
            PhotoEffect.NORMAL, PhotoEffect.SEPIA, PhotoEffect.NEGATIVE,
            PhotoEffect.GRAYSCALE, PhotoEffect.WARM, PhotoEffect.COOL
    );

    public PhotoEffectSettings() {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Set<PhotoEffect> getEnabledEffects() {
        if (enabledEffects == null || enabledEffects.isEmpty()) {
            return EnumSet.of(PhotoEffect.NORMAL);
        }
        return EnumSet.copyOf(enabledEffects);
    }

    public void setEnabledEffects(Set<PhotoEffect> effects) {
        if (effects == null || effects.isEmpty()) {
            enabledEffects = EnumSet.of(PhotoEffect.NORMAL);
        } else {
            enabledEffects = EnumSet.copyOf(effects);
            enabledEffects.add(PhotoEffect.NORMAL);
        }
    }

    public boolean isEffectAllowed(PhotoEffect effect) {
        return effect != null && getEnabledEffects().contains(effect);
    }
}
