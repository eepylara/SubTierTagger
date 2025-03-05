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
    BOW(0, "Bow", "bow", "\uE831", TextColor.fromRgb(0x964B00)),
    BED(1, "Bed", "bed", "\uE837", TextColor.fromRgb(0xFFB6C1)),
    MINECART(2, "Minecart", "minecart", "\uE830", TextColor.fromRgb(0xFF0000)),
    SPEED(3, "Speed", "speed", "\uE839", TextColor.fromRgb(0xFFE4B5)),
    CREEPER(4, "Creeper", "creeper", "\uE838", TextColor.fromRgb(0x008000)),
    DIA_SMP(5, "Diamond SMP", "dia_smp", "\uE832", TextColor.fromRgb(0x90D5FF)),
    IRON_POT(6, "Iron Pot", "iron_pot", "\uE835", TextColor.fromRgb(0xc0c0c0)),
    OG_VANILLA(7, "OG Vanilla", "og_vanilla", "\uE834", TextColor.fromRgb(0xD4AF37)),
    MANHUNT(8, "Manhunt", "manhunt", "\uE833", TextColor.fromRgb(0x90EE90)),
    DIA_VANILLA(9, "Diamond Vanilla", "dia_crystal", "\uE836", TextColor.fromRgb(0xA9A9A9)),
    ELYTRA(10, "Elytra", "elytra", "\uE840", TextColor.fromRgb(0x0078FF)),
    TRIDENT(11, "Trident", "trident", "\uE841", TextColor.fromRgb(0x42957E));

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

    public MutableText configFormatted() {
        Text gamemode = Text.literal(this.translationKey).styled(style -> style.withColor(this.iconColor));

        return Text.literal(this.icon).append(gamemode);
    }

    public GameMode next() {
        GameMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static Optional<GameMode> byKey(String key) {
        return Arrays.stream(values()).filter(m -> m.apiKey.equalsIgnoreCase(key)).findFirst();
    }
}
