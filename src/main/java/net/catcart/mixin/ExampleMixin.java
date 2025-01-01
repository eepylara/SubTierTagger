package net.catcart.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.catcart.SubtiersTagger;
import net.catcart.config.SubtierConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerEntity.class)
public class ExampleMixin {


	@ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
	public Text prependTier(Text original) {
		if (SubtierConfig.getEnabled() == true){
			PlayerEntity self = (PlayerEntity) (Object) this;

			// Check if the display name is already modified to avoid appending twice
			Text cachedName = SubtiersTagger.getDisplayNameCache().get(self.getUuid());
			if (cachedName != null) {
				return cachedName; // Return cached name to avoid appending again
			}

			// If not cached, apply the tier and icon logic
			return SubtiersTagger.appendTier(self, original);
		} else {
			return original;
		}
	}
}