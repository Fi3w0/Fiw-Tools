package com.fiw.tools.util;

import net.minecraft.core.Holder;

/**
 * Java helper to unwrap Holder.value() — Kotlin's synthetic-property resolution
 * collides with the private 'value' field on Holder.Reference, so we go through
 * a Java site to force the interface method call.
 */
public final class HolderAccess {
    private HolderAccess() {}

    public static <T> T value(Holder<T> holder) {
        return holder.value();
    }
}
