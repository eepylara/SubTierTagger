package net.catcart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.catcart.config.GameMode;
import net.catcart.config.SubtierConfig;
import net.fabricmc.api.ModInitializer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class SubtiersTagger implements ModInitializer {
	public static final String MOD_ID = "subtierstagger";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Cache for tier information
	private static final ConcurrentHashMap<UUID, CachedTier> playerTiers = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, ArrayList<Pair<GameMode, CachedTier>>> allPlayerTiers = new ConcurrentHashMap<>();
	private static final ConcurrentHashMap<UUID, Text> displayNameCache = new ConcurrentHashMap<>();
	private static final CopyOnWriteArraySet<UUID> ongoingFetches = new CopyOnWriteArraySet<>();
	private static final long DISPLAY_NAME_CACHE_EXPIRATION = 1000; // 1 second
	private static final long MIN_FETCH_INTERVAL = 60000; // 1 minute
	private static final String NO_TIER = "NoTier";

	@Override
	public void onInitialize() {
		SubtierConfig.HANDLER.load();


		LOGGER.info("CartTierTagger initialized, and subtier commands registered.");
	}

	public static Text appendTier(PlayerEntity player, Text text) {
		UUID uuid = player.getUuid();
		GameMode activeMode = SubtierConfig.getCurrentGameMode();
		GameMode lastUsedMode = SubtierConfig.getLastUsedGameMode();

		if (lastUsedMode != activeMode){
			displayNameCache.clear();
		}


		if (!allPlayerTiers.containsKey(uuid)) {
			fetchTierAsync(player);
			return text; // Return original text while data is being fetched
		}

		int index = activeMode.ordinal();
		ArrayList<Pair<GameMode, CachedTier>> tiers = allPlayerTiers.get(uuid);
		if (tiers == null || tiers.size() <= index) {
			LOGGER.warn("Tier data not initialized correctly for player: {}", player.getName().getString());
			return text;
		}

		CachedTier cachedTier = tiers.get(index).getRight();
		if (cachedTier == null || cachedTier.isExpired()) {
			return text; // Skip if no valid tier data
		}

		if (cachedTier != null && !cachedTier.isExpired()) {
			if (cachedTier == null || NO_TIER.equals(cachedTier.getTier())) {
				// Find the highest tier from all game modes
				CachedTier highestTier = null;
				GameMode highestGameMode = null;
				for (Pair<GameMode, CachedTier> tierPair : tiers) {
					CachedTier currentTier = tierPair.getRight();
					if (currentTier != null && !NO_TIER.equals(currentTier.getTier())) {
						if (highestTier == null || compareTiers(currentTier.getTier(), highestTier.getTier()) > 0) {
							highestTier = currentTier;
							highestGameMode = tierPair.getLeft();  // Keep track of the corresponding game mode
						}
					}
				}

				// If a highest tier is found, set it for the active game mode
				if (highestTier != null) {
					MutableText mutableText = Text.literal(text.getString());
					int tierColor = SubtierConfig.getColor(highestTier.getTier()); // Get color based on tier
					Text formattedTier = Text.literal(highestTier.getTier()).styled(style -> style.withColor(TextColor.fromRgb(tierColor)));

					// Append the tier
					mutableText.append(" | ").formatted(Formatting.GRAY);
					mutableText.append(formattedTier);

					// Append GameMode icon if a tier exists
					String icon = highestGameMode.getIcon();
					TextColor color = highestGameMode.getIconColor();
					Text gamemodeText = Text.literal(icon).styled(style -> style.withColor(color));

					// Append the GameMode icon behind the tier
					mutableText.append(" ").append(gamemodeText);

					displayNameCache.put(uuid, mutableText);
					return mutableText;
				} else {
					return text; // Skip if no valid tier found
				}
			}

			if (cachedTier.isExpired()) {
				return text; // Skip if expired tier data
			}

			// Check if the text is already cached in displayNameCache
			if (displayNameCache.containsKey(uuid)) {
				return displayNameCache.get(uuid); // Return cached version
			}

			// Convert original text to MutableText for appending
			MutableText mutableText = Text.literal(text.getString());
			int tierColor = SubtierConfig.getColor(cachedTier.getTier()); // Get color based on tier
			Text formattedTier = Text.literal(cachedTier.getTier()).styled(style -> style.withColor(TextColor.fromRgb(tierColor)));

			// Append the tier
			mutableText.append(" | ").formatted(Formatting.GRAY);
			mutableText.append(formattedTier);

			// Append GameMode icon if a tier exists
			String icon = activeMode.getIcon();
			TextColor color = activeMode.getIconColor();
			Text gamemodeText = Text.literal(icon).styled(style -> style.withColor(color));

			// Append the GameMode icon behind the tier
			mutableText.append(" ").append(gamemodeText);

			// Cache the result and return
			SubtierConfig.setLastUsedGameMode(activeMode);
			displayNameCache.put(uuid, mutableText);
			return mutableText;
		}

		// Trigger a fetch for the player's tier
		fetchTierAsync(player);
		return text; // Return original text while fetching
	}



	private static int compareTiers(String tier1, String tier2) {
		List<String> tierOrder = Arrays.asList("LT5", "HT5", "LT4", "HT4", "LT3", "HT3", "RLT2", "LT2", "RHT2", "RHT1", "LT1", "RTLT1", "RLT1", "HT1");
		return Integer.compare(tierOrder.indexOf(tier1), tierOrder.indexOf(tier2));
	}


	public static void clearAllCaches() {
		playerTiers.clear(); // Clear tier cache
		displayNameCache.clear(); // Clear display name cache
		LOGGER.info("Cleared all caches on server disconnect.");
	}


	public static void fetchTierAsync(PlayerEntity player) {
		UUID uuid = player.getUuid();

		if (!ongoingFetches.add(uuid)) {
			return; // Already fetching
		}

		new Thread(() -> {
			int retryAttempts = 0;
			boolean success = false;

			while (retryAttempts < 3 && !success) { // Retry up to 3 times
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
						// Populate allPlayerTiers with default NO_TIER for all game modes
						for (GameMode gameMode : GameMode.values()) {
							ArrayList<Pair<GameMode, CachedTier>> tierList = allPlayerTiers.computeIfAbsent(uuid, k -> new ArrayList<>());
							tierList.add(new Pair<>(gameMode, new CachedTier(NO_TIER)));
						}
						success = true; // No need to retry
					} else if (responseCode == 200) {
						InputStreamReader reader = new InputStreamReader(connection.getInputStream());
						JsonObject jsonResponse = JsonParser.parseReader(reader).getAsJsonObject();

						// Populate tiers for each game mode
						for (GameMode gameMode : GameMode.values()) {
							JsonObject gamemodeData = jsonResponse.getAsJsonObject(gameMode.getApiKey());
							ArrayList<Pair<GameMode, CachedTier>> tierList = allPlayerTiers.computeIfAbsent(uuid, k -> new ArrayList<>());

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
						success = true; // Successfully fetched
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
						// Exponential backoff before retrying
						Thread.sleep(1000 * (long) Math.pow(2, retryAttempts)); // Increase delay after each retry
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt(); // Handle thread interruption
					}
				}
			}

			if (!success) {
				LOGGER.error("Failed to fetch tier for player {} after {} attempts.", player.getName().getString(), retryAttempts);
				// Populate allPlayerTiers with default NO_TIER for all game modes as fallback
				for (GameMode gameMode : GameMode.values()) {
					ArrayList<Pair<GameMode, CachedTier>> tierList = allPlayerTiers.computeIfAbsent(uuid, k -> new ArrayList<>());
					tierList.add(new Pair<>(gameMode, new CachedTier(NO_TIER)));
				}
			}

			ongoingFetches.remove(uuid); // Ensure ongoing fetch is cleared
		}).start();
	}



	public static class CachedTier {
		private final String tier;
		private final long timestamp;
		private static final long EXPIRATION_TIME = 300000; // 5 minutes
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