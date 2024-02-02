package me.senseiwells.replay.mixin.compat.carpet;

import carpet.patches.EntityPlayerMPFake;
import com.mojang.authlib.GameProfile;
import me.senseiwells.replay.ServerReplay;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(EntityPlayerMPFake.class)
public class EntityPlayerMPFakeMixin extends ServerPlayer {
	public EntityPlayerMPFakeMixin(MinecraftServer minecraftServer, ServerLevel serverLevel, GameProfile gameProfile, ClientInformation clientInformation) {
		super(minecraftServer, serverLevel, gameProfile, clientInformation);
	}

	@Override
	public int requestedViewDistance() {
		if (ServerReplay.config.getFixCarpetBotViewDistance()) {
			return this.server.getPlayerList().getViewDistance();
		}
		return super.requestedViewDistance();
	}
}
