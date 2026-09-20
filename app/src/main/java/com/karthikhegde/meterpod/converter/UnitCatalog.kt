package com.karthikhegde.meterpod.converter

import kotlin.math.atan
import kotlin.math.tan

object UnitCatalog {

    private fun linear(id: String, name: String, symbol: String, factor: Double) =
        UnitDefinition.linear(id, name, symbol, factor)

    // ---- Angle (base: radians) ----
    private val angle = UnitCategory(
        id = "angle",
        name = "Angle",
        badgeText = "∠",
        units = listOf(
            linear("deg", "Degrees", "°", Math.PI / 180.0),
            linear("rad", "Radians", "rad", 1.0),
            linear("grad", "Gradians", "gon", Math.PI / 200.0),
            linear("turn", "Turns", "turn", 2 * Math.PI),
            // Slope (rise/run) isn't a linear multiple of angle - it needs tan/atan.
            // Diverges toward infinity as the angle approaches 90°/π/2, same as any
            // real slope figure would.
            UnitDefinition(
                id = "slope",
                displayName = "Slope",
                symbol = "rise/run",
                toBase = { value -> atan(value) },
                fromBase = { radians -> tan(radians) }
            )
        )
    )

    // ---- Length (base: meters) ----
    private val length = UnitCategory(
        id = "length",
        name = "Length / Distance",
        badgeText = "LEN",
        units = listOf(
            linear("m", "Meters", "m", 1.0),
            linear("km", "Kilometers", "km", 1000.0),
            linear("cm", "Centimeters", "cm", 0.01),
            linear("mm", "Millimeters", "mm", 0.001),
            linear("mi", "Miles", "mi", 1609.344),
            linear("yd", "Yards", "yd", 0.9144),
            linear("ft", "Feet", "ft", 0.3048),
            linear("in", "Inches", "in", 0.0254),
            linear("nmi", "Nautical Miles", "nmi", 1852.0)
        )
    )

    // ---- Mass / Weight (base: kilograms) ----
    private val mass = UnitCategory(
        id = "mass",
        name = "Mass / Weight",
        badgeText = "MAS",
        units = listOf(
            linear("kg", "Kilograms", "kg", 1.0),
            linear("g", "Grams", "g", 0.001),
            linear("mg", "Milligrams", "mg", 0.000001),
            linear("lb", "Pounds", "lb", 0.45359237),
            linear("oz", "Ounces", "oz", 0.028349523125),
            linear("tonne", "Metric Tons", "t", 1000.0),
            linear("shortton", "US Tons", "ton", 907.18474),
            linear("stone", "Stone", "st", 6.35029318)
        )
    )

    // ---- Energy (base: joules) ----
    private val energy = UnitCategory(
        id = "energy",
        name = "Energy",
        badgeText = "ENR",
        units = listOf(
            linear("j", "Joules", "J", 1.0),
            linear("kj", "Kilojoules", "kJ", 1000.0),
            linear("cal", "Calories", "cal", 4.184),
            linear("kcal", "Kilocalories", "kcal", 4184.0),
            linear("erg", "Ergs", "erg", 1.0e-7),
            linear("wh", "Watt-hours", "Wh", 3600.0),
            linear("kwh", "Kilowatt-hours", "kWh", 3_600_000.0),
            linear("ev", "Electronvolts", "eV", 1.602176634e-19),
            linear("btu", "BTU", "BTU", 1055.05585262)
        )
    )

    // ---- Pressure (base: pascals) ----
    private val pressure = UnitCategory(
        id = "pressure",
        name = "Pressure",
        badgeText = "PRE",
        units = listOf(
            linear("pa", "Pascals", "Pa", 1.0),
            linear("kpa", "Kilopascals", "kPa", 1000.0),
            linear("hpa", "Hectopascals", "hPa", 100.0),
            linear("bar", "Bar", "bar", 100000.0),
            linear("psi", "PSI", "psi", 6894.757293168),
            linear("atm", "Atmospheres", "atm", 101325.0),
            linear("torr", "Torr (mmHg)", "torr", 133.322368421)
        )
    )

    // ---- Force (base: newtons) ----
    private val force = UnitCategory(
        id = "force",
        name = "Force",
        badgeText = "FOR",
        units = listOf(
            linear("n", "Newtons", "N", 1.0),
            linear("kn", "Kilonewtons", "kN", 1000.0),
            linear("dyn", "Dynes", "dyn", 0.00001),
            linear("lbf", "Pound-force", "lbf", 4.4482216153),
            linear("kgf", "Kilogram-force", "kgf", 9.80665)
        )
    )

