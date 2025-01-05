package net.catcart;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import net.catcart.config.GameMode;
import net.catcart.config.SubtierConfig;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.SharedConstants;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.fabricmc.fabric.impl.resource.loader.ModResourcePackUtil.GSON;

public class SubtiersTagger implements ModInitializer {
	public static final String MOD_ID = "subtierstagger";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private static final String UPDATE_URL = "https://api.modrinth.com/v2/project/subtiertagger/version?game_versions=%s";

	private static final ConcurrentHashMap<UUID, CopyOnWriteArrayList<Pair<GameMode, CachedTier>>> allPlayerTiers = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, Text> displayNameCache = new ConcurrentHashMap<>();
	private static final CopyOnWriteArraySet<UUID> ongoingFetches = new CopyOnWriteArraySet<>();
	private static final String NO_TIER = "NoTier";

	@Getter
	private static final HttpClient client = HttpClient.newHttpClient();

	@Getter
	private static Version latestVersion = null;
	private static final AtomicBoolean isObsolete = new AtomicBoolean(false);

	@Override
	public void onInitialize() {
		SubtierConfig.HANDLER.load();

		LOGGER.info("SubTierTagger initialized, and subtier commands registered.");
	}

	public static Text appendTier(PlayerEntity player, Text text) {
		UUID uuid = player.getUuid();
		GameMode activeMode = SubtierConfig.getCurrentGameMode();
		GameMode lastUsedMode = SubtierConfig.getLastUsedGameMode();

		if (lastUsedMode != activeMode){
			SubtierConfig.setLastUsedGameMode(activeMode);
			displayNameCache.clear();
		}

		if (SubtierConfig.getEnabled() == false){
			SubtierConfig.setLastUsedGameMode(activeMode);
			clearAllCaches();
			return text;
		}

		if (!allPlayerTiers.containsKey(uuid)) {
			fetchTierAsync(player);
			SubtierConfig.setLastUsedGameMode(activeMode);
			return text; // Return original text while data is being fetched
		}

		int index = activeMode.ordinal();
		CopyOnWriteArrayList<Pair<GameMode, CachedTier>> tiers = allPlayerTiers.get(uuid);
		if (tiers == null || tiers.size() <= index) {
			LOGGER.warn("Tier data not initialized correctly for player: {}", player.getName().getString());
			SubtierConfig.setLastUsedGameMode(activeMode);
			return text;
		}

		CachedTier cachedTier = tiers.get(index).getRight();
		if (cachedTier == null || cachedTier.isExpired()) {
			SubtierConfig.setLastUsedGameMode(activeMode);
			return text; // Skip if no valid tier data
		}

		if (cachedTier != null && !cachedTier.isExpired()) {
			if (cachedTier == null || NO_TIER.equals(cachedTier.getTier())) {
				CachedTier highestTier = null;
				GameMode highestGameMode = null;
				for (Pair<GameMode, CachedTier> tierPair : tiers) {
					CachedTier currentTier = tierPair.getRight();
					if (currentTier != null && !NO_TIER.equals(currentTier.getTier())) {
						if (highestTier == null || compareTiers(currentTier.getTier(), highestTier.getTier()) > 0) {
							highestTier = currentTier;
							highestGameMode = tierPair.getLeft();
						}
					}
				}

				if (highestTier != null) {
					MutableText mutableText = Text.literal(text.getString());
					int tierColor = SubtierConfig.getColor(highestTier.getTier());
					Text formattedTier = Text.literal(highestTier.getTier()).styled(style -> style.withColor(TextColor.fromRgb(tierColor)));

					// Append the tier
					mutableText.append(" | ").formatted(Formatting.GRAY);
					mutableText.append(formattedTier);

					String icon = highestGameMode.getIcon();
					Text gamemodeText = Text.literal(icon);

					mutableText.append(gamemodeText);

					SubtierConfig.setLastUsedGameMode(activeMode);
					displayNameCache.put(uuid, mutableText);
					return mutableText;
				} else {
					return text;
				}
			}

			if (cachedTier.isExpired()) {
				SubtierConfig.setLastUsedGameMode(activeMode);
				return text;
			}

			if (displayNameCache.containsKey(uuid)) {
				SubtierConfig.setLastUsedGameMode(activeMode);
				return displayNameCache.get(uuid);
			}

			MutableText mutableText = Text.literal(text.getString());
			int tierColor = SubtierConfig.getColor(cachedTier.getTier());
			Text formattedTier = Text.literal(cachedTier.getTier()).styled(style -> style.withColor(TextColor.fromRgb(tierColor)));

			mutableText.append(" | ").formatted(Formatting.GRAY);
			mutableText.append(formattedTier);

			String icon = activeMode.getIcon();
			Text gamemodeText = Text.literal(icon);

			mutableText.append(gamemodeText);

			SubtierConfig.setLastUsedGameMode(activeMode);
			displayNameCache.put(uuid, mutableText);
			return mutableText;
		}

		fetchTierAsync(player);
		return text;
	}

