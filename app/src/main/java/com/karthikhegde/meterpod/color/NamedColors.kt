package com.karthikhegde.meterpod.color

import android.graphics.Color

/**
 * The standard 147-name CSS/X11 color keyword list (e.g. "Tomato", "SkyBlue",
 * "RebeccaPurple") with their defined hex values. These are a fixed technical
 * spec, not creative content, so they're just reproduced directly rather than
 * fetched from anywhere.
 */
object NamedColors {

    data class Entry(val name: String, val argb: Int)

    val all: List<Entry> = listOf(
        "AliceBlue" to "#F0F8FF", "AntiqueWhite" to "#FAEBD7", "Aqua" to "#00FFFF",
        "Aquamarine" to "#7FFFD4", "Azure" to "#F0FFFF", "Beige" to "#F5F5DC",
        "Bisque" to "#FFE4C4", "Black" to "#000000", "BlanchedAlmond" to "#FFEBCD",
        "Blue" to "#0000FF", "BlueViolet" to "#8A2BE2", "Brown" to "#A52A2A",
        "BurlyWood" to "#DEB887", "CadetBlue" to "#5F9EA0", "Chartreuse" to "#7FFF00",
        "Chocolate" to "#D2691E", "Coral" to "#FF7F50", "CornflowerBlue" to "#6495ED",
        "Cornsilk" to "#FFF8DC", "Crimson" to "#DC143C", "Cyan" to "#00FFFF",
        "DarkBlue" to "#00008B", "DarkCyan" to "#008B8B", "DarkGoldenRod" to "#B8860B",
        "DarkGray" to "#A9A9A9", "DarkGreen" to "#006400", "DarkKhaki" to "#BDB76B",
        "DarkMagenta" to "#8B008B", "DarkOliveGreen" to "#556B2F", "DarkOrange" to "#FF8C00",
        "DarkOrchid" to "#9932CC", "DarkRed" to "#8B0000", "DarkSalmon" to "#E9967A",
        "DarkSeaGreen" to "#8FBC8F", "DarkSlateBlue" to "#483D8B", "DarkSlateGray" to "#2F4F4F",
        "DarkTurquoise" to "#00CED1", "DarkViolet" to "#9400D3", "DeepPink" to "#FF1493",
        "DeepSkyBlue" to "#00BFFF", "DimGray" to "#696969", "DodgerBlue" to "#1E90FF",
        "FireBrick" to "#B22222", "FloralWhite" to "#FFFAF0", "ForestGreen" to "#228B22",
        "Fuchsia" to "#FF00FF", "Gainsboro" to "#DCDCDC", "GhostWhite" to "#F8F8FF",
        "Gold" to "#FFD700", "GoldenRod" to "#DAA520", "Gray" to "#808080",
        "Green" to "#008000", "GreenYellow" to "#ADFF2F", "HoneyDew" to "#F0FFF0",
        "HotPink" to "#FF69B4", "IndianRed" to "#CD5C5C", "Indigo" to "#4B0082",
        "Ivory" to "#FFFFF0", "Khaki" to "#F0E68C", "Lavender" to "#E6E6FA",
        "LavenderBlush" to "#FFF0F5", "LawnGreen" to "#7CFC00", "LemonChiffon" to "#FFFACD",
        "LightBlue" to "#ADD8E6", "LightCoral" to "#F08080", "LightCyan" to "#E0FFFF",
        "LightGoldenRodYellow" to "#FAFAD2", "LightGray" to "#D3D3D3", "LightGreen" to "#90EE90",
        "LightPink" to "#FFB6C1", "LightSalmon" to "#FFA07A", "LightSeaGreen" to "#20B2AA",
        "LightSkyBlue" to "#87CEFA", "LightSlateGray" to "#778899", "LightSteelBlue" to "#B0C4DE",
        "LightYellow" to "#FFFFE0", "Lime" to "#00FF00", "LimeGreen" to "#32CD32",
        "Linen" to "#FAF0E6", "Magenta" to "#FF00FF", "Maroon" to "#800000",
        "MediumAquaMarine" to "#66CDAA", "MediumBlue" to "#0000CD", "MediumOrchid" to "#BA55D3",
        "MediumPurple" to "#9370DB", "MediumSeaGreen" to "#3CB371", "MediumSlateBlue" to "#7B68EE",
        "MediumSpringGreen" to "#00FA9A", "MediumTurquoise" to "#48D1CC", "MediumVioletRed" to "#C71585",
        "MidnightBlue" to "#191970", "MintCream" to "#F5FFFA", "MistyRose" to "#FFE4E1",
        "Moccasin" to "#FFE4B5", "NavajoWhite" to "#FFDEAD", "Navy" to "#000080",
        "OldLace" to "#FDF5E6", "Olive" to "#808000", "OliveDrab" to "#6B8E23",
        "Orange" to "#FFA500", "OrangeRed" to "#FF4500", "Orchid" to "#DA70D6",
        "PaleGoldenRod" to "#EEE8AA", "PaleGreen" to "#98FB98", "PaleTurquoise" to "#AFEEEE",
        "PaleVioletRed" to "#DB7093", "PapayaWhip" to "#FFEFD5", "PeachPuff" to "#FFDAB9",
        "Peru" to "#CD853F", "Pink" to "#FFC0CB", "Plum" to "#DDA0DD",
        "PowderBlue" to "#B0E0E6", "Purple" to "#800080", "RebeccaPurple" to "#663399",
        "Red" to "#FF0000", "RosyBrown" to "#BC8F8F", "RoyalBlue" to "#4169E1",
        "SaddleBrown" to "#8B4513", "Salmon" to "#FA8072", "SandyBrown" to "#F4A460",
        "SeaGreen" to "#2E8B57", "SeaShell" to "#FFF5EE", "Sienna" to "#A0522D",
        "Silver" to "#C0C0C0", "SkyBlue" to "#87CEEB", "SlateBlue" to "#6A5ACD",
        "SlateGray" to "#708090", "Snow" to "#FFFAFA", "SpringGreen" to "#00FF7F",
        "SteelBlue" to "#4682B4", "Tan" to "#D2B48C", "Teal" to "#008080",
        "Thistle" to "#D8BFD8", "Tomato" to "#FF6347", "Turquoise" to "#40E0D0",
        "Violet" to "#EE82EE", "Wheat" to "#F5DEB3", "White" to "#FFFFFF",
        "WhiteSmoke" to "#F5F5F5", "Yellow" to "#FFFF00", "YellowGreen" to "#9ACD32"
    ).map { (name, hex) -> Entry(name, Color.parseColor(hex)) }
}
