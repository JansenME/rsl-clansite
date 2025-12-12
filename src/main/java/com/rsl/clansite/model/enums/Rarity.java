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
        for(Rarity rarity : Rarity.values()) {
            if(rarity.name.equalsIgnoreCase(name)) {
                return rarity;
            }
        }

        return null;
    }
}
