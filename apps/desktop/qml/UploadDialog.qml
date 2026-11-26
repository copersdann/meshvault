import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.15
import QtQuick.Dialogs

Dialog {
    id: dialog
    title: "Загрузить модель"
    modal: true
    standardButtons: Dialog.Cancel | Dialog.Ok
    width: 520
    property string filePath: ""

    Theme { id: theme }

    onAccepted: {
        if (dialog.filePath.length > 0) {
            Api.uploadModel(dialog.filePath, nameField.text);
            dialog.filePath = "";
            nameField.text = "";
        }
    }

    contentItem: ColumnLayout {
        spacing: 14

        Label {
            text: "Поддерживаются STL, 3MF, OBJ, GLB, PLY, STEP и G-code"
            color: theme.muted
            font.pixelSize: 12
            wrapMode: Text.WordWrap
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 10

            TextField {
                id: pathField
                Layout.fillWidth: true
                text: dialog.filePath
                readOnly: true
                placeholderText: "Файл не выбран"
                color: theme.text
                background: Rectangle {
                    color: theme.panelAlt
                    border.color: theme.border
                    radius: theme.smallRadius
                }
            }
            Button {
                text: "Выбрать..."
                onClicked: fileDialog.open()
            }
        }

        TextField {
            id: nameField
            Layout.fillWidth: true
            placeholderText: "Название (необязательно)"
            color: theme.text
            background: Rectangle {
                color: theme.panelAlt
                border.color: theme.border
                radius: theme.smallRadius
            }
        }
    }

    FileDialog {
        id: fileDialog
        title: "Выберите файл модели"
        nameFilters: [
            "3D models (*.stl *.obj *.3mf *.glb *.gltf *.ply *.step *.stp *.amf *.gcode)",
            "All files (*)"
        ]
        onAccepted: dialog.filePath = selectedFile.toString().replace("file:///", "")
    }
}
