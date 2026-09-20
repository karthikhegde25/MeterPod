package com.karthikhegde.meterpod.converter

/**
 * A single convertible unit within a category.
 *
 * [toBase] converts a value in this unit into the category's shared base
 * unit; [fromBase] does the reverse. Using functions instead of a plain
 * multiplier lets the same model represent simple ratio units (length,
 * mass, energy...) and units that aren't simple multiples of the base at
 * all — temperature needs an additive offset, and Slope (in the Angle
 * category) needs tan/atan rather than any linear factor.
 */
data class UnitDefinition(
    val id: String,
    val displayName: String,
    val symbol: String,
    val toBase: (Double) -> Double,
    val fromBase: (Double) -> Double
) {
    companion object {
        /** Convenience for the common case: a plain multiplier against the base unit. */
        fun linear(id: String, displayName: String, symbol: String, factorToBase: Double): UnitDefinition {
            return UnitDefinition(
                id = id,
                displayName = displayName,
                symbol = symbol,
                toBase = { value -> value * factorToBase },
                fromBase = { value -> value / factorToBase }
            )
        }
    }
}

data class UnitCategory(
    val id: String,
    val name: String,
    val badgeText: String,
    val units: List<UnitDefinition>
)
