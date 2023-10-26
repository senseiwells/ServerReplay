package me.senseiwells.replay.util

import net.fabricmc.loader.api.FabricLoader
import org.objectweb.asm.tree.ClassNode
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.extensibility.IMixinInfo

class ReplayMixinConfig: IMixinConfigPlugin {
    override fun onLoad(mixinPackage: String?) {

    }

    override fun getRefMapperConfig(): String? {
        return null
    }

    override fun shouldApplyMixin(targetClassName: String, mixinClassName: String): Boolean {
        if (mixinClassName.startsWith("me.senseiwells.replay.mixin.compat.carpet")) {
            return FabricLoader.getInstance().isModLoaded("carpet")
        }
        return true
    }

    override fun acceptTargets(myTargets: MutableSet<String>, otherTargets: MutableSet<String>) {

    }

    override fun getMixins(): MutableList<String>? {
        return null
    }

    override fun preApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) {

    }

    override fun postApply(
        targetClassName: String,
        targetClass: ClassNode,
        mixinClassName: String,
        mixinInfo: IMixinInfo
    ) {

    }
}