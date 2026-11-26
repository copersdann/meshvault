import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import MeshVault 1.0

Rectangle {
    id: card
    property var item: ({})

    Layout.preferredWidth: 320
    Layout.preferredHeight: 170
    radius: theme.radius
    color: theme.panel
    border.color: theme.border
    border.width: 1

    Theme { id: theme }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        spacing: 8

        RowLayout {
            Layout.fillWidth: true
            Rectangle {
                width: 10; height: 10; radius: 5
                color: card.item["enabled"] ? theme.ok : theme.muted
            }
            Label {
                Layout.fillWidth: true
                text: card.item["name"] || "Принтер"
                color: theme.text
                font.bold: true
                font.pixelSize: 15
                elide: Text.ElideRight
            }
        }
        Label {
            Layout.fillWidth: true
            text: card.item["host"] || ""
            color: theme.muted
            font.pixelSize: 12
            elide: Text.ElideMiddle
        }
        Item { Layout.fillHeight: true }
        RowLayout {
            Layout.fillWidth: true
            Button {
                text: "Проверить"
                onClicked: Api.testPrinter(card.item["id"])
            }
            Item { Layout.fillWidth: true }
            Button {
                text: "Удалить"
                onClicked: Api.deletePrinter(card.item["id"])
            }
        }
    }
}
