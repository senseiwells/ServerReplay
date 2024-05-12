package me.senseiwells.replay.mixin.viewer;

import me.senseiwells.replay.ducks.ServerReplay$ReplayViewable;
import me.senseiwells.replay.viewer.ReplayViewer;
import me.senseiwells.replay.viewer.ReplayViewerPackets;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.LastSeenMessagesValidator;
import net.minecraft.network.chat.RemoteChatSession;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin implements ServerReplay$ReplayViewable {
	@Unique
	private static final PacketSendListener BYPASS = new PacketSendListener() {};

	@Mutable
	@Shadow
	@Final
	private LastSeenMessagesValidator lastSeenMessages;
	@Shadow
	@Nullable
	private RemoteChatSession chatSession;
	@Shadow
	@Final
	private MinecraftServer server;

	@Unique
	private ReplayViewer replay$viewer = null;

	@Shadow
	public abstract void send(Packet<?> packet, @Nullable PacketSendListener listener);

	@Inject(
		method = {
			"handleAnimate",
			"handleClientCommand",
			"handleContainerButtonClick",
			"handleContainerClick",
			"handlePlaceRecipe",
			"handleContainerClose",
			"handleInteract",
			"handleMovePlayer",
			"handlePlayerAbilities",
			"handlePlayerAction",
			"handlePlayerCommand",
			"handlePlayerInput",
			"handleSetCarriedItem",
			"handleSetCreativeModeSlot",
			"handleSignUpdate",
			"handleUseItemOn",
			"handleUseItem",
			"handleTeleportToEntityPacket",
			"handlePaddleBoat",
			"handleMoveVehicle",
			"handleAcceptTeleportPacket",
			"handleRecipeBookSeenRecipePacket",
			"handleRecipeBookChangeSettingsPacket",
			"handleSeenAdvancements",
			"handleCustomCommandSuggestions",
			"handleSetCommandBlock",
			"handleSetCommandMinecart",
			"handlePickItem",
			"handleRenameItem",
			"handleSetBeaconPacket",
			"handleSetStructureBlock",
			"handleSelectTrade",
			"handleEditBook",
			"handleEntityTagQuery",
			"handleBlockEntityTagQuery",
			"handleSetJigsawBlock",
			"handleJigsawGenerate",
			"handleChangeDifficulty",
			"handleLockDifficulty",
			"handleChatSessionUpdate"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
			shift = At.Shift.AFTER
		),
		cancellable = true
	)
	private void onServerboundPacket(@Coerce Packet<ServerGamePacketListener> packet, CallbackInfo ci) {
		if (this.replay$viewer != null) {
			this.replay$viewer.onServerboundPacket(packet);
			ci.cancel();
		}
	}

	@Inject(
		method = {
			"handleChat",
			"handleChatCommand"
		},
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;tryHandleChat(Ljava/lang/String;Ljava/time/Instant;Lnet/minecraft/network/chat/LastSeenMessages$Update;)Ljava/util/Optional;"
		),
		cancellable = true
	)
	private void onServerboundChatPacket(@Coerce Packet<ServerGamePacketListener> packet, CallbackInfo ci) {
		if (this.replay$viewer != null) {
			ci.cancel();
			this.server.execute(() -> this.replay$viewer.onServerboundPacket(packet));
		}
	}

	@Inject(
		method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void onSendPacket(Packet<?> packet, @Nullable PacketSendListener listener, CallbackInfo ci) {
		if (listener != BYPASS && this.replay$viewer != null && !ReplayViewerPackets.clientboundBypass(packet)) {
			ci.cancel();
		}
	}

	@Override
	public void replay$startViewingReplay(ReplayViewer viewer) {
		this.replay$viewer = viewer;
	}

	@Override
	public void replay$stopViewingReplay() {
		if (this.replay$viewer != null) {
			this.lastSeenMessages = new LastSeenMessagesValidator(20);
			this.replay$viewer = null;
			// Reset chat session
			this.chatSession = null;
		}
	}

	@Override
	public void replay$sendReplayViewerPacket(Packet<?> packet) {
		this.send(packet, BYPASS);
	}

	@Override
	public ReplayViewer replay$getViewingReplay() {
		return this.replay$viewer;
	}
}
