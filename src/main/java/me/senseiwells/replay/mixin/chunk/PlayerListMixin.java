package me.senseiwells.replay.mixin.chunk;

import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.player.PlayerRecorder;
import me.senseiwells.replay.player.PlayerRecorders;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignatureCache;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;
import java.util.function.Predicate;

@Mixin(PlayerList.class)
public class PlayerListMixin {
	@Shadow @Final private MinecraftServer server;

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
		if (except instanceof ServerPlayer player && player.level().dimension() == dimension) {
			PlayerRecorder recorder = PlayerRecorders.get(player);
			if (recorder != null) {
				recorder.record(packet);
			}
		}

		ChunkPos pos = new ChunkPos(BlockPos.containing(x, y, z));
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			if (recorder.getChunks().contains(dimension, pos)) {
				recorder.record(packet);
			}
		}
	}

	@Inject(
		method = "broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Ljava/util/function/Function;Z)V",
		at = @At("HEAD")
	)
	private void onBroadcastSystemMessage(
		Component serverMessage,
		Function<ServerPlayer, Component> playerMessageFactory,
		boolean bypassHiddenChat,
		CallbackInfo ci
	) {
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			recorder.record(new ClientboundSystemChatPacket(serverMessage, bypassHiddenChat));
		}
	}

	@Inject(
		method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Ljava/util/function/Predicate;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
		at = @At("HEAD")
	)
	private void onBroadcastChatMessage(
		PlayerChatMessage message,
		Predicate<ServerPlayer> shouldFilterMessageTo,
		@Nullable ServerPlayer sender,
		ChatType.Bound boundChatType,
		CallbackInfo ci
	) {
		for (ChunkRecorder recorder : ChunkRecorders.recorders()) {
			if (message.isSystem()) {
				recorder.record(new ClientboundDisguisedChatPacket(
					message.decoratedContent(),
					boundChatType.toNetwork(this.server.registryAccess())
				));
				continue;
			}
			recorder.record(new ClientboundPlayerChatPacket(
				message.link().sender(),
				message.link().index(),
				message.signature(),
				message.signedBody().pack(MessageSignatureCache.createDefault()),
				message.unsignedContent(),
				message.filterMask(),
				boundChatType.toNetwork(this.server.registryAccess())
			));
		}
	}
}
