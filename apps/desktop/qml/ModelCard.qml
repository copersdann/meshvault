import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
import MeshVault 1.0

Rectangle {
    id: card
    property var item: ({})
    property bool trashMode: false
    property bool showPreview: true
    property bool compact: false

    Layout.preferredWidth: 260
    Layout.preferredHeight: card.compact ? 292 : 336
    color: theme.panel
    radius: theme.radius
    border.color: theme.border
    border.width: 1

    Theme { id: theme }

    Column {
        anchors.fill: parent

        Rectangle {
            width: parent.width
            height: card.compact ? 84 : 176
            color: theme.panelAlt
            clip: true

            Image {
                anchors.fill: parent
                anchors.margins: 10
                visible: card.showPreview
                source: card.item["thumbUrl"] || ""
                fillMode: Image.PreserveAspectFit
                asynchronous: true
                cache: true
            }

            Rectangle {
                anchors.fill: parent
                visible: !card.showPreview
                color: theme.glass
                Label {
                    anchors.centerIn: parent
                    text: "Превью скрыто"
                    color: theme.muted
                    font.pixelSize: 11
                }
            }

            Rectangle {
                anchors.top: parent.top
                anchors.right: parent.right
                anchors.margins: 10
                radius: 6
                color: statusColor()
                opacity: 0.18
                width: 76
                height: 24
                Label {
                    anchors.centerIn: parent
                    text: statusText()
                    color: statusColor()
                    font.pixelSize: 11
                    font.bold: true
                }
            }
        }

        ColumnLayout {
            width: parent.width
            anchors.margins: 14
            spacing: 4

            Label {
                Layout.fillWidth: true
                text: card.item["name"] || "Без названия"
                color: theme.text
                font.pixelSize: 15
                font.bold: true
                elide: Text.ElideRight
            }
            RowLayout {
                spacing: 8
                Rectangle {
                    radius: 5
                    color: theme.accent
                    opacity: 0.16
                    width: formatLabel.implicitWidth + 12
                    height: 20
                    Label {
                        id: formatLabel
                        anchors.centerIn: parent
                        text: (card.item["format"] || "—").toUpperCase()
                        color: theme.accent
                        font.pixelSize: 10
                        font.bold: true
                    }
                }
                Label {
                    text: (card.item["triangles"] || 0).toLocaleString() + " △"
                    color: theme.muted
                    font.pixelSize: 11
                }
            }
            Label {
                Layout.fillWidth: true
                text: formatBytes(card.item["size"] || 0)
                color: theme.muted
                font.pixelSize: 11
            }
            RowLayout {
                Layout.fillWidth: true
                visible: !card.compact
                spacing: 8
                Label {
                    Layout.fillWidth: true
                    text: card.item["bbox"] || "габариты не рассчитаны"
                    color: theme.muted
                    font.pixelSize: 11
                    elide: Text.ElideRight
                }
                Label {
                    visible: card.item["watertight"] !== undefined && card.item["watertight"] !== null
                    text: card.item["watertight"] ? "замкнут" : "не замкнут"
                    color: card.item["watertight"] ? theme.ok : theme.warn
                    font.pixelSize: 10
                }
            }
            Label {
                Layout.fillWidth: true
                visible: !card.compact
                text: (card.item["tags"] || []).slice(0, 3).join(", ") || "без тегов"
                color: theme.accent
                font.pixelSize: 10
                elide: Text.ElideRight
            }
            Label {
                Layout.fillWidth: true
                visible: !card.compact
                text: formatDate(card.item["createdAt"])
                color: theme.muted
                font.pixelSize: 10
            }
            Button {
                Layout.fillWidth: true
                visible: card.trashMode
                text: "Восстановить"
                z: 2
                onClicked: Api.restoreModel(card.item["id"])
            }
        }
    }

    MouseArea {
        anchors.fill: parent
        cursorShape: Qt.PointingHandCursor
        onClicked: Window.window.navigate("model", card.item["id"])
    }

    function statusColor() {
        return card.item["status"] === "ready" ? theme.ok :
               card.item["status"] === "failed" ? theme.danger : theme.warn;
    }

    function statusText() {
        return card.item["status"] === "ready" ? "готово" :
               card.item["status"] === "failed" ? "ошибка" : "обработка";
    }

    function formatBytes(value) {
        if (value < 1024) return value + " B";
        if (value < 1024 * 1024) return (value / 1024).toFixed(1) + " KB";
        return (value / 1024 / 1024).toFixed(1) + " MB";
    }

    function formatDate(value) {
        if (!value) return "";
        const date = new Date(value);
        if (isNaN(date.getTime())) return "";
        return "Добавлено: " + date.toLocaleDateString(Qt.locale("ru_RU"), "dd.MM.yyyy");
    }
}
