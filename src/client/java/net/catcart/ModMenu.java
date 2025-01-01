package net.catcart;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.catcart.config.GameMode;
import net.catcart.config.SubtierConfig;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.Arrays;
import java.util.Optional;

public class ModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> YetAnotherConfigLib.createBuilder()
                .title(Text.literal("SubTierTagger"))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("SubTierTagger Config"))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Config"))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Enable"))
                                        .binding(true, () -> SubtierConfig.getEnabled(), newVal -> SubtierConfig.setEnabled(newVal))
                                        .controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
                                        .build())
                                .option(Option.<GameMode>createBuilder()
                                        .name(Text.literal("Gamemode"))
                                        .binding(GameMode.MINECART, () -> SubtierConfig.getCurrentGameMode(), newVal -> SubtierConfig.setCurrentGameMode(newVal))
                                        .controller(opt -> EnumControllerBuilder.create(opt)
                                                .enumClass(GameMode.class)
                                                .formatValue(GameMode::formatted))
                                        .build())
                                .build())
                        .build())
                .build()
                .generateScreen(parent);
    }





}
