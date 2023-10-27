package me.senseiwells.replay.ducks;

import me.senseiwells.replay.util.ducks.TrackedEntityInvoker;

public interface ServerReplay$TrackedEntityInvoker extends TrackedEntityInvoker {
	@Override
	default int getRange() {
		return this.replay$getEffectiveRange();
	}

	int replay$getEffectiveRange();
}
