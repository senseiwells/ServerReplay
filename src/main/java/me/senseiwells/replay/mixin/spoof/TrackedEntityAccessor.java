package me.senseiwells.replay.mixin.spoof;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.TrackedEntity.class)
public interface TrackedEntityAccessor {
	@Accessor("entity")
	Entity getEntity();

	@Accessor("serverEntity")
	ServerEntity getServerEntity();
}
