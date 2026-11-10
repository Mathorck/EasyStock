package ch.mathorck.easystock.composables

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.ic_location
import easystock.shared.generated.resources.ic_loc_backpack
import easystock.shared.generated.resources.ic_loc_box
import easystock.shared.generated.resources.ic_loc_cabinet
import easystock.shared.generated.resources.ic_loc_car
import easystock.shared.generated.resources.ic_loc_crate
import easystock.shared.generated.resources.ic_loc_door
import easystock.shared.generated.resources.ic_loc_first_aid
import easystock.shared.generated.resources.ic_loc_folder
import easystock.shared.generated.resources.ic_loc_fridge
import easystock.shared.generated.resources.ic_loc_garage
import easystock.shared.generated.resources.ic_loc_home
import easystock.shared.generated.resources.ic_loc_shelf
import easystock.shared.generated.resources.ic_loc_store
import easystock.shared.generated.resources.ic_loc_toolbox
import easystock.shared.generated.resources.ic_loc_tools
import easystock.shared.generated.resources.ic_loc_van
import easystock.shared.generated.resources.ic_loc_warehouse
import easystock.shared.generated.resources.ic_loc_workbench
import easystock.shared.generated.resources.locations_color_amber
import easystock.shared.generated.resources.locations_color_blue
import easystock.shared.generated.resources.locations_color_blue_grey
import easystock.shared.generated.resources.locations_color_brown
import easystock.shared.generated.resources.locations_color_cyan
import easystock.shared.generated.resources.locations_color_green
import easystock.shared.generated.resources.locations_color_indigo
import easystock.shared.generated.resources.locations_color_orange
import easystock.shared.generated.resources.locations_color_pink
import easystock.shared.generated.resources.locations_color_purple
import easystock.shared.generated.resources.locations_color_red
import easystock.shared.generated.resources.locations_color_teal
import easystock.shared.generated.resources.locations_icon_backpack
import easystock.shared.generated.resources.locations_icon_box
import easystock.shared.generated.resources.locations_icon_cabinet
import easystock.shared.generated.resources.locations_icon_car
import easystock.shared.generated.resources.locations_icon_crate
import easystock.shared.generated.resources.locations_icon_door
import easystock.shared.generated.resources.locations_icon_first_aid
import easystock.shared.generated.resources.locations_icon_folder
import easystock.shared.generated.resources.locations_icon_fridge
import easystock.shared.generated.resources.locations_icon_garage
import easystock.shared.generated.resources.locations_icon_home
import easystock.shared.generated.resources.locations_icon_shelf
import easystock.shared.generated.resources.locations_icon_store
import easystock.shared.generated.resources.locations_icon_toolbox
import easystock.shared.generated.resources.locations_icon_tools
import easystock.shared.generated.resources.locations_icon_van
import easystock.shared.generated.resources.locations_icon_warehouse
import easystock.shared.generated.resources.locations_icon_workbench
import easystock.shared.generated.resources.locations_color_default
import easystock.shared.generated.resources.locations_icon_default
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Display side of ch.mathorck.easystock.data.model.LocationIcons / LocationColors: the one place mapping a
// stored key to a drawable, a color and a label. An unknown key (e.g. from a newer app) shows the default.

private val iconDrawables: Map<String, DrawableResource> = mapOf(
    "warehouse" to Res.drawable.ic_loc_warehouse,
    "shelf" to Res.drawable.ic_loc_shelf,
    "box" to Res.drawable.ic_loc_box,
    "crate" to Res.drawable.ic_loc_crate,
    "cabinet" to Res.drawable.ic_loc_cabinet,
    "workbench" to Res.drawable.ic_loc_workbench,
    "garage" to Res.drawable.ic_loc_garage,
    "home" to Res.drawable.ic_loc_home,
    "door" to Res.drawable.ic_loc_door,
    "store" to Res.drawable.ic_loc_store,
    "fridge" to Res.drawable.ic_loc_fridge,
    "folder" to Res.drawable.ic_loc_folder,
    "tools" to Res.drawable.ic_loc_tools,
    "toolbox" to Res.drawable.ic_loc_toolbox,
    "first_aid" to Res.drawable.ic_loc_first_aid,
    "backpack" to Res.drawable.ic_loc_backpack,
    "car" to Res.drawable.ic_loc_car,
    "van" to Res.drawable.ic_loc_van,
)

