package me.senseiwells.replay.mixin;

import com.mojang.brigadier.CommandDispatcher;
import me.senseiwells.replay.ServerReplay;
import me.senseiwells.replay.commands.PackCommand;
import me.senseiwells.replay.commands.ReplayCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public class CommandsMixin {
	@Shadow @Final private CommandDispatcher<CommandSourceStack> dispatcher;

	@Inject(
		method = "<init>",
		at = @At("TAIL")
	)
	private void onRegisterCommands(
		Commands.CommandSelection commandSelection,
		CommandBuildContext commandBuildContext,
		CallbackInfo ci
	) {
		ReplayCommand.register(this.dispatcher);

		if (ServerReplay.getConfig().getDebug()) {
			PackCommand.register(this.dispatcher);
		}
	}
}
