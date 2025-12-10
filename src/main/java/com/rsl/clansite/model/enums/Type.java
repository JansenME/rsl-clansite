package com.rsl.clansite.model.enums;

public enum Type {
    ATTACK("Attack"),
    SUPPORT("Support"),
    DEFENCE("Defence"),
    HP("HP");

    private final String name;

    Type(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Type getTypeByName(final String name) {
        return switch (name) {
            case "Attack" -> Type.ATTACK;
            case "Support" -> Type.SUPPORT;
            case "Defence" -> Type.DEFENCE;
            case "HP" -> Type.HP;
            default -> null;
        };
    }
}
