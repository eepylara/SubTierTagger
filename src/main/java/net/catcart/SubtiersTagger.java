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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class SubtiersTagger implements ModInitializer {
	public static final String MOD_ID = "subtierstagger";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Cache for tier information
	private static final ConcurrentHashMap<UUID, CachedTier> playerTiers = new ConcurrentHashMap<>();
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

		// Check for cached tier
		CachedTier cachedTier = playerTiers.get(uuid);
		if (cachedTier != null && !cachedTier.isExpired()) {
			if (NO_TIER.equals(cachedTier.getTier())) {
				return text; // Skip if "NoTier" is cached
			}

			String tier = cachedTier.getTier();

			// Check if the text is already cached in displayNameCache
			if (displayNameCache.containsKey(uuid)) {
				return displayNameCache.get(uuid); // Return cached version
			}

			// Convert original text to MutableText for appending
			MutableText mutableText = Text.literal(text.getString());
			int tiercolor = SubtierConfig.getColor(tier); // Get color based on tier
			Text formattedTier = Text.literal(tier).styled(style -> style.withColor(TextColor.fromRgb(tiercolor)));

			// Append the tier
			mutableText.append(" | ").formatted(Formatting.GRAY);
			mutableText.append(formattedTier);

			// Append GameMode icon if a tier exists
			GameMode activeMode = SubtierConfig.getCurrentGameMode();
			String icon = activeMode.getIcon();
			TextColor color = activeMode.getIconColor();
			Text gamemodeText = Text.literal(icon).styled(style -> style.withColor(color));

			// Append the GameMode icon behind the tier
			mutableText.append(" ").append(gamemodeText);

			// Cache the result and return
			displayNameCache.put(uuid, mutableText);
			return mutableText;
		}

		// Trigger a fetch for the player's tier
		fetchTierAsync(player);
		return text; // Return original text while fetching
	}





	public static void clearAllCaches() {
		playerTiers.clear(); // Clear tier cache
		displayNameCache.clear(); // Clear display name cache
		LOGGER.info("Cleared all caches on server disconnect.");
	}


	public static void fetchTierAsync(PlayerEntity player) {
		UUID uuid = player.getUuid();

		// Avoid duplicate fetches or repeated fetches too soon
		if (!ongoingFetches.add(uuid)) {
			return; // Already fetching
		}

		// Check if we have a cached tier and if it is expired or marked as "NoTier"
		CachedTier cachedTier = playerTiers.get(uuid);
		if (cachedTier != null && !cachedTier.isExpired()) {
			if (NO_TIER.equals(cachedTier.getTier())) {
				return; // Avoid retrying if tier was already determined to be missing
			}
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

					if (responseCode == 404) {
						LOGGER.info("No tier data found for player: {}", player.getName().getString());
						playerTiers.put(uuid, new CachedTier(NO_TIER)); // Cache "NoTier"
						success = true; // No need to retry
					} else if (responseCode == 422) {
						LOGGER.warn("Server returned 422 for player {}: Invalid tier data or unavailable.", player.getName().getString());
						playerTiers.put(uuid, new CachedTier(NO_TIER)); // Cache "NoTier"
						success = true; // No need to retry
					} else if (responseCode == 200) {
						InputStreamReader reader = new InputStreamReader(connection.getInputStream());
						JsonObject jsonResponse = JsonParser.parseReader(reader).getAsJsonObject();
						JsonObject gamemode = jsonResponse.getAsJsonObject(SubtierConfig.getCurrentGameMode().getApiKey());

						if (gamemode != null && gamemode.has("tier") && gamemode.has("pos")) {
							int tier = gamemode.get("tier").getAsInt();
							int pos = gamemode.get("pos").getAsInt();
							String position = (pos == 0) ? "HT" : "LT";
							String retired;

							if (gamemode.get("retired").getAsString() == "true"){
								retired = "R";
							} else {
								retired = "";
							}

							String formattedTier = retired + position + tier;
							playerTiers.put(uuid, new CachedTier(formattedTier));

							LOGGER.info("Fetched tier for player {}: {}", player.getName().getString(), formattedTier);
							success = true; // Successfully fetched
						} else {
							LOGGER.warn("Incomplete or invalid tier data for player: {}", player.getName().getString());
							playerTiers.put(uuid, new CachedTier(NO_TIER)); // Cache as "NoTier"
							success = true; // No need to retry
						}
					} else {
						LOGGER.error("Unexpected response code {} while fetching tier for player: {}", responseCode, player.getName().getString());
					}
				} catch (java.net.SocketTimeoutException e) {
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
				playerTiers.put(uuid, new CachedTier(NO_TIER)); // Cache as "NoTier" after multiple failed attempts
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