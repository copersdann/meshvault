import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtQuick.Dialogs
import QtQuick.Window
import MeshVault 1.0

Item {
    id: page
    property string modelId: ""

    Theme { id: theme }

    onModelIdChanged: {
        if (modelId.length > 0) Api.openModel(modelId);
    }

    Component.onCompleted: Api.refreshSlicers()

    Connections {
        target: Api
        function onCurrentModelChanged() {
            const versions = Api.currentModel["versions"] || [];
            if (versions.length > 0) {
                const v = versions[0];
                const bx = v["bbox_x"] || 0, by = v["bbox_y"] || 0, bz = v["bbox_z"] || 0;
                viewer.modelSize = Math.max(60, bx, by, bz);
                viewer.modelHeight = Math.max(20, bz);
                viewer.bboxX = bx;
                viewer.bboxY = by;
                viewer.bboxZ = bz;
                Api.fetchPrintability(page.modelId, v["version"], currentPrinterId());
            }
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 14

        RowLayout {
            Layout.fillWidth: true
            Button {
                text: "← Библиотека"
                onClicked: Window.window.navigate("library")
            }
            Label {
                Layout.fillWidth: true
                text: Api.currentModel["name"] || "Модель"
                color: theme.text
                font.pixelSize: 22
                font.bold: true
                elide: Text.ElideRight
            }
            Label {
                text: Api.currentModel["description"] || ""
                color: theme.muted
                visible: false
            }
            Button {
                text: "Экспорт"
                onClicked: exportMenu.open()
                Menu {
                    id: exportMenu
                    MenuItem { text: "STL"; onTriggered: doExport("stl") }
                    MenuItem { text: "OBJ"; onTriggered: doExport("obj") }
                    MenuItem { text: "GLB"; onTriggered: doExport("glb") }
                    MenuItem { text: "PLY"; onTriggered: doExport("ply") }
                    MenuItem { text: "3MF"; onTriggered: doExport("3mf") }
                }
            }
            Button {
                text: "Удалить"
                onClicked: deleteDialog.open()
            }
        }

        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 18

            Rectangle {
                Layout.fillWidth: true
                Layout.fillHeight: true
                radius: theme.radius
                color: theme.panel
                border.color: theme.border
                clip: true

                ViewerSettings { id: viewerSettings }

                ModelViewer3D {
                    id: viewer
                    anchors.fill: parent
                    anchors.margins: 1
                    glbUrl: Api.glbLocalUrl
                    settings: viewerSettings
                }

                Row {
                    anchors.top: parent.top
                    anchors.right: parent.right
                    anchors.margins: 12
                    spacing: 8
                    Button {
                        text: "Кадрировать"
                        onClicked: viewer.frame()
                    }
                }
            }

            Rectangle {
                Layout.preferredWidth: 340
                Layout.fillHeight: true
                radius: theme.radius
                color: theme.panel
                border.color: theme.border

                ScrollView {
                    anchors.fill: parent
                    anchors.margins: 16
                    ColumnLayout {
                        width: parent.width
                        spacing: 12

                        Label { text: "Информация"; color: theme.text; font.bold: true; font.pixelSize: 15 }
                        InfoRow { label: "Формат"; value: latest("source_format").toUpperCase() }
                        InfoRow { label: "Размер"; value: latest("size") ? Math.round(latest("size") / 1024) + " KB" : "—" }
                        InfoRow { label: "Габариты"; value: dims() }
                        InfoRow { label: "Треугольники"; value: (latest("triangles") || 0).toLocaleString() }
                        InfoRow { label: "Объём"; value: latest("volume") ? latest("volume") + " см³" : "—" }
                        InfoRow { label: "Замкнутый"; value: latest("watertight") === true ? "да" : "нет" }

                        Rectangle { Layout.fillWidth: true; height: 1; color: theme.border }

                        Label { text: "Печать"; color: theme.text; font.bold: true; font.pixelSize: 15 }
                        ComboBox {
                            id: printerCombo
                            Layout.fillWidth: true
                            model: printerNames()
                        }
                        Button {
                            Layout.fillWidth: true
                            text: "Прикрепить G-code"
                            onClicked: gcodeDialog.open()
                        }
                        Label {
                            Layout.fillWidth: true
                            text: latest("gcode_filename") ? "G-code: " + latest("gcode_filename") : "G-code не прикреплён"
                            color: theme.muted
                            font.pixelSize: 11
                            wrapMode: Text.WordWrap
                        }
                        Button {
                            Layout.fillWidth: true
                            text: "Начать печать"
                            enabled: Api.printers.length > 0
                            onClicked: {
                                const id = Api.printers[printerCombo.currentIndex]["id"];
                                Api.startPrint(page.modelId, id);
                                Window.window.navigate("jobs");
                            }
                        }

                        Rectangle { Layout.fillWidth: true; height: 1; color: theme.border }

                        Label { text: "Слайсер"; color: theme.text; font.bold: true; font.pixelSize: 15 }
                        ComboBox {
                            id: slicerCombo
                            Layout.fillWidth: true
                            model: slicerNames()
                            enabled: Api.slicers.length > 0
                        }
                        Button {
                            Layout.fillWidth: true
                            text: "Открыть в слайсере"
                            enabled: Api.slicers.length > 0
                            onClicked: Api.openInSlicer(page.modelId, latest("version"), currentSlicerId())
                        }

                        Rectangle { Layout.fillWidth: true; height: 1; color: theme.border }

                        Label { text: "Пригодность к печати"; color: theme.text; font.bold: true; font.pixelSize: 15 }
                        Label {
                            Layout.fillWidth: true
                            visible: (Api.printability["issues"] || []).length === 0
                            text: "Проблем не обнаружено"
                            color: theme.ok
                            font.pixelSize: 12
                        }
                        Repeater {
                            model: Api.printability["issues"] || []
                            Label {
                                Layout.fillWidth: true
                                text: modelData["message"]
                                color: severityColor(modelData["severity"])
                                font.pixelSize: 12
                                wrapMode: Text.WordWrap
                            }
                        }
                        Button {
                            Layout.fillWidth: true
                            text: "Проверить с принтером"
                            onClicked: Api.fetchPrintability(page.modelId, latest("version"), currentPrinterId())
                        }

                        Rectangle { Layout.fillWidth: true; height: 1; color: theme.border }
                        Label { text: "Версии"; color: theme.text; font.bold: true; font.pixelSize: 15 }
                        Repeater {
                            model: Api.currentModel["versions"] || []
                            RowLayout {
                                Layout.fillWidth: true
                                Label {
                                    Layout.fillWidth: true
                                    text: "v" + modelData["version"] + " · " + modelData["source_format"] + " · " + modelData["status"]
                                    color: modelData["status"] === "failed" ? theme.danger : theme.muted
                                    font.pixelSize: 12
                                }
                                Button {
                                    visible: modelData["status"] === "failed"
                                    text: "Повторить"
                                    onClicked: Api.retryVersion(page.modelId, modelData["version"])
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Dialog {
        id: deleteDialog
        title: "Удалить модель?"
        modal: true
        standardButtons: Dialog.Cancel | Dialog.Ok
        onAccepted: {
            Api.deleteModel(page.modelId);
            Window.window.navigate("library");
        }
        Label { text: "Модель будет перемещена в корзину. Её можно восстановить."; color: theme.text }
    }

    FileDialog {
        id: gcodeDialog
        title: "Выберите G-code"
        nameFilters: ["G-code (*.gcode *.bgcode)", "All files (*)"]
        onAccepted: {
            const path = selectedFile.toString().replace("file:///", "");
            const versions = Api.currentModel["versions"] || [];
            if (versions.length > 0) {
                Api.attachGcode(page.modelId, versions[0]["version"], path);
            }
        }
    }

    function latest(key) {
        const versions = Api.currentModel["versions"] || [];
        return versions.length > 0 ? (versions[0][key] || "") : "";
    }

    function dims() {
        const v = latest("bbox_x"), b = latest("bbox_y"), h = latest("bbox_z");
        return v && b && h ? v + " × " + b + " × " + h + " мм" : "—";
    }

    function printerNames() {
        const result = [];
        for (const p of Api.printers) result.push(p["name"]);
        return result;
    }

    function currentPrinterId() {
        if (Api.printers.length > 0 && printerCombo.currentIndex >= 0) {
            return Api.printers[printerCombo.currentIndex]["id"];
        }
        return "";
    }

    function slicerNames() {
        const result = [];
        for (const s of Api.slicers) result.push(s["name"]);
        return result;
    }

    function currentSlicerId() {
        if (Api.slicers.length > 0 && slicerCombo.currentIndex >= 0) {
            return Api.slicers[slicerCombo.currentIndex]["id"];
        }
        return "";
    }

    function doExport(format) {
        Api.exportModel(page.modelId, latest("version"), format);
    }

    function severityColor(severity) {
        return severity === "error" ? theme.danger :
               severity === "warning" ? theme.warn : theme.muted;
    }

    component InfoRow : RowLayout {
        property string label: ""
        property string value: ""
        Layout.fillWidth: true
        Label { text: parent.label; color: theme.muted; font.pixelSize: 12 }
        Item { Layout.fillWidth: true }
        Label { text: parent.value; color: theme.text; font.pixelSize: 12; font.bold: true }
    }
}
