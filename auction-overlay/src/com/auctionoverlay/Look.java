package com.auctionoverlay;

// ROUNDED, CLASSIC and SLEEK are retired: they can't be picked any more and settings that still say them get
// PLAIN (shown as "Default"). They stay in the enum so the drawing code for them still compiles.
// The Free edition is always CLASSIC (2.1.0) - the paid editions don't have it.
public enum Look {
    PLAIN("Default"),
    GLASS("Glass"),
    LIQUID("Liquid Glass"),
    NEON("Neon"),
    MINIMAL("Minimal"),
    ROUNDED("Rounded"),
    CLASSIC("Classic"),
    SLEEK("Sleek");

    public final String label;

    Look(String label) {
        this.label = label;
    }

    public boolean retired() {
        return this == ROUNDED || this == SLEEK || (this == CLASSIC && Edition.LOOKS);
    }

    public Look next() {
        Look n = values()[(ordinal() + 1) % values().length];
        return n.retired() ? n.next() : n;
    }

    public static Look parse(String name, Look fallback) {
        if (name == null) {
            return fallback;
        }
        for (Look look : values()) {
            if (look.name().equalsIgnoreCase(name)) {
                return look.retired() ? PLAIN : look;
            }
        }
        return fallback;
    }
}
