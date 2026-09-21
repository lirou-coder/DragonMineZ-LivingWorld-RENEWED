package com.dmzlwfusion.mixin;

import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the registered vanilla summon node to hide Living World's internal menace entity ID. */
@Mixin(value = ArgumentCommandNode.class, remap = false)
public interface ArgumentCommandNodeAccessor<S> {
    @Accessor("customSuggestions")
    @Mutable
    void dmzlivingworld$setCustomSuggestions(SuggestionProvider<S> suggestions);
}
