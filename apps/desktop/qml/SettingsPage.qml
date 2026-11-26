import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Dialogs
import MeshVault 1.0

Item {
    id: page
    Theme { id: theme }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        Rectangle {
            Layout.fillWidth: true
            radius: theme.radius
            color: theme.panel
            border.color: theme.border
            height: 230

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 12

                Label { text: "Подключение к серверу"; color: theme.text; font.bold: true; font.pixelSize: 16 }

                TextField {
                    id: urlField
                    Layout.fillWidth: true
                    text: Api.serverUrl
                    placeholderText: "http://192.168.1.10:8000"
                    color: theme.text
                    background: Rectangle {
                        color: theme.panelAlt
                        border.color: theme.border
                        radius: theme.smallRadius
                    }
                }
                TextField {
                    id: tokenField
                    Layout.fillWidth: true
                    text: Api.token
                    placeholderText: "API токен (если включён)"
                    echoMode: TextInput.Password
                    color: theme.text
                    background: Rectangle {
                        color: theme.panelAlt
                        border.color: theme.border
                        radius: theme.smallRadius
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    Button {
                        text: "Сохранить и подключиться"
                        onClicked: {
                            Api.serverUrl = urlField.text;
                            Api.token = tokenField.text;
                            Api.checkHealth();
                            Api.refreshModels();
                        }
                    }
                    Item { Layout.fillWidth: true }
                    Label {
                        text: Api.health["status"] === "ok" ? "● Сервер доступен" : "● Нет связи"
                        color: Api.health["status"] === "ok" ? theme.ok : theme.danger
                        font.pixelSize: 12
                    }
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            radius: theme.radius
            color: theme.panel
            border.color: theme.border
            height: 250

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 10

                Label {
                    text: "Каталог моделей — автоматический импорт"
                    color: theme.text
                    font.bold: true
                    font.pixelSize: 16
                }
                Label {
                    text: "Сервер сканирует папку, определяет формат, создаёт теги из названий папок и раскладывает модели по коллекциям."
                    color: theme.muted
                    font.pixelSize: 11
                    wrapMode: Text.WordWrap
                    Layout.fillWidth: true
                }
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 10
                    TextField {
                        id: importDirField
                        Layout.fillWidth: true
                        text: Api.importConfig["directory"] || ""
                        placeholderText: "Путь к папке на этом ПК"
                        color: theme.text
                        background: Rectangle {
                            color: theme.panelAlt
                            border.color: theme.border
                            radius: theme.smallRadius
                        }
                    }
                    Button { text: "Папка..."; onClicked: folderDialog.open() }
                    SpinBox {
                        id: importInterval
                        from: 1
                        to: 1440
                        value: (Api.importConfig["interval_min"] || 5)
                        editable: true
                    }
                    Label { text: "мин"; color: theme.muted; font.pixelSize: 11 }
                }
                RowLayout {
                    Layout.fillWidth: true
                    Button {
                        text: "Сохранить"
                        onClicked: Api.setImportConfig(importDirField.text, importInterval.value)
                    }
                    Button { text: "Сканировать сейчас"; onClicked: Api.scanImport() }
                    Button { text: "Предпросмотр"; onClicked: Api.scanImportDryRun() }
                    Item { Layout.fillWidth: true }
                    Label {
                        text: Api.importConfig["last_scan"] ? ("Последнее: " + Api.importConfig["last_scan"]) : "Ещё не сканировалось"
                        color: theme.muted
                        font.pixelSize: 11
                        elide: Text.ElideRight
                    }
                }
                Label {
                    id: importResultLabel
                    Layout.fillWidth: true
                    text: "—"
                    color: theme.muted
                    font.pixelSize: 11
                    wrapMode: Text.WordWrap
                }
                Connections {
                    target: Api
                    function onImportResult(result) {
                        importResultLabel.text = "Найдено: " + (result["found"] || 0)
                            + "  •  Добавлено: " + (result["added"] || 0)
                            + "  •  Пропущено: " + (result["skipped"] || 0)
                            + "  •  Дубликатов: " + (result["duplicates"] || 0)
                            + "  •  Ошибок: " + (result["errors"] || 0);
                        importResultLabel.color = (result["errors"] || 0) > 0 ? theme.danger : theme.ok;
                    }
                    function onImportPreviewResult(result) {
                        importResultLabel.text = "Предпросмотр (без записи в базу): найдено "
                            + (result["found"] || 0) + "  •  Добавилось бы: " + (result["added"] || 0)
                            + "  •  Дубликатов: " + (result["duplicates"] || 0);
                        importResultLabel.color = theme.muted;
                    }
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            radius: theme.radius
            color: theme.panel
            border.color: theme.border
            height: 260

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 8

                Label {
                    text: "Слайсеры"
                    color: theme.text
                    font.bold: true
                    font.pixelSize: 16
                }
                Label {
                    text: "Добавьте путь к установленному слайсеру — он появится в меню «Открыть в слайсере»."
                    color: theme.muted
                    font.pixelSize: 11
                    wrapMode: Text.WordWrap
                    Layout.fillWidth: true
                }
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8
                    TextField {
                        id: slicerNameField
                        Layout.preferredWidth: 170
                        placeholderText: "Название"
                        color: theme.text
                        background: Rectangle { color: theme.panelAlt; border.color: theme.border; radius: theme.smallRadius }
                    }
                    TextField {
                        id: slicerPathField
                        Layout.fillWidth: true
                        placeholderText: "C:\\Program Files\\...\\slicer.exe"
                        color: theme.text
                        background: Rectangle { color: theme.panelAlt; border.color: theme.border; radius: theme.smallRadius }
                    }
                    Button { text: "Файл..."; onClicked: slicerFileDialog.open() }
                    Button {
                        text: "Добавить"
                        enabled: slicerPathField.text.length > 0
                        onClicked: {
                            Api.addSlicer(slicerNameField.text, slicerPathField.text);
                            slicerNameField.text = "";
                            slicerPathField.text = "";
                        }
                    }
                }
                Repeater {
                    model: Api.slicers
                    delegate: RowLayout {
                        Layout.fillWidth: true
                        spacing: 8
                        Label {
                            Layout.fillWidth: true
                            text: modelData.name + "  —  " + modelData.path
                            color: theme.muted
                            font.pixelSize: 11
                            elide: Text.ElideMiddle
                        }
                        Button {
                            text: "Удалить"
                            visible: modelData.id.toString().indexOf("custom-") === 0
                            onClicked: Api.removeSlicer(modelData.id)
                        }
                    }
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            radius: theme.radius
            color: theme.panel
            border.color: theme.border

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 20
                spacing: 10
                Label { text: "О приложении"; color: theme.text; font.bold: true; font.pixelSize: 16 }
                Label { text: "MeshVault Desktop 0.1.0 — клиент каталога 3D-моделей"; color: theme.muted; font.pixelSize: 12 }
                Label { text: "Сервер: " + (Api.health["version"] || "—"); color: theme.muted; font.pixelSize: 12 }
                Label {
                    text: "CAD: " + (Api.health["cad"] ? "доступен" : "не найден")
                    color: theme.muted
                    font.pixelSize: 12
                }
                Label {
                    text: "Форматы: " + ((Api.health["formats"] || []).join(", "))
                    color: theme.muted
                    font.pixelSize: 11
                    wrapMode: Text.WordWrap
                    Layout.fillWidth: true
                }
            }
        }
    }

    FolderDialog {
        id: folderDialog
        title: "Выберите каталог моделей"
        onAccepted: importDirField.text = selectedFolder.toString().replace("file:///", "")
    }

    FileDialog {
        id: slicerFileDialog
        title: "Выберите исполняемый файл слайсера"
        nameFilters: ["Исполняемые файлы (*.exe *.appimage)", "Все файлы (*)"]
        onAccepted: slicerPathField.text = selectedFile.toString().replace("file:///", "")
    }

    Component.onCompleted: Api.refreshSlicers()
}
