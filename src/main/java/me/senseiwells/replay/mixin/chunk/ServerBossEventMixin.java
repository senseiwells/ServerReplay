package me.senseiwells.replay.mixin.chunk;

import com.llamalad7.mixinextras.sugar.Local;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.ducks.ServerReplay$ChunkRecordable;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Mixin(ServerBossEvent.class)
public abstract class ServerBossEventMixin extends BossEvent implements ServerReplay$ChunkRecordable {
	@Shadow private boolean visible;
	@Unique private final Set<ChunkRecorder> replay$recorders = new HashSet<>();

	public ServerBossEventMixin(UUID id, Component name, BossBarColor color, BossBarOverlay overlay) {
		super(id, name, color, overlay);
	}

	@Inject(
		method = "broadcast",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"
		)
	)
	private void onBroadcast(
		Function<BossEvent, ClientboundBossEventPacket> packetGetter,
		CallbackInfo ci,
		@Local ClientboundBossEventPacket packet
	) {
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.record(packet);
		}
	}

	@Inject(
		method = "removeAllPlayers",
		at = @At("TAIL")
	)
	private void onRemoveAll(CallbackInfo ci) {
		this.removeAllRecorders();
	}

	@Inject(
		method = "setVisible",
		at = @At(
			value = "INVOKE",
			target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"
		)
	)
	private void onSetVisible(boolean visible, CallbackInfo ci) {
		ClientboundBossEventPacket packet = visible ?
			ClientboundBossEventPacket.createAddPacket(this) :
			ClientboundBossEventPacket.createRemovePacket(this.getId());
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.record(packet);
		}
	}

	@NotNull
	@Override
	public  Collection<ChunkRecorder> replay$getRecorders() {
		return this.replay$recorders;
	}

	@Override
	public void replay$addRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.add(recorder) && this.visible) {
			recorder.record(ClientboundBossEventPacket.createAddPacket(this));
		}
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.remove(recorder) && this.visible) {
			recorder.record(ClientboundBossEventPacket.createRemovePacket(this.getId()));
		}
	}

	@Override
	public void replay$removeAllRecorders() {
		if (this.visible) {
			ClientboundBossEventPacket packet = ClientboundBossEventPacket.createRemovePacket(this.getId());
			for (ChunkRecorder recorder : this.replay$recorders) {
				recorder.record(packet);
			}
		}
		this.replay$recorders.clear();
	}
}
