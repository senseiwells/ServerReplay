package me.senseiwells.replay.mixin.viewer;

import me.senseiwells.replay.ducks.ServerReplay$ReplayViewable;
import me.senseiwells.replay.viewer.ReplayViewer;
import me.senseiwells.replay.viewer.ReplayViewerPackets;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin extends ServerCommonPacketListenerImpl implements ServerReplay$ReplayViewable {
	@Unique
	private ReplayViewer replay$viewer = null;

	public ServerGamePacketListenerImplMixin(MinecraftServer minecraftServer, Connection connection, CommonListenerCookie commonListenerCookie) {
		super(minecraftServer, connection, commonListenerCookie);
	}

	@Inject(
		method = "shouldHandleMessage",
		at = @At("HEAD"),
		cancellable = true
	)
	private void canAcceptPacket(Packet<?> packet, CallbackInfoReturnable<Boolean> cir) {
		if (this.replay$viewer != null && !ReplayViewerPackets.serverboundBypass(packet)) {
			cir.setReturnValue(false);
		}
	}

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
			"handleChatSessionUpdate",
			"handleChunkBatchReceived"
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
		method = "method_44900",
		at = @At("HEAD"),
		cancellable = true
	)
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void onServerboundChatPacket(ServerboundChatPacket packet, Optional<LastSeenMessages> optional, CallbackInfo ci) {
		if (this.replay$viewer != null) {
			this.replay$viewer.onServerboundPacket(packet);
			ci.cancel();
		}
	}

	@Inject(
		method = "method_44356",
		at = @At("HEAD"),
		cancellable = true
	)
	@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
	private void onServerboundChatCommandPacket(ServerboundChatCommandPacket packet, Optional<LastSeenMessages> optional, CallbackInfo ci) {
		if (this.replay$viewer != null) {
			this.replay$viewer.onServerboundPacket(packet);
			ci.cancel();
		}
	}

	@Override
	public void replay$setReplayViewer(ReplayViewer viewer) {
		this.replay$viewer = viewer;
	}

	@Override
	public void replay$sendReplayViewerPacket(Packet<?> packet) {
		super.send(packet, null);
	}

	@Override
	public ReplayViewer replay$getReplayViewer() {
		return this.replay$viewer;
	}

	@Override
	public void send(Packet<?> packet, @Nullable PacketSendListener packetSendListener) {
		if (this.replay$viewer == null || ReplayViewerPackets.clientboundBypass(packet)) {
			super.send(packet, packetSendListener);
		}
	}
}
