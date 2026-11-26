import QtQuick 2.15

QtObject {
    readonly property color bg: "#0B0F14"
    readonly property color panel: "#121A24"
    readonly property color panelAlt: "#16202C"
    readonly property color border: "#1F2A37"
    readonly property color text: "#E6EDF3"
    readonly property color muted: "#8B98A5"
    readonly property color accent: "#FF7A1A"
    readonly property color accentDark: "#C85C10"
    readonly property color ok: "#3DDC97"
    readonly property color warn: "#FFC857"
    readonly property color danger: "#FF5C5C"

    readonly property int radius: 14
    readonly property int smallRadius: 9
    readonly property int radiusLarge: 18
    readonly property int pad: 18
    readonly property int sidebarWidth: 236

    // Современные «стеклянные» поверхности
    readonly property color glass: Qt.rgba(0.07, 0.10, 0.14, 0.82)
    readonly property color glassStrong: Qt.rgba(0.06, 0.09, 0.13, 0.95)
    readonly property color glassBorder: Qt.rgba(0.55, 0.65, 0.78, 0.20)
    readonly property color accentSoft: Qt.rgba(1.0, 0.478, 0.102, 0.16)
    readonly property color hover: Qt.rgba(1.0, 1.0, 1.0, 0.07)
    readonly property color shadow: Qt.rgba(0.0, 0.0, 0.0, 0.45)

}
