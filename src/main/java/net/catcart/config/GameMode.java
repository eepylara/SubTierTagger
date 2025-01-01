package net.catcart.config;

import lombok.Getter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.TranslatableOption;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum GameMode implements TranslatableOption {
    BOW(0, "Bow", "bow", "🏹", TextColor.fromRgb(0x964B00)),
    BED(1, "Bed", "bed", "🛏", TextColor.fromRgb(0xFFB6C1)),
    MINECART(2, "Minecart", "minecart", "\uD83D\uDED2", TextColor.fromRgb(0xFF0000)),
    SPEED(3, "Speed", "speed", "⚡", TextColor.fromRgb(0xFFE4B5)),
    CREEPER(4, "Creeper", "creeper", "\ud83d\udc38", TextColor.fromRgb(0x008000)),
    DIA_SMP(5, "Dia SMP", "dia_smp", "💎", TextColor.fromRgb(0x90D5FF)),
    IRON_POT(6, "Iron Pot", "iron_pot", "\ud83d\udee1", TextColor.fromRgb(0xc0c0c0)),
    OG_VANILLA(7, "OG Vanilla", "og_vanilla", "\ud83c\udf4e", TextColor.fromRgb(0xD4AF37)),
    MANHUNT(8, "Manhunt", "manhunt", "\ud83e\udded", TextColor.fromRgb(0x90EE90)),
    MACE(9, "Mace", "mace", "\ud83d\udd28", TextColor.fromRgb(0xA9A9A9));

    private final int id;
    private final String translationKey;
    private final String apiKey;
    private final String icon;
    private final TextColor iconColor;

    GameMode(int id, String translationKey, String apiKey, String icon, TextColor iconColor) {
        this.id = id;
        this.translationKey = translationKey;
        this.apiKey = apiKey;
        this.icon = icon;
        this.iconColor = iconColor;
    }

    public int getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getIcon() {
        return icon;
    }

    public TextColor getIconColor() {
        return iconColor;
    }

    public MutableText formatted() {
        return Text.literal(this.icon + " " + this.translationKey).styled(s -> s.withColor(this.iconColor));
    }

    public GameMode next() {
        GameMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static Optional<GameMode> byKey(String key) {
        return Arrays.stream(values()).filter(m -> m.apiKey.equalsIgnoreCase(key)).findFirst();
    }
}
