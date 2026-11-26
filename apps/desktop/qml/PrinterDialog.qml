import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.15
import MeshVault 1.0

Dialog {
    id: dialog
    title: "Добавить принтер"
    modal: true
    standardButtons: Dialog.Cancel | Dialog.Ok
    width: 540

    Theme { id: theme }

    property string probeMessage: ""

    Connections {
        target: Api
        function onProbeResult(ok, message) {
            dialog.probeMessage = (ok ? "✓ " : "✗ ") + message;
        }
    }

    onAccepted: {
        Api.addPrinter(nameField.text, hostField.text, keyField.text,
                       bedX.value, bedY.value);
        dialog.probeMessage = "";
    }

    contentItem: ColumnLayout {
        spacing: 12

        TextField {
            id: nameField
            Layout.fillWidth: true
            placeholderText: "Название (например, Voron 2.4)"
            color: theme.text
            background: Rectangle {
                color: theme.panelAlt
                border.color: theme.border
                radius: theme.smallRadius
            }
        }
        TextField {
            id: hostField
            Layout.fillWidth: true
            placeholderText: "Адрес Moonraker, например 192.168.1.50:7125"
            color: theme.text
            background: Rectangle {
                color: theme.panelAlt
                border.color: theme.border
                radius: theme.smallRadius
            }
        }
        TextField {
            id: keyField
            Layout.fillWidth: true
            placeholderText: "API ключ (необязательно)"
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
            Label { text: "Стол, мм:"; color: theme.muted; font.pixelSize: 12 }
            SpinBox {
                id: bedX
                from: 50; to: 600; value: 220
                editable: true
            }
            Label { text: "×"; color: theme.muted }
            SpinBox {
                id: bedY
                from: 50; to: 600; value: 220
                editable: true
            }
            Button {
                text: "Проверить"
                onClicked: Api.probePrinter(hostField.text, keyField.text)
            }
        }
        Label {
            Layout.fillWidth: true
            text: dialog.probeMessage
            color: dialog.probeMessage.startsWith("✓") ? theme.ok :
                   dialog.probeMessage.length > 0 ? theme.danger : theme.muted
            font.pixelSize: 12
            wrapMode: Text.WordWrap
        }
    }
}
