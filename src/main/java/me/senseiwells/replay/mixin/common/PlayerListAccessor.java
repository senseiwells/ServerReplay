package me.senseiwells.replay.mixin.common;

import net.minecraft.core.RegistryAccess;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerList.class)
public interface PlayerListAccessor {
	@Accessor("synchronizedRegistries")
	RegistryAccess.Frozen getFrozenRegistries();
}
