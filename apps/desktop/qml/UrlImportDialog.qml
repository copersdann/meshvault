import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
import MeshVault 1.0

Dialog {
    id: dialog
    title: "Импорт модели по ссылке"
    modal: true
    standardButtons: Dialog.Close
    width: 560

    Theme { id: theme }

    ColumnLayout {
        anchors.fill: parent
        spacing: 12

        Label {
            text: "Вставьте прямую ссылку на STL, 3MF, OBJ, GLB, PLY, STEP или ZIP. "
                + "Дубликаты по SHA-256 не добавляются."
            color: theme.muted
            font.pixelSize: 11
            wrapMode: Text.WordWrap
            Layout.fillWidth: true
        }

        RowLayout {
            Layout.fillWidth: true
            spacing: 8
            TextField {
                id: urlField
                Layout.fillWidth: true
                placeholderText: "https://example.com/model.stl"
                color: theme.text
                placeholderTextColor: theme.muted
                onAccepted: Api.importUrl(text)
                background: Rectangle {
                    color: theme.panelAlt
                    border.color: theme.border
                    radius: theme.smallRadius
                }
            }
            Button { text: "Загрузить"; onClicked: Api.importUrl(urlField.text) }
        }
    }
}
