package net.catcart.config;

import com.google.gson.internal.LinkedTreeMap;
import dev.isxander.yacl3.config.v2.api.ConfigClassHandler;
import dev.isxander.yacl3.config.v2.api.SerialEntry;
import dev.isxander.yacl3.config.v2.api.autogen.AutoGen;
import dev.isxander.yacl3.config.v2.api.autogen.StringField;
import dev.isxander.yacl3.config.v2.api.autogen.TickBox;
import dev.isxander.yacl3.config.v2.api.serializer.GsonConfigSerializerBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SubtierConfig implements Serializable {

    public static ConfigClassHandler<SubtierConfig> HANDLER = ConfigClassHandler.createBuilder(SubtierConfig.class)
            .id(Identifier.of("subtierstagger", "config"))
            .serializer(config -> GsonConfigSerializerBuilder.create(config)
                    .setPath(FabricLoader.getInstance().getConfigDir().resolve("subtiertagger.json5"))
                    .setJson5(true)
                    .build())
            .build();

    @SerialEntry
    private static GameMode currentGameMode = GameMode.MINECART;

    @SerialEntry
    private static Boolean enabled = true;

    @SerialEntry
    private static GameMode lastUsedGameMode = GameMode.MINECART;

    public static GameMode getLastUsedGameMode() {
        return lastUsedGameMode;
    }

    public static void setLastUsedGameMode(GameMode gameMode) {
        lastUsedGameMode = gameMode;
    }

    private static LinkedTreeMap<String, Integer> tierColors = defaultColorsTiers();

    public static GameMode getCurrentGameMode() {
        return currentGameMode;
    }

    public static Boolean getEnabled(){return enabled;}

    public static void setEnabled(Boolean val){
        enabled = val;
    }

    public static void setCurrentGameMode(GameMode gameMode) {
        currentGameMode = gameMode;
    }

    public static int getColor(String tier) {
        return tierColors.getOrDefault(tier, 0xFFFFFF);
    }


    private static LinkedTreeMap<String, Integer> defaultColorsTiers() {
        LinkedTreeMap<String, Integer> colors = new LinkedTreeMap<>();
        colors.put("HT1", 0xFF0000);
        colors.put("LT1", 0xFFB6C1);
        colors.put("HT2", 0xFFA500);
        colors.put("LT2", 0xFFE4B5);
        colors.put("HT3", 0xDAA520);
        colors.put("LT3", 0xEEE8AA);
        colors.put("HT4", 0x006400);
        colors.put("LT4", 0x90EE90);
        colors.put("HT5", 0x808080);
        colors.put("LT5", 0xD3D3D3);
        colors.put("RHT1", 0xFF0000);
        colors.put("RLT1", 0xFFB6C1);
        colors.put("RHT2", 0xFFA500);
        colors.put("RLT2", 0xFFE4B5);
        return colors;
    }

}
