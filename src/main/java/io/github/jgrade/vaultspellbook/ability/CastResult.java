package io.github.jgrade.vaultspellbook.ability;

// Outcome of casting one ability glyph. Every value except SUCCESS shows a player-facing message.
public enum CastResult {
    SUCCESS(null),
    STARTED("vaultspellbook.cast.started"),
    STOPPED("vaultspellbook.cast.stopped"),
    NOT_LEARNED("vaultspellbook.cast.not_learned"),
    ON_COOLDOWN("vaultspellbook.cast.on_cooldown"),
    BLOCKED("vaultspellbook.cast.blocked"),
    FAILED("vaultspellbook.cast.failed"),
    UNSUPPORTED("vaultspellbook.cast.unsupported"),
    DISABLED("vaultspellbook.cast.disabled");

    private final String messageKey;

    CastResult(String messageKey) {
        this.messageKey = messageKey;
    }

    // Translation key taking the glyph name as its only argument; null for SUCCESS.
    public String getMessageKey() {
        return messageKey;
    }
}
