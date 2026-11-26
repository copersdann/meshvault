import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 2.15
import MeshVault 1.0

/// Панель настроек каталога (только UI; состояние — в LibraryPage / ApiClient).
Rectangle {
    id: panel
    property var page: null
    signal detachRequested()

    Theme { id: theme }

    width: 286
    height: 380
    radius: theme.radiusLarge
    color: theme.glassStrong
    border.color: theme.glassBorder
    border.width: 1

    component GlassButton: Button {
        id: button
        implicitHeight: 28
        background: Rectangle {
            radius: theme.smallRadius
            color: button.hovered ? theme.accentSoft : theme.glass
            border.color: button.checked ? theme.accent : theme.glassBorder
            border.width: 1
        }
        contentItem: Label {
            text: button.text
            color: button.checked ? theme.accent : theme.text
            font.pixelSize: 11
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 12
        spacing: 8

        RowLayout {
            Layout.fillWidth: true
            Label { text: "Каталог"; color: theme.text; font.bold: true; font.pixelSize: 14; Layout.fillWidth: true }
            GlassButton { text: "⧉"; onClicked: panel.detachRequested() }
            GlassButton { text: "✕"; onClicked: panel.visible = false }
        }

        Label { text: "Загружать моделей: " + Api.catalogLimit; color: theme.muted; font.pixelSize: 10 }
        SpinBox {
            Layout.fillWidth: true
            from: 20
            to: 200
            stepSize: 10
            value: Api.catalogLimit
            onValueChanged: Api.catalogLimit = value
        }

        Label { text: "Размер карточки: " + page.cardWidth; color: theme.muted; font.pixelSize: 10 }
        Slider {
            Layout.fillWidth: true
            from: 180
            to: 380
            stepSize: 10
            value: page.cardWidth
            onValueChanged: page.cardWidth = Math.round(value)
        }

        Label { text: "Формат: " + page.formatFilter; color: theme.muted; font.pixelSize: 10 }
        ComboBox {
            Layout.fillWidth: true
            model: ["Все", "stl", "3mf", "obj", "glb", "ply", "step", "gcode"]
            currentIndex: 0
            onActivated: page.formatFilter = currentText === "Все" ? "" : currentText
        }

        Switch { text: "Показывать превью"; checked: page.showPreview; onCheckedChanged: page.showPreview = checked }
        Switch { text: "Компактные карточки"; checked: page.compact; onCheckedChanged: page.compact = checked }
        Switch { text: "Статистика на главной"; checked: page.showStats; onCheckedChanged: page.showStats = checked }

        GlassButton { text: "Обновить список"; Layout.fillWidth: true; onClicked: Api.refreshModels(page.trashMode) }
        GlassButton { text: "Сброс настроек"; Layout.fillWidth: true; onClicked: page.resetViewSettings() }
    }
}
