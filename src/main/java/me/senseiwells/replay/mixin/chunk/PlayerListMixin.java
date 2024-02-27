package me.senseiwells.replay.mixin.chunk;

import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundChatPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.Function;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Inject(
		method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;)V",
		at = @At("HEAD")
	)
	private void onBroadcastAll(Packet<?> packet, CallbackInfo ci) {
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			recorder.record(packet);
		}
	}

	@Inject(
		method = "broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V",
		at = @At("HEAD")
	)
	private void onBroadcastAll(Packet<?> packet, ResourceKey<Level> dimension, CallbackInfo ci) {
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			if (recorder.getLevel().dimension() == dimension) {
				recorder.record(packet);
			}
		}
	}

	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(
		Player except,
		double x,
		double y,
		double z,
		double radius,
		ResourceKey<Level> dimension,
		Packet<?> packet,
		CallbackInfo ci
	) {
		if (except instanceof ServerPlayer player && player.getLevel().dimension() == dimension) {
			PlayerRecorder recorder = PlayerRecorders.get(player);
			if (recorder != null) {
				recorder.record(packet);
			}
		}

		ChunkPos pos = new ChunkPos(new BlockPos(x, y, z));
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			if (recorder.getChunks().contains(dimension, pos)) {
				recorder.record(packet);
			}
		}
	}

	@Inject(
		method = "broadcastMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V",
		at = @At("HEAD")
	)
	private void onBroadcastSystemMessage(
		Component message,
		Function<ServerPlayer, Component> filter,
		ChatType type,
		UUID uuid,
		CallbackInfo ci
	) {
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			recorder.record(new ClientboundChatPacket(message, type, uuid));
		}
	}

	@Inject(
		method = "broadcastMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/ChatType;Ljava/util/UUID;)V",
		at = @At("HEAD")
	)
	private void onBroadcastChatMessage(
		Component message,
		ChatType type,
		UUID uuid,
		CallbackInfo ci
	) {
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			recorder.record(new ClientboundChatPacket(message, type, uuid));
		}
	}
}
