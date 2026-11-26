import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import MeshVault 1.0

Item {
    id: row
    property var item: ({})
    height: 72

    Theme { id: theme }

    Rectangle {
        anchors.fill: parent
        anchors.margins: 4
        radius: theme.smallRadius
        color: theme.panel
        border.color: theme.border

        RowLayout {
            anchors.fill: parent
            anchors.margins: 12
            spacing: 14

            ColumnLayout {
                Layout.preferredWidth: 260
                Label {
                    text: row.item["modelName"] || "—"
                    color: theme.text
                    font.bold: true
                    elide: Text.ElideRight
                }
                Label { text: row.item["printerName"] || ""; color: theme.muted; font.pixelSize: 11 }
            }

            ColumnLayout {
                Layout.fillWidth: true
                Label {
                    text: row.item["status"] || ""
                    color: row.item["status"] === "failed" ? theme.danger :
                           row.item["status"] === "printing" ? theme.warn : theme.text
                    font.pixelSize: 12
                }
                Rectangle {
                    Layout.fillWidth: true
                    height: 6
                    radius: 3
                    color: theme.panelAlt
                    Rectangle {
                        width: parent.width * (row.item["progress"] || 0) / 100
                        height: parent.height
                        radius: 3
                        color: theme.accent
                    }
                }
            }

            Label { text: Math.round(row.item["progress"] || 0) + "%"; color: theme.muted; font.pixelSize: 12 }

            Row {
                spacing: 6
                Button {
                    text: "Пауза"
                    visible: row.item["status"] === "printing"
                    onClicked: Api.pauseJob(row.item["id"])
                }
                Button {
                    text: "Продолжить"
                    visible: row.item["status"] === "paused"
                    onClicked: Api.resumeJob(row.item["id"])
                }
                Button {
                    text: "Отмена"
                    visible: ["printing", "paused", "queued"].indexOf(row.item["status"]) >= 0
                    onClicked: Api.cancelJob(row.item["id"])
                }
            }
        }
    }
}
