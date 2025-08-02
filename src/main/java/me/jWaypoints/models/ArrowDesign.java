package me.jWaypoints.models;

import org.bukkit.Material;

public class ArrowDesign {
    private final String name;
    private final Material mainMaterial;
    private final Material headMaterial;
    private final Material tailMaterial;
    private final boolean glowing;
    private final String particleEffect;
    private final int patternType;

    public ArrowDesign(String name, Material mainMaterial, Material headMaterial,
                       Material tailMaterial, boolean glowing, String particleEffect, int patternType) {
        this.name = name;
        this.mainMaterial = mainMaterial;
        this.headMaterial = headMaterial;
        this.tailMaterial = tailMaterial;
        this.glowing = glowing;
        this.particleEffect = particleEffect;
        this.patternType = patternType;
    }

    public String getName() { return name; }
    public Material getMainMaterial() { return mainMaterial; }
    public Material getHeadMaterial() { return headMaterial; }
    public Material getTailMaterial() { return tailMaterial; }
    public boolean isGlowing() { return glowing; }
    public String getParticleEffect() { return particleEffect; }
    public int getPatternType() { return patternType; }
}