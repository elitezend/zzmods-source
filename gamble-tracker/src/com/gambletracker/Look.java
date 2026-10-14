package com.gambletracker;

public enum Look {
    ROUNDED("Rounded"),
    CLASSIC("Classic"),
    GLASS("Glass"),
    LIQUID("Liquid Glass"),
    NEON("Neon"),
    MINIMAL("Minimal"),
    SLEEK("Sleek");

    public final String label;

    Look(String label) {
        this.label = label;
    }

    public Look next() {
        Look n = values()[(ordinal() + 1) % values().length];
        return n == SLEEK && !Edition.LOOKS ? n.next() : n;
    }

    public static Look parse(String name, Look fallback) {
        if (name == null) {
            return fallback;
        }
        for (Look look : values()) {
            if (look.name().equalsIgnoreCase(name)) {
                return look == SLEEK && !Edition.LOOKS ? fallback : look;
            }
        }
        return fallback;
    }
}