private val iconLabels: Map<String, StringResource> = mapOf(
    "warehouse" to Res.string.locations_icon_warehouse,
    "shelf" to Res.string.locations_icon_shelf,
    "box" to Res.string.locations_icon_box,
    "crate" to Res.string.locations_icon_crate,
    "cabinet" to Res.string.locations_icon_cabinet,
    "workbench" to Res.string.locations_icon_workbench,
    "garage" to Res.string.locations_icon_garage,
    "home" to Res.string.locations_icon_home,
    "door" to Res.string.locations_icon_door,
    "store" to Res.string.locations_icon_store,
    "fridge" to Res.string.locations_icon_fridge,
    "folder" to Res.string.locations_icon_folder,
    "tools" to Res.string.locations_icon_tools,
    "toolbox" to Res.string.locations_icon_toolbox,
    "first_aid" to Res.string.locations_icon_first_aid,
    "backpack" to Res.string.locations_icon_backpack,
    "car" to Res.string.locations_icon_car,
    "van" to Res.string.locations_icon_van,
)

/** Mid tones (Material 400-600): readable on both the light and the dark theme. */
private val paletteColors: Map<String, Color> = mapOf(
    "red" to Color(0xFFE53935),
    "pink" to Color(0xFFD81B60),
    "purple" to Color(0xFF8E24AA),
    "indigo" to Color(0xFF5C6BC0),
    "blue" to Color(0xFF1E88E5),
    "cyan" to Color(0xFF00ACC1),
    "teal" to Color(0xFF00897B),
    "green" to Color(0xFF43A047),
    "amber" to Color(0xFFFFB300),
    "orange" to Color(0xFFFB8C00),
    "brown" to Color(0xFF8D6E63),
    "blue_grey" to Color(0xFF78909C),
)

private val colorLabels: Map<String, StringResource> = mapOf(
    "red" to Res.string.locations_color_red,
    "pink" to Res.string.locations_color_pink,
    "purple" to Res.string.locations_color_purple,
    "indigo" to Res.string.locations_color_indigo,
    "blue" to Res.string.locations_color_blue,
    "cyan" to Res.string.locations_color_cyan,
    "teal" to Res.string.locations_color_teal,
    "green" to Res.string.locations_color_green,
    "amber" to Res.string.locations_color_amber,
    "orange" to Res.string.locations_color_orange,
    "brown" to Res.string.locations_color_brown,
    "blue_grey" to Res.string.locations_color_blue_grey,
)

/** Drawable of an icon key. Null or unknown: the default location icon. */
fun locationIconRes(key: String?): DrawableResource = iconDrawables[key] ?: Res.drawable.ic_location

/** Label of an icon key (for content descriptions). */
fun locationIconLabel(key: String?): StringResource = iconLabels[key] ?: Res.string.locations_icon_default

/** Color of a palette key. Null or unknown: null, meaning the default (content) color. */
fun locationColor(key: String?): Color? = paletteColors[key]

/** Label of a color key. */
fun locationColorLabel(key: String?): StringResource = colorLabels[key] ?: Res.string.locations_color_default

/** A location's icon tinted with its color. Decorative unless [contentDescription] is given. */
@Composable
fun LocationIcon(
    icon: String?,
    color: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(locationIconRes(icon)),
        contentDescription = contentDescription,
        tint = locationColor(color) ?: LocalContentColor.current,
        modifier = modifier.size(size),
    )
}
