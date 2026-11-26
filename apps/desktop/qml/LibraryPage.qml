import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.15
import QtQuick.Window 2.15
import MeshVault 1.0

Item {
    id: page
    Theme { id: theme }

    property string search: ""
    property var filteredModels: []
    property bool trashMode: false
    property string formatFilter: ""
    property int cardWidth: 260
    property bool showPreview: true
    property bool compact: false
    property bool showStats: true
    property string tagFilter: ""
    property string collectionFilter: ""
    property string localSort: ""
    property bool catalogDetached: false

    onSearchChanged: page.applyFilter()
    onTagFilterChanged: page.applyFilter()
    onCollectionFilterChanged: page.applyFilter()
    onLocalSortChanged: {
        Api.setLayoutValue("sort", page.localSort || Api.sortOrder);
        page.applyFilter();
    }
    onCardWidthChanged: Api.setLayoutValue("cardWidth", String(page.cardWidth))
    onCompactChanged: Api.setLayoutValue("compact", page.compact ? "1" : "0")
    onShowPreviewChanged: Api.setLayoutValue("showPreview", page.showPreview ? "1" : "0")
    onShowStatsChanged: Api.setLayoutValue("showStats", page.showStats ? "1" : "0")

    function applyFilter() {
        const wanted = statusMap[statusFilter.currentText] || "";
        const result = [];
        for (const item of Api.models) {
            const statusOk = !wanted || item["status"] === wanted;
            const formatOk = !formatFilter || item["format"] === formatFilter;
            const tagOk = !page.tagFilter || (item["tags"] || []).indexOf(page.tagFilter) >= 0;
            const collectionOk = !page.collectionFilter
                || (item["collections"] || []).indexOf(page.collectionFilter) >= 0;
            if (statusOk && formatOk && tagOk && collectionOk) result.push(item);
        }
        if (page.localSort === "size_desc") {
            result.sort(function(a, b) { return (b["size"] || 0) - (a["size"] || 0); });
        } else if (page.localSort === "name_desc") {
            result.sort(function(a, b) { return String(b["name"]).localeCompare(String(a["name"])); });
        } else if (page.localSort === "status") {
            result.sort(function(a, b) { return String(a["status"]).localeCompare(String(b["status"])); });
        } else if (page.localSort === "date_asc") {
            result.sort(function(a, b) { return String(a["createdAt"]).localeCompare(String(b["createdAt"])); });
        }
        page.filteredModels = result;
    }

    function resetViewSettings() {
        cardWidth = 260;
        showPreview = true;
        compact = false;
        showStats = true;
        formatFilter = "";
        tagFilter = "";
        collectionFilter = "";
        Api.catalogLimit = 200;
    }

    function formatBytes(value) {
        if (value < 1024) return value + " B";
        if (value < 1024 * 1024) return (value / 1024).toFixed(1) + " KB";
        return (value / 1024 / 1024).toFixed(1) + " MB";
    }

    readonly property var statusMap: ({
        "Все": "", "Готово": "ready", "Обработка": "processing", "Ошибка": "failed"
    })

    Connections {
        target: Api
        function onModelsChanged() { page.applyFilter() }
        function onOperationFinished() { page.applyFilter() }
    }

    Component.onCompleted: {
        cardWidth = parseInt(Api.layoutValue("cardWidth", "260"), 10) || 260;
        compact = Api.layoutValue("compact", "0") === "1";
        showPreview = Api.layoutValue("showPreview", "1") !== "0";
        showStats = Api.layoutValue("showStats", "1") !== "0";
        const savedSort = Api.layoutValue("sort", "created_desc");
        if (["size_desc", "name_desc", "status", "date_asc"].indexOf(savedSort) >= 0) {
            page.localSort = savedSort;
        } else {
            for (let i = 0; i < sortBox.model.length; ++i) {
                if (sortBox.model[i].value === savedSort) sortBox.currentIndex = i;
            }
            Api.sortOrder = sortBox.model[sortBox.currentIndex].value;
        }
        if (Api.layoutValue("catalogDetached", "0") === "1") page.toggleCatalogPanel();
        page.applyFilter();
    }

    Connections {
        target: Api
        function onLayoutChanged() {
            if ((Api.layoutValue("catalogDetached", "0") === "1") !== page.catalogDetached)
                page.toggleCatalogPanel();
        }
    }

    function toggleCatalogPanel() {
        if (page.catalogDetached) {
            catalogWindow.hide();
            page.catalogDetached = false;
            catalogPanel.parent = page;
            catalogPanel.anchors.fill = undefined;
            catalogPanel.anchors.top = page.top;
            catalogPanel.anchors.right = page.right;
            catalogPanel.anchors.margins = 12;
            Api.setLayoutValue("catalogDetached", "0");
        } else {
            page.catalogDetached = true;
            catalogPanel.visible = true;
            catalogPanel.parent = catalogWindow.contentItem;
            catalogPanel.anchors.top = undefined;
            catalogPanel.anchors.right = undefined;
            catalogPanel.anchors.margins = 8;
            catalogPanel.anchors.fill = catalogWindow.contentItem;
            catalogWindow.width = 320;
            catalogWindow.height = 460;
            catalogWindow.show();
            Api.setLayoutValue("catalogDetached", "1");
        }
    }

    Timer {
        id: searchTimer
        interval: 250
        repeat: false
        onTriggered: {
            page.search = searchField.text;
            Api.searchQuery = searchField.text;
        }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        RowLayout {
            Layout.fillWidth: true
            spacing: 12

            TextField {
                id: searchField
                Layout.preferredWidth: 360
                placeholderText: "Поиск по названию..."
                color: theme.text
                placeholderTextColor: theme.muted
                onTextChanged: searchTimer.restart()
                background: Rectangle {
                    color: theme.panel
                    border.color: theme.border
                    radius: theme.smallRadius
                }
            }

            ComboBox {
                id: statusFilter
                Layout.preferredWidth: 170
                model: ["Все", "Готово", "Обработка", "Ошибка"]
                onActivated: page.applyFilter()
            }

            ComboBox {
                id: sortBox
                Layout.preferredWidth: 190
                textRole: "text"
                model: [
                    { text: "Сначала новые", value: "created_desc" },
                    { text: "Недавно обновлённые", value: "updated_desc" },
                    { text: "По названию (А–Я)", value: "name_asc" },
                    { text: "По названию (Я–А)", value: "local:name_desc" },
                    { text: "По размеру (крупные)", value: "local:size_desc" },
                    { text: "По статусу", value: "local:status" },
                    { text: "Сначала старые", value: "local:date_asc" }
                ]
                onActivated: {
                    const value = model[index].value;
                    if (value.indexOf("local:") === 0) {
                        page.localSort = value.substring(6);
                    } else {
                        page.localSort = "";
                        Api.sortOrder = value;
                    }
                }
            }

            Button {
                text: page.trashMode ? "← Библиотека" : "Корзина"
                onClicked: {
                    page.trashMode = !page.trashMode;
                    Api.refreshModels(page.trashMode);
                }
            }

            Button {
                text: "⚙ Вид каталога"
                checkable: true
                checked: catalogPanel.visible
                onClicked: catalogPanel.visible = !catalogPanel.visible
            }

            Item { Layout.fillWidth: true }

            Label {
                text: page.filteredModels.length + " из " + Api.modelsTotal
                color: theme.muted
                font.pixelSize: 13
            }
        }

        RowLayout {
            Layout.fillWidth: true
            visible: page.showStats
            spacing: 18
            Label { text: "Всего: " + Api.modelsTotal; color: theme.text; font.pixelSize: 11 }
            Label { text: "Готово: " + (Api.modelStats["ready"] || 0); color: theme.ok; font.pixelSize: 11 }
            Label { text: "В обработке: " + (Api.modelStats["processing"] || 0); color: theme.warn; font.pixelSize: 11 }
            Label { text: "Ошибок: " + (Api.modelStats["failed"] || 0); color: theme.danger; font.pixelSize: 11 }
            Label { text: "Объём: " + page.formatBytes(Api.modelStats["size"] || 0); color: theme.muted; font.pixelSize: 11 }
            Item { Layout.fillWidth: true }
            Label {
                text: "Сервер v" + (Api.health["version"] || "—")
                color: theme.muted
                font.pixelSize: 11
            }
        }

        DropArea {
            id: dropArea
            Layout.fillWidth: true
            Layout.fillHeight: true

            onDropped: {
                const paths = [];
                for (const u of drag.urls) {
                    paths.push(decodeURIComponent(u.toString().replace(/^file:\/\/\//, "")));
                }
                if (paths.length > 0) {
                    Api.uploadFiles(paths);
                }
            }

            Rectangle {
                anchors.fill: parent
                radius: theme.radius
                color: "transparent"
                border.width: dropArea.containsDrag ? 2 : 0
                border.color: theme.accent
            }

            GridView {
                id: grid
                anchors.fill: parent
                cellWidth: page.cardWidth + 28
                cellHeight: page.compact ? 200 : page.cardWidth + 96
                clip: true
                model: page.filteredModels
                delegate: ModelCard {
                    width: grid.cellWidth - 24
                    height: grid.cellHeight - 22
                    item: modelData
                    trashMode: page.trashMode
                    showPreview: page.showPreview
                    compact: page.compact
                }
            }

            Label {
                anchors.centerIn: parent
                visible: page.filteredModels.length === 0
                text: page.trashMode ? "Корзина пуста." :
                      "Библиотека пуста. Перетащите STL, 3MF, OBJ или GLB файлы сюда."
                color: theme.muted
                horizontalAlignment: Text.AlignHCenter
                font.pixelSize: 15
            }
        }
    }

    CatalogSettings {
        id: catalogPanel
        anchors.top: parent.top
        anchors.right: parent.right
        anchors.margins: 12
        visible: false
        page: page
        onDetachRequested: page.toggleCatalogPanel()
    }

    Window {
        id: catalogWindow
        title: "MeshVault — вид каталога"
        width: 320
        height: 460
        visible: false
        onClosing: {
            close.accepted = false;
            page.toggleCatalogPanel();
        }
    }
}
