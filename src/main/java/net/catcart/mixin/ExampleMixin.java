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
		PlayerEntity self = (PlayerEntity) (Object) this;

		Text cachedName = SubtiersTagger.getDisplayNameCache().get(self.getUuid());
		if (cachedName != null) {
			return cachedName;
		}

		return SubtiersTagger.appendTier(self, original);

	}
}