import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
import MeshVault 1.0

Dialog {
    id: dialog
    title: "Профили рабочего пространства"
    modal: true
    standardButtons: Dialog.Close
    width: 420
    height: 260

    Theme { id: theme }

    ColumnLayout {
        anchors.fill: parent
        spacing: 10

        Label {
            text: "Профиль запоминает панели, кнопки, сортировку и вид каталога."
            color: theme.muted
            font.pixelSize: 11
            wrapMode: Text.WordWrap
            Layout.fillWidth: true
        }

        ComboBox {
            id: profileBox
            Layout.fillWidth: true
            model: Api.layoutProfiles()
            Component.onCompleted: dialog.reload()
        }

        TextField {
            id: nameField
            Layout.fillWidth: true
            placeholderText: "Имя нового профиля"
            color: theme.text
            placeholderTextColor: theme.muted
        }

        RowLayout {
            Layout.fillWidth: true
            Button {
                text: "Сохранить"
                onClicked: {
                    Api.saveLayoutProfile(nameField.text);
                    dialog.reload();
                    profileBox.currentIndex = profileBox.count - 1;
                }
            }
            Button {
                text: "Загрузить"
                enabled: profileBox.currentText.length > 0
                onClicked: {
                    Api.loadLayoutProfile(profileBox.currentText);
                    dialog.accept();
                }
            }
        }
    }

    function reload() {
        profileBox.model = Api.layoutProfiles();
    }
}