    // ---- Volume (base: liters) ----
    private val volume = UnitCategory(
        id = "volume",
        name = "Volume",
        badgeText = "VOL",
        units = listOf(
            linear("l", "Liters", "L", 1.0),
            linear("ml", "Milliliters", "mL", 0.001),
            linear("m3", "Cubic Meters", "m³", 1000.0),
            linear("usgal", "US Gallons", "gal", 3.785411784),
            linear("usqt", "US Quarts", "qt", 0.946352946),
            linear("uspt", "US Pints", "pt", 0.473176473),
            linear("usfloz", "US Fluid Ounces", "fl oz", 0.0295735296),
            linear("impgal", "Imperial Gallons", "imp gal", 4.54609),
            linear("ft3", "Cubic Feet", "ft³", 28.316846592),
            linear("in3", "Cubic Inches", "in³", 0.016387064)
        )
    )

    // ---- Area (base: square meters) ----
    private val area = UnitCategory(
        id = "area",
        name = "Area",
        badgeText = "ARE",
        units = listOf(
            linear("m2", "Square Meters", "m²", 1.0),
            linear("km2", "Square Kilometers", "km²", 1_000_000.0),
            linear("cm2", "Square Centimeters", "cm²", 0.0001),
            linear("hectare", "Hectares", "ha", 10000.0),
            linear("acre", "Acres", "ac", 4046.8564224),
            linear("mi2", "Square Miles", "mi²", 2_589_988.110336),
            linear("yd2", "Square Yards", "yd²", 0.83612736),
            linear("ft2", "Square Feet", "ft²", 0.09290304),
            linear("in2", "Square Inches", "in²", 0.00064516)
        )
    )

    // ---- Time (base: seconds) ----
    private val time = UnitCategory(
        id = "time",
        name = "Time",
        badgeText = "TIM",
        units = listOf(
            linear("s", "Seconds", "s", 1.0),
            linear("ms", "Milliseconds", "ms", 0.001),
            linear("min", "Minutes", "min", 60.0),
            linear("hr", "Hours", "hr", 3600.0),
            linear("day", "Days", "day", 86400.0),
            linear("week", "Weeks", "wk", 604800.0),
            linear("month", "Months (avg)", "mo", 2_629_746.0),
            linear("year", "Years (365.25d)", "yr", 31_557_600.0)
        )
    )

    // ---- Speed (base: meters/second) ----
    private val speed = UnitCategory(
        id = "speed",
        name = "Speed",
        badgeText = "SPD",
        units = listOf(
            linear("mps", "Meters/second", "m/s", 1.0),
            linear("kph", "Kilometers/hour", "km/h", 1000.0 / 3600.0),
            linear("mph", "Miles/hour", "mph", 0.44704),
            linear("knot", "Knots", "kn", 0.514444),
            linear("fps", "Feet/second", "ft/s", 0.3048)
        )
    )

    // ---- Temperature (base: celsius) - offsets, not simple ratios ----
    private val temperature = UnitCategory(
        id = "temperature",
        name = "Temperature",
        badgeText = "TEM",
        units = listOf(
            UnitDefinition("c", "Celsius", "°C", { it }, { it }),
            UnitDefinition(
                "f", "Fahrenheit", "°F",
                toBase = { f -> (f - 32.0) * 5.0 / 9.0 },
                fromBase = { c -> c * 9.0 / 5.0 + 32.0 }
            ),
            UnitDefinition(
                "k", "Kelvin", "K",
                toBase = { k -> k - 273.15 },
                fromBase = { c -> c + 273.15 }
            )
        )
    )

    // ---- Data Storage (base: bytes) ----
    private val dataStorage = UnitCategory(
        id = "data",
        name = "Data Storage",
        badgeText = "DAT",
        units = listOf(
            linear("bit", "Bits", "bit", 0.125),
            linear("byte", "Bytes", "B", 1.0),
            linear("kb", "Kilobytes", "KB", 1024.0),
            linear("mb", "Megabytes", "MB", 1024.0 * 1024.0),
            linear("gb", "Gigabytes", "GB", 1024.0 * 1024.0 * 1024.0),
            linear("tb", "Terabytes", "TB", 1024.0 * 1024.0 * 1024.0 * 1024.0)
        )
    )

    // ---- Power (base: watts) ----
    private val power = UnitCategory(
        id = "power",
        name = "Power",
        badgeText = "POW",
        units = listOf(
            linear("w", "Watts", "W", 1.0),
            linear("kw", "Kilowatts", "kW", 1000.0),
            linear("mw", "Megawatts", "MW", 1_000_000.0),
            linear("hp", "Horsepower", "hp", 745.699872)
        )
    )

    val all: List<UnitCategory> = listOf(
        angle, length, mass, energy, pressure, force, volume,
        area, time, speed, temperature, dataStorage, power
    )

    fun byId(id: String): UnitCategory? = all.find { it.id == id }
}
