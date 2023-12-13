package me.senseiwells.replay.mixin.rejoin;

import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import me.senseiwells.replay.rejoin.RejoinGamePacketListener;
import me.senseiwells.replay.rejoin.RejoinedReplayPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.Map;

@Mixin(PlayerList.class)
@SuppressWarnings("unused")
public abstract class PlayerListMixin {
	@Shadow public abstract @Nullable CompoundTag load(ServerPlayer player);

	@WrapOperation(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;load(Lnet/minecraft/server/level/ServerPlayer;)Lnet/minecraft/nbt/CompoundTag;"
		)
	)
	private CompoundTag onLoadPlayerData(
		PlayerList instance,
		ServerPlayer player,
		Operation<CompoundTag> operation,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		if (player instanceof RejoinedReplayPlayer replay) {
			spoofed.set(replay);
			CompoundTag data = replay.getOriginal().saveWithoutId(new CompoundTag());
			replay.load(data);
			return data;
		}
		return operation.call(instance, player);
	}

	@WrapWithCondition(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V",
			remap = false
		)
	)
	private boolean shouldLogPlayerJoin(
		Logger instance,
		String string,
		Object[] objects,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		return spoofed.get() == null;
	}

	@WrapOperation(
		method = "placeNewPlayer",
		at = @At(
			value = "NEW",
			target = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)Lnet/minecraft/server/network/ServerGamePacketListenerImpl;"
		)
	)
	private ServerGamePacketListenerImpl postCreatePacketListener(
		MinecraftServer server,
		Connection connection,
		ServerPlayer player,
		CommonListenerCookie cookies,
		Operation<ServerGamePacketListenerImpl> constructor,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		RejoinedReplayPlayer replay = spoofed.get();
		if (replay != null) {
			return new RejoinGamePacketListener(replay, connection, cookies);
		}
		return constructor.call(server, connection, player, cookies);
	}

	@WrapWithCondition(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;broadcastSystemMessage(Lnet/minecraft/network/chat/Component;Z)V"
		)
	)
	private boolean shouldBroadcastSystemMessage(
		PlayerList instance,
		Component message,
		boolean bypassHiddenChat,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		return spoofed.get() == null;
	}

	@WrapWithCondition(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
		)
	)
	private <E> boolean shouldAddPlayer(
		List<E> instance,
		E player,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		return spoofed.get() == null;
	}

	@WrapWithCondition(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
		)
	)
	private <K, V> boolean shouldAddPlayer(
		Map<K, V> instance,
		K k,
		V v,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		return spoofed.get() == null;
	}

	@WrapWithCondition(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"
		)
	)
	private boolean shouldBroadcastToAll(
		PlayerList instance,
		Packet<?> packet,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		// We do not need to send the player to itself!
		// It already does this because the player is already online
		return spoofed.get() == null;
	}

	@WrapWithCondition(
		method = "placeNewPlayer",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/level/ServerLevel;addNewPlayer(Lnet/minecraft/server/level/ServerPlayer;)V"
		)
	)
	private boolean shouldAddNewPlayer(
		ServerLevel instance,
		ServerPlayer player,
		@Share("spoofed") LocalRef<RejoinedReplayPlayer> spoofed
	) {
		return spoofed.get() == null;
	}
}
