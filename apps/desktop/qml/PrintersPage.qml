import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
import MeshVault 1.0

Item {
    id: page
    Theme { id: theme }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        RowLayout {
            Layout.fillWidth: true
            Label { text: "Подключённые принтеры"; color: theme.text; font.pixelSize: 16; font.bold: true }
            Item { Layout.fillWidth: true }
            Button { text: "Добавить принтер"; onClicked: Window.window.printerDialog.open() }
        }

        Flow {
            Layout.fillWidth: true
            spacing: 16
            Repeater {
                model: Api.printers
                delegate: PrinterCard {
                    item: modelData
                }
            }
        }

        Label {
            Layout.fillWidth: true
            visible: Api.printers.length === 0
            text: "Принтеры не добавлены. Укажите адрес Moonraker (например, 192.168.1.50:7125)."
            color: theme.muted
            font.pixelSize: 13
        }
    }
}
