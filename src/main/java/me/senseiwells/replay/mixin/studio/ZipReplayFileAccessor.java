package me.senseiwells.replay.mixin.studio;

import com.replaymod.replaystudio.replay.ZipReplayFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

@Mixin(value = ZipReplayFile.class, remap = false)
public interface ZipReplayFileAccessor {
	@Accessor("zipFile")
	ZipFile getZipFile();

	@Accessor("changedEntries")
	Map<String, File> getChangedEntries();

	@Accessor("removedEntries")
	Set<String> getRemovedEntries();
}
