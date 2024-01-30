package me.senseiwells.replay.mixin.chunk;

import me.senseiwells.replay.chunk.ChunkArea;
import me.senseiwells.replay.chunk.ChunkRecorder;
import me.senseiwells.replay.chunk.ChunkRecorders;
import me.senseiwells.replay.ducks.ServerReplay$ChunkRecordable;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(ChunkMap.TrackedEntity.class)
public class TrackedEntityMixin implements ServerReplay$ChunkRecordable {
	@Unique private final Set<ChunkRecorder> replay$recorders = new HashSet<>();

	@Shadow @Final Entity entity;
	@Shadow @Final ServerEntity serverEntity;

	@Override
	public void replay$addRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.add(recorder)) {
			List<Packet<ClientGamePacketListener>> list = new ArrayList<>();
			this.serverEntity.sendPairingData(null, list::add);
			recorder.record(new ClientboundBundlePacket(list));
		}
	}

	@Override
	public void replay$removeRecorder(ChunkRecorder recorder) {
		if (this.replay$recorders.remove(recorder)) {
			recorder.record(new ClientboundRemoveEntitiesPacket(
				this.entity.getId()
			));
		}
	}

	@Inject(
		method = "broadcast",
		at = @At("HEAD")
	)
	private void onBroadcast(Packet<?> packet, CallbackInfo ci) {
		for (ChunkRecorder recorder : this.replay$recorders) {
			recorder.record(packet);
		}
	}

	@Inject(
		method = "updatePlayers",
		at = @At("HEAD")
	)
	private void onUpdate(List<ServerPlayer> playersList, CallbackInfo ci) {
		ChunkPos pos = this.entity.chunkPosition();
		Level level = this.entity.level();
		for (ChunkRecorder recorder : ChunkRecorders.all()) {
			ChunkArea area = recorder.getChunks();
			if (area.contains(level, pos)) {
				this.addRecorder(recorder);
			} else {
				this.removeRecorder(recorder);
			}
		}
	}

	@Inject(
		method = "broadcastRemoved",
		at = @At("HEAD")
	)
	private void onRemoved(CallbackInfo ci) {
		for (ChunkRecorder recorder : new ArrayList<>(this.replay$recorders)) {
			this.removeRecorder(recorder);
		}
	}
}
