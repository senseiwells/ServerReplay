package me.senseiwells.replay.mixin.rejoin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("entityMap")
	Int2ObjectMap<ChunkMap.TrackedEntity> getEntityMap();

	@Accessor("serverViewDistance")
	int getViewDistance();

	@Accessor("lightEngine")
	ThreadedLevelLightEngine getLightEngine();
}
