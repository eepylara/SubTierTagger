package net.catcart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.catcart.config.GameMode;
import net.catcart.config.SubtierConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.command.CommandSource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;

public class SubtiersTaggerClient implements ClientModInitializer {
	public static final String[] gamemodes = new String[]{"minecart", "bed", "mace", "og_vanilla", "speed", "dia_smp", "iron_pot", "creeper", "bow", "manhunt"};

	private static KeyBinding myKeyBinding;

	private static Boolean isenabled = SubtierConfig.getEnabled();

	@Override
	public void onInitializeClient() {




		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
			dispatcher.register(
					com.mojang.brigadier.builder.LiteralArgumentBuilder
							.<FabricClientCommandSource>literal("subtiertagger")
							.then(
									com.mojang.brigadier.builder.RequiredArgumentBuilder
											.<FabricClientCommandSource, String>argument("username", StringArgumentType.word())
											.executes(ctx -> {
												String username = StringArgumentType.getString(ctx, "username");
												fetchAndDisplayTiers(username);
												return 0;
											})
							)
			);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
			dispatcher.register(
					com.mojang.brigadier.builder.LiteralArgumentBuilder
							.<FabricClientCommandSource>literal("stt")
							.then(
									com.mojang.brigadier.builder.RequiredArgumentBuilder
											.<FabricClientCommandSource, String>argument("username", StringArgumentType.word())
											.executes(ctx -> {
												String username = StringArgumentType.getString(ctx, "username");

												fetchAndDisplayTiers(username);

												return 0;
											})
							)
			);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registry) -> {
			dispatcher.register(
					com.mojang.brigadier.builder.LiteralArgumentBuilder
							.<FabricClientCommandSource>literal("stttoggle")
							.executes(context -> {
								if (SubtierConfig.getEnabled() == false){
									SubtierConfig.setEnabled(true);
								} else if (SubtierConfig.getEnabled() == true){
									SubtierConfig.setEnabled(false);
								}
								return 0;
							})
			);
		});

		myKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.subtiertagger.open_config",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				"SubTierTagger"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (myKeyBinding.wasPressed()) {
				if (client.player != null) {
					MinecraftClient.getInstance().execute(() -> {
						MinecraftClient.getInstance().setScreen(
								YetAnotherConfigLib.createBuilder()
										.title(Text.literal("SubTierTagger"))
										.category(ConfigCategory.createBuilder()
												.name(Text.literal("SubTierTagger Config"))
												.group(OptionGroup.createBuilder()
														.name(Text.literal("Config"))
														.option(Option.<Boolean>createBuilder()
																.name(Text.literal("Enable"))
																.binding(true, () -> SubtierConfig.getEnabled(), SubtierConfig::setEnabled)
																.controller(opt -> BooleanControllerBuilder.create(opt).coloured(true))
																.build())
														.option(Option.<GameMode>createBuilder()
																.name(Text.literal("Gamemode"))
																.binding(GameMode.MINECART, SubtierConfig::getCurrentGameMode, SubtierConfig::setCurrentGameMode)
																.controller(opt -> EnumControllerBuilder.create(opt)
																		.enumClass(GameMode.class)
																		.formatValue(GameMode::configFormatted))
																.build())
														.build())
												.build())
										.build()
										.generateScreen(MinecraftClient.getInstance().currentScreen)
						);
					});
				}
			}
		});


	}

	private void fetchAndDisplayTiers(String username) {
		new Thread(() -> {
			try {
				String uuid = getUuidFromUsername(username).get();
				String formattedUuid = uuid.toString().replace("-", "");
				URL url = new URL("https://subtiers.net/api/rankings/" + formattedUuid);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(5000);
				connection.setReadTimeout(5000);

				int responseCode = connection.getResponseCode();
				if (responseCode == 200) {
					JsonObject jsonResponse = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject();

					MinecraftClient client = MinecraftClient.getInstance();
					Text tiers = Text.literal("Tiers for " + username + ":").formatted(Formatting.BOLD);
					MutableText resultMessage = Text.literal("");
					resultMessage.append(tiers);

					for (GameMode gameMode : GameMode.values()) {
						String apiKey = gameMode.getApiKey();
						if (jsonResponse.has(apiKey)) {
							JsonObject gamemodeData = jsonResponse.getAsJsonObject(apiKey);
							int tier = gamemodeData.get("tier").getAsInt();
							int pos = gamemodeData.get("pos").getAsInt();

							String positionText = (pos == 0) ? "HT" : "LT";

							String retired;

							if (gamemodeData.get("retired").getAsString() == "true"){
								retired = "R";
							} else {
								retired = "";
							}

							String tierText = retired + positionText + tier;

							int tiercolor = SubtierConfig.getColor(tierText);
							Text formattedTier = Text.literal(tierText).styled(style -> style.withColor(TextColor.fromRgb(tiercolor))).formatted(Formatting.BOLD);

							Text gamemode = Text.literal(gameMode.getTranslationKey()).formatted(Formatting.BOLD).styled(style -> style.withColor(gameMode.getIconColor()));

							MutableText gamemodeText = Text.literal(gameMode.getIcon() + " ").append(gamemode).append(" - ").append(formattedTier);

							resultMessage.append(Text.literal("\n").append(gamemodeText));
						}
					}

					client.execute(() -> client.player.sendMessage(resultMessage, false));
				} else {
					sendErrorMessage("Failed to fetch data for user: " + username);
				}
			} catch (Exception e) {
				sendErrorMessage("An error occurred while fetching data for user: " + username);
				e.printStackTrace();
			}
		}).start();
	}

	private void sendErrorMessage(String message) {
		MinecraftClient client = MinecraftClient.getInstance();
		client.execute(() -> client.player.sendMessage(Text.literal(message).formatted(Formatting.RED), false));
	}

	public Optional<String> getUuidFromUsername(String username) {
		try {
			URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + username);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);

			int responseCode = connection.getResponseCode();
			if (responseCode == 200) {
				JsonObject jsonResponse = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
				return Optional.of(jsonResponse.get("id").getAsString());
			} else if (responseCode == 204) {
				System.err.println("No UUID found for username: " + username);
			} else {
				System.err.println("Failed to fetch UUID for username: " + username + " (Response code: " + responseCode + ")");
			}
		} catch (Exception e) {
			System.err.println("Error occurred while fetching UUID for username: " + username);
			e.printStackTrace();
		}

		return Optional.empty();
	}







}


