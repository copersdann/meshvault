import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 2.15
import QtQuick.Dialogs

/// Панель тонких настроек вьювера. Только UI: всё состояние живёт в settings.
Rectangle {
    id: controls
    property QtObject settings: null
    property var viewer: null

    Theme { id: theme }

    width: 286
    height: Math.min(620, (parent ? parent.height : 620) - 70)
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

    Flickable {
        anchors.fill: parent
        anchors.margins: 12
        contentWidth: width
        contentHeight: content.implicitHeight
        clip: true
        flickableDirection: Flickable.VerticalFlick

        ColumnLayout {
            id: content
            width: parent.width
            spacing: 8

            RowLayout {
                Layout.fillWidth: true
                Label {
                    text: "Настройки вида"
                    color: theme.text
                    font.bold: true
                    font.pixelSize: 14
                    Layout.fillWidth: true
                }
                GlassButton {
                    text: "✕"
                    onClicked: controls.visible = false
                }
            }

            Label { text: "КАМЕРА"; color: theme.accent; font.pixelSize: 10; font.bold: true; Layout.topMargin: 4 }
            RowLayout {
                Layout.fillWidth: true
                spacing: 4
                GlassButton { text: "Изо"; Layout.fillWidth: true; onClicked: viewer.setView("iso") }
                GlassButton { text: "Верх"; Layout.fillWidth: true; onClicked: viewer.setView("top") }
                GlassButton { text: "Переди"; Layout.fillWidth: true; onClicked: viewer.setView("front") }
                GlassButton { text: "Справа"; Layout.fillWidth: true; onClicked: viewer.setView("right") }
            }
            GlassButton { text: "Вписать модель"; Layout.fillWidth: true; onClicked: viewer.frame() }

            Switch { text: "Ортографическая камера"; checked: settings.orthographic; onCheckedChanged: settings.orthographic = checked }
            Label { text: "Масштаб орто: x" + settings.orthoZoom.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0.2; to: 3; value: settings.orthoZoom; onValueChanged: settings.orthoZoom = value }
            Label { text: "Поле зрения: " + Math.round(settings.fov) + "°"; color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 20; to: 80; value: settings.fov; onValueChanged: settings.fov = value }
            Label { text: "Скорость управления: " + settings.navSpeed.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0.02; to: 0.4; value: settings.navSpeed; onValueChanged: settings.navSpeed = value }
            Switch { text: "Автовращение"; checked: settings.autoRotate; onCheckedChanged: settings.autoRotate = checked }
            Label { text: "Скорость авто: " + settings.autoRotateSpeed.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0.05; to: 1.0; value: settings.autoRotateSpeed; onValueChanged: settings.autoRotateSpeed = value }

            Label { text: "СЕТКА И ОСИ"; color: theme.accent; font.pixelSize: 10; font.bold: true; Layout.topMargin: 4 }
            Switch { text: "Сетка стола"; checked: settings.showGrid; onCheckedChanged: settings.showGrid = checked }
            Label { text: "Плотность: " + settings.gridDivisions; color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 4; to: 30; stepSize: 1; value: settings.gridDivisions; onValueChanged: settings.gridDivisions = Math.round(value) }
            Label { text: "Размер: x" + settings.gridScale.toFixed(1); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 1; to: 4; value: settings.gridScale; onValueChanged: settings.gridScale = value }
            Label { text: "Прозрачность: " + settings.gridOpacity.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0.1; to: 1; value: settings.gridOpacity; onValueChanged: settings.gridOpacity = value }
            GlassButton {
                text: "Цвет сетки"
                Layout.fillWidth: true
                onClicked: gridColorDialog.open()
            }
            Switch { text: "Оси X/Y/Z"; checked: settings.showAxes; onCheckedChanged: settings.showAxes = checked }
            Label { text: "Длина осей: x" + settings.axesScale.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0.2; to: 2; value: settings.axesScale; onValueChanged: settings.axesScale = value }

            Label { text: "МАТЕРИАЛ И СВЕТ"; color: theme.accent; font.pixelSize: 10; font.bold: true; Layout.topMargin: 4 }
            Switch { text: "Освещение"; checked: settings.lit; onCheckedChanged: settings.lit = checked }
            Switch { text: "AMB. затенение (SSAO)"; checked: settings.aoEnabled; onCheckedChanged: settings.aoEnabled = checked }
            Switch { text: "Плоские грани (геометрия)"; checked: settings.flatShading; onCheckedChanged: settings.flatShading = checked }
            Switch { text: "Габаритный контур"; checked: settings.showDimensions; onCheckedChanged: settings.showDimensions = checked }
            Switch { text: "Тени на стол"; checked: settings.shadowsEnabled; onCheckedChanged: settings.shadowsEnabled = checked }
            Label { text: "Сила теней: " + settings.shadowStrength.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0; to: 1; value: settings.shadowStrength; onValueChanged: settings.shadowStrength = value }
            Label { text: "Смещение теней: " + settings.shadowBias.toFixed(4); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0; to: 0.02; stepSize: 0.0005; value: settings.shadowBias; onValueChanged: settings.shadowBias = value }
            GlassButton { text: "Цвет стола"; Layout.fillWidth: true; onClicked: floorColorDialog.open() }
            Switch { text: "Каркас (wireframe)"; checked: settings.wireframe; onCheckedChanged: settings.wireframe = checked }
            Label { text: "Шероховатость: " + settings.roughness.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0.05; to: 1; value: settings.roughness; onValueChanged: settings.roughness = value }
            Label { text: "Металличность: " + settings.metalness.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0; to: 1; value: settings.metalness; onValueChanged: settings.metalness = value }
            Label { text: "Блик: " + settings.specular.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0; to: 1; value: settings.specular; onValueChanged: settings.specular = value }
            Label { text: "Ключевой свет: " + settings.keyLight.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0.2; to: 3; value: settings.keyLight; onValueChanged: settings.keyLight = value }
            Label { text: "Заполняющий: " + settings.fillLight.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0; to: 1.5; value: settings.fillLight; onValueChanged: settings.fillLight = value }
            Label { text: "Контровой: " + settings.rimLight.toFixed(2); color: theme.muted; font.pixelSize: 10 }
            Slider { Layout.fillWidth: true; from: 0; to: 1; value: settings.rimLight; onValueChanged: settings.rimLight = value }
            RowLayout {
                Layout.fillWidth: true
                GlassButton { text: "Цвет модели"; Layout.fillWidth: true; onClicked: modelColorDialog.open() }
                GlassButton { text: "Фон"; Layout.fillWidth: true; onClicked: backgroundDialog.open() }
            }

            RowLayout {
                Layout.fillWidth: true
                Layout.topMargin: 6
                GlassButton { text: "Сброс настроек"; Layout.fillWidth: true; onClicked: settings.reset() }
                GlassButton { text: "Сброс камеры"; Layout.fillWidth: true; onClicked: viewer.frame() }
            }
        }
    }

    ColorDialog {
        id: modelColorDialog
        title: "Цвет модели"
        selectedColor: settings.modelColor
        onAccepted: settings.modelColor = selectedColor.toString()
    }
    ColorDialog {
        id: backgroundDialog
        title: "Цвет фона"
        selectedColor: settings.backgroundColor
        onAccepted: settings.backgroundColor = selectedColor.toString()
    }
    ColorDialog {
        id: floorColorDialog
        title: "Цвет стола"
        selectedColor: settings.floorColor
        onAccepted: settings.floorColor = selectedColor.toString()
    }
    ColorDialog {
        id: gridColorDialog
        title: "Цвет сетки"
        selectedColor: settings.gridColor
        onAccepted: settings.gridColor = selectedColor.toString()
    }
}
