package ch.mathorck.easystock.enums.settings

import easystock.shared.generated.resources.Res
import easystock.shared.generated.resources.app_info_section_app
import easystock.shared.generated.resources.app_info_section_data
import easystock.shared.generated.resources.app_info_section_device
import easystock.shared.generated.resources.ic_folder_open
import easystock.shared.generated.resources.ic_info
import easystock.shared.generated.resources.ic_settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

internal enum class AppInfoSection(val title: StringResource, val icon: DrawableResource) {
    App(Res.string.app_info_section_app, Res.drawable.ic_info),
    Data(Res.string.app_info_section_data, Res.drawable.ic_folder_open),
    Device(Res.string.app_info_section_device, Res.drawable.ic_settings),
}
