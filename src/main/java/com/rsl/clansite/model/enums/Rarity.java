package com.rsl.clansite.model.enums;

public enum Rarity {
    COMMON("Common"),
    UNCOMMON("Uncommon"),
    RARE("Rare"),
    EPIC("Epic"),
    LEGENDARY("Legendary"),
    MYTHICAL("Mythical");

    private final String name;

    Rarity(final String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static Rarity getRarityByName(final String name) {
        return switch (name) {
            case "Common" -> Rarity.COMMON;
            case "Uncommon" -> Rarity.UNCOMMON;
            case "Rare" -> Rarity.RARE;
            case "Epic" -> Rarity.EPIC;
            case "Legendary" -> Rarity.LEGENDARY;
            case "Mythical" -> Rarity.MYTHICAL;
            default -> null;
        };
    }
}
