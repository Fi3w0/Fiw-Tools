package com.fiw.tools.elemental

/** The three tracked elemental statuses. Burning is handled by vanilla fire and not tracked here. */
enum class ElementalStatus {
    /** Ice — Slowness VI, can't sprint, snowflake particles. Consumed by thaw_burst. */
    FROZEN,
    /** Water — dampens fire damage, amplifies lightning hits. Consumed by storm_chain. */
    SOAKED,
    /** Lightning — deals small periodic damage. Chains to nearby SOAKED targets. */
    SHOCKED
}
