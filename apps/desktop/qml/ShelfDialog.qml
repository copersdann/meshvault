import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Window
import MeshVault 1.0

Dialog {
    id: dialog
    title: "Настройка панели кнопок"
    modal: true
    standardButtons: Dialog.Cancel | Dialog.Ok
    width: 460
    height: 420

    Theme { id: theme }

    function label(id) {
        return ({
            library: "Библиотека", upload: "Загрузить", url: "По ссылке",
            refresh: "Обновить", catalog: "Вид каталога", shelf: "Настройка кнопок",
            printers: "Принтеры", jobs: "Очередь", settings: "Настройки"
        })[id] || id;
    }

    function commit() {
        const ids = [];
        for (let i = 0; i < shelfModel.count; ++i) {
            const item = shelfModel.get(i);
            if (item.enabled) ids.push(item.id);
        }
        Api.setShelfButtons(ids.join(","));
    }

    Component.onCompleted: {
        const available = ["library", "upload", "url", "refresh", "catalog",
                           "printers", "jobs", "settings", "shelf"];
        const active = Api.shelfButtons().split(",").filter(function(value) { return value.length > 0; });
        shelfModel.clear();
        available.forEach(function(id) {
            shelfModel.append({ id: id, label: dialog.label(id), enabled: active.indexOf(id) >= 0 });
        });
    }

    onAccepted: dialog.commit()

    ListModel { id: shelfModel }

    ColumnLayout {
        anchors.fill: parent
        spacing: 10

        Label {
            text: "Включите кнопки и измените порядок стрелками — как shelf в Maya."
            color: theme.muted
            font.pixelSize: 11
            wrapMode: Text.WordWrap
            Layout.fillWidth: true
        }

        ListView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            model: shelfModel
            delegate: RowLayout {
                width: ListView.view.width
                spacing: 8
                CheckBox {
                    text: model.label
                    checked: model.enabled
                    onCheckedChanged: shelfModel.set(index, { enabled: checked })
                    Layout.fillWidth: true
                }
                Button {
                    text: "↑"
                    enabled: index > 0
                    onClicked: shelfModel.move(index, index - 1, 1)
                }
                Button {
                    text: "↓"
                    enabled: index < shelfModel.count - 1
                    onClicked: shelfModel.move(index, index + 1, 1)
                }
            }
        }
    }
}