	private static int compareTiers(String tier1, String tier2) {
		List<String> tierOrder = Arrays.asList("LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "RLT2", "LT2", "RHT2", "RHT1", "LT1", "RTLT1", "RLT1", "HT1");
		return Integer.compare(tierOrder.indexOf(tier1), tierOrder.indexOf(tier2));
	}


	public static void clearAllCaches() {
		allPlayerTiers.clear();
		displayNameCache.clear();
		LOGGER.info("Cleared all caches.");
	}


	public static void fetchTierAsync(PlayerEntity player) {
		UUID uuid = player.getUuid();

		if (!ongoingFetches.add(uuid)) {
			return;
		}

		new Thread(() -> {
			int retryAttempts = 0;
			boolean success = false;

			while (retryAttempts < 3 && !success) {
				try {
					String formattedUuid = uuid.toString().replace("-", "");
					URL url = new URL("https://subtiers.net/api/rankings/" + formattedUuid);
					HttpURLConnection connection = (HttpURLConnection) url.openConnection();
					connection.setRequestMethod("GET");
					connection.setConnectTimeout(5000);
					connection.setReadTimeout(5000);

					int responseCode = connection.getResponseCode();

					if (responseCode == 404 || responseCode == 422) {
						LOGGER.warn("No valid tier data found for player: {}", player.getName().getString());
						for (GameMode gameMode : GameMode.values()) {
							CopyOnWriteArrayList<Pair<GameMode, CachedTier>> tierList = allPlayerTiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());
							tierList.add(new Pair<>(gameMode, new CachedTier(NO_TIER)));
						}
						success = true;
					} else if (responseCode == 200) {
						InputStreamReader reader = new InputStreamReader(connection.getInputStream());
						JsonObject jsonResponse = JsonParser.parseReader(reader).getAsJsonObject();

						for (GameMode gameMode : GameMode.values()) {
							JsonObject gamemodeData = jsonResponse.getAsJsonObject(gameMode.getApiKey());
							CopyOnWriteArrayList<Pair<GameMode, CachedTier>> tierList = allPlayerTiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());

							if (gamemodeData != null && gamemodeData.has("tier") && gamemodeData.has("pos")) {
								int tier = gamemodeData.get("tier").getAsInt();
								int pos = gamemodeData.get("pos").getAsInt();
								String position = (pos == 0) ? "HT" : "LT";
								String retired = gamemodeData.has("retired") && gamemodeData.get("retired").getAsBoolean() ? "R" : "";
								String formattedTier = retired + position + tier;

								tierList.add(new Pair<>(gameMode, new CachedTier(formattedTier)));
								LOGGER.info("Fetched tier for player {}: {}", player.getName().getString(), formattedTier);
							} else {
								tierList.add(new Pair<>(gameMode, new CachedTier(NO_TIER)));
								LOGGER.warn("Incomplete or invalid tier data for player: {}", player.getName().getString());
							}
						}
						success = true;
					} else {
						LOGGER.error("Unexpected response code {} while fetching tier for player: {}", responseCode, player.getName().getString());
					}
				} catch (SocketTimeoutException e) {
					LOGGER.warn("Timeout while fetching tier for player: {}", player.getName().getString());
				} catch (Exception e) {
					LOGGER.error("Failed to fetch tier for player {}", player.getName().getString(), e);
				}

				if (!success) {
					retryAttempts++;
					try {
						Thread.sleep(1000 * (long) Math.pow(2, retryAttempts));
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			}

			if (!success) {
				LOGGER.error("Failed to fetch tier for player {} after {} attempts.", player.getName().getString(), retryAttempts);
				for (GameMode gameMode : GameMode.values()) {
					CopyOnWriteArrayList<Pair<GameMode, CachedTier>> tierList = allPlayerTiers.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());
					tierList.add(new Pair<>(gameMode, new CachedTier(NO_TIER)));
				}
			}

			ongoingFetches.remove(uuid);
		}).start();
	}



	public static class CachedTier {
		private final String tier;
		private final long timestamp;
		private static final long EXPIRATION_TIME = 300000;
		private static final String NO_TIER = "NoTier";

		public CachedTier(String tier) {
			this.tier = tier;
			this.timestamp = System.currentTimeMillis();
		}

		public String getTier() {
			return tier;
		}

		public boolean isExpired() {
			return System.currentTimeMillis() - timestamp > EXPIRATION_TIME;
		}

		public boolean isNoTier() {
			return NO_TIER.equals(tier);
		}

		public static CachedTier noTier() {
			return new CachedTier(NO_TIER);
		}
	}

	public static ConcurrentHashMap<UUID, Text> getDisplayNameCache() {
		return displayNameCache;
	}
}