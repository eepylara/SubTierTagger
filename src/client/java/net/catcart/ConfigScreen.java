package net.catcart;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.catcart.config.SubtierConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;

    protected ConfigScreen(Screen parent) {
        super(Text.literal("Example Mod Config Screen"));

        this.parent = parent;

        this.client.setScreen(YetAnotherConfigLib.createBuilder()
                .title(Text.literal("Used for narration. Could be used to render a title in the future."))
                .category(ConfigCategory.createBuilder()
                        .name(Text.literal("Name of the category"))
                        .tooltip(Text.literal("This text will appear as a tooltip when you hover or focus the button with Tab. There is no need to add \n to wrap as YACL will do it for you."))
                        .group(OptionGroup.createBuilder()
                                .name(Text.literal("Name of the group"))
                                .description(OptionDescription.of(Text.literal("This text will appear when you hover over the name or focus on the collapse button with Tab.")))
                                .option(Option.<Boolean>createBuilder()
                                        .name(Text.literal("Boolean Option"))
                                        .description(OptionDescription.of(Text.literal("This text will appear as a tooltip when you hover over the option.")))
                                        .binding(true, () -> SubtierConfig.getEnabled(), newVal -> SubtierConfig.setEnabled(newVal))
                                        .controller(TickBoxControllerBuilder::create)
                                        .build())
                                .build())
                        .build())
                .build()
                .generateScreen(parent));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    // Implement your own rendering etc. in here!
}
