import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick.Layouts 1.15
import QtQuick.Window 2.15
import MeshVault 1.0

ApplicationWindow {
    id: root
    visible: true
    width: 1440
    height: 900
    minimumWidth: 1024
    minimumHeight: 680
    title: "MeshVault — 3D print library"
    color: theme.bg
    palette.window: theme.bg
    palette.windowText: theme.text
    palette.button: theme.panelAlt
    palette.buttonText: theme.text
    palette.text: theme.text
    palette.base: theme.panel
    palette.highlight: theme.accent

    property string page: "library"
    property string selectedModelId: ""
    property string tagFilter: ""
    property string collectionFilter: ""
    property alias uploadDialog: uploadDialog
    property alias printerDialog: printerDialog
    property alias urlDialog: urlDialog
    property alias shelfDialog: shelfDialog
    property alias profilesDialog: profilesDialog
    property bool sidebarDetached: false

    Theme { id: theme }

    Component.onCompleted: {
        Api.refreshModels();
        Api.refreshPrinters();
        Api.refreshJobs();
        Api.checkHealth();
        Api.getImportConfig();
        Api.refreshMeta();
    }

    function setSidebarFilter(tag, collection) {
        root.tagFilter = tag;
        root.collectionFilter = collection;
        root.navigate("library");
        if (pageLoader.item) {
            pageLoader.item.tagFilter = tag;
            pageLoader.item.collectionFilter = collection;
        }
    }

    function shelfLabel(id) {
        return ({
            library: "Библиотека", upload: "Загрузить", url: "По ссылке",
            refresh: "Обновить", catalog: "Вид каталога", shelf: "⚙ Кнопки",
            printers: "Принтеры", jobs: "Очередь", settings: "Настройки"
        })[id] || id;
    }

    function shelfAction(id) {
        switch (id) {
        case "library": root.navigate("library"); break;
        case "upload": uploadDialog.open(); break;
        case "url": urlDialog.open(); break;
        case "refresh":
            Api.refreshModels();
            Api.refreshPrinters();
            Api.refreshJobs();
            Api.refreshMeta();
            break;
        case "catalog":
            if (pageLoader.item && pageLoader.item.toggleCatalogPanel)
                pageLoader.item.toggleCatalogPanel();
            break;
        case "shelf": shelfDialog.open(); break;
        case "printers": root.navigate("printers"); break;
        case "jobs": root.navigate("jobs"); break;
        case "settings": root.navigate("settings"); break;
        }
    }

    function toggleSidebar() {
        if (root.sidebarDetached) {
            sidebarWindow.hide();
            root.sidebarDetached = false;
            sidebarContent.parent = sidebarHost;
            sidebarContent.anchors.fill = sidebarHost;
            Api.setLayoutValue("sidebarDetached", "0");
        } else {
            root.sidebarDetached = true;
            sidebarContent.parent = sidebarWindow.contentItem;
            sidebarContent.anchors.fill = sidebarWindow.contentItem;
            sidebarWindow.width = 320;
            sidebarWindow.height = root.height;
            sidebarWindow.show();
            Api.setLayoutValue("sidebarDetached", "1");
        }
    }

    Connections {
        target: Api
        function onLayoutChanged() {
            if ((Api.layoutValue("sidebarDetached", "0") === "1") !== root.sidebarDetached)
                root.toggleSidebar();
            shelfRepeater.model = Api.shelfButtons().split(",").filter(function(id) { return id.length > 0; });
        }
    }

    function navigate(target, modelId) {
        if (modelId !== undefined) {
            root.selectedModelId = modelId;
        }
        root.page = target;
    }

    Rectangle {
        id: sidebar
        width: root.sidebarDetached ? 0 : theme.sidebarWidth
        visible: width > 0
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.left: parent.left
        color: theme.panel
        z: 2

        Rectangle {
            anchors.top: parent.top
            anchors.bottom: parent.bottom
            anchors.right: parent.right
            width: 1
            color: theme.border
        }

        Item { id: sidebarHost; anchors.fill: parent }

        Item {
            id: sidebarContent

            Component.onCompleted: {
                sidebarContent.parent = sidebarHost;
                sidebarContent.anchors.fill = sidebarHost;
                if (Api.layoutValue("sidebarDetached", "0") === "1") root.toggleSidebar();
            }

            ColumnLayout {
            anchors.fill: parent
            anchors.margins: 18
            spacing: 22

            RowLayout {
                Layout.fillWidth: true
                spacing: 12

                Rectangle {
                    width: 38
                    height: 38
                    radius: 10
                    color: theme.accent
                    Label {
                        anchors.centerIn: parent
                        text: "M"
                        color: theme.bg
                        font.pixelSize: 20
                        font.bold: true
                    }
                }
                Column {
                    Layout.fillWidth: true
                    Label { text: "MESHVAULT"; color: theme.text; font.bold: true; font.pixelSize: 15 }
                    Label { text: "3D print library"; color: theme.muted; font.pixelSize: 11 }
                }
                Button {
                    text: "⧉"
                    onClicked: root.toggleSidebar()
                }
            }

            ColumnLayout {
                Layout.fillWidth: true
                spacing: 6

                NavButton {
                    text: "Библиотека"
                    icon: "▦"
                    active: root.page === "library"
                    onClicked: root.navigate("library")
                }
                NavButton {
                    text: "Принтеры"
                    icon: "⎙"
                    active: root.page === "printers"
                    onClicked: root.navigate("printers")
                }
                NavButton {
                    text: "Очередь"
                    icon: "⧖"
                    active: root.page === "jobs"
                    onClicked: root.navigate("jobs")
                }
                NavButton {
                    text: "Настройки"
                    icon: "⚙"
                    active: root.page === "settings"
                    onClicked: root.navigate("settings")
                }
            }

            Flickable {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                contentWidth: width
                contentHeight: filters.implicitHeight

                ColumnLayout {
                    id: filters
                    width: parent.width
                    spacing: 6

                    Label {
                        text: "Каталоги"
                        color: theme.muted
                        font.pixelSize: 11
                        font.bold: true
                    }
                    Repeater {
                        model: Api.collections
                        Button {
                            Layout.fillWidth: true
                            text: modelData
                            onClicked: root.setSidebarFilter("", modelData)
                        }
                    }
                    Label {
                        text: "Категории"
                        color: theme.muted
                        font.pixelSize: 11
                        font.bold: true
                    }
                    Repeater {
                        model: Api.categories
                        Button {
                            Layout.fillWidth: true
                            text: modelData
                            onClicked: root.setSidebarFilter(modelData, "")
                        }
                    }
                    Label {
                        text: "Теги"
                        color: theme.muted
                        font.pixelSize: 11
                        font.bold: true
                    }
                    Repeater {
                        model: Api.tags
                        Button {
                            Layout.fillWidth: true
                            text: modelData
                            onClicked: root.setSidebarFilter(modelData, "")
                        }
                    }
                    Button {
                        Layout.fillWidth: true
                        visible: root.tagFilter.length > 0 || root.collectionFilter.length > 0
                        text: "Сбросить фильтры"
                        onClicked: root.setSidebarFilter("", "")
                    }
                }
            }

            Rectangle {
                Layout.fillWidth: true
                radius: theme.smallRadius
                color: theme.panelAlt
                border.color: theme.border
                border.width: 1

                Column {
                    anchors.fill: parent
                    anchors.margins: 12
                    spacing: 4
                    Row {
                        spacing: 6
                        Rectangle {
                            width: 8; height: 8; radius: 4
                            color: Api.health["status"] === "ok" ? theme.ok : theme.danger
                            anchors.verticalCenter: parent.verticalCenter
                        }
                        Label {
                            text: Api.health["status"] === "ok" ? "Сервер онлайн" : "Сервер недоступен"
                            color: theme.text
                            font.pixelSize: 12
                        }
                    }
                    Label {
                        text: Api.serverUrl
                        color: theme.muted
                        font.pixelSize: 10
                        elide: Text.ElideMiddle
                        width: 190
                    }
                }
            }
        }
        }
    }

    Rectangle {
        id: content
        anchors.top: parent.top
        anchors.bottom: parent.bottom
        anchors.left: sidebar.right
        anchors.right: parent.right
        color: theme.bg

        Rectangle {
            id: topbar
            width: parent.width
            height: 68
            color: theme.panel
            z: 1
            Rectangle {
                anchors.bottom: parent.bottom
                anchors.left: parent.left
                anchors.right: parent.right
                height: 1
                color: theme.border
            }
            RowLayout {
                anchors.fill: parent
                anchors.margins: 18
                spacing: 12

                Label {
                    text: root.page === "library" ? "Библиотека" :
                          root.page === "model" ? "Модель" :
                          root.page === "printers" ? "Принтеры" :
                          root.page === "jobs" ? "Очередь печати" : "Настройки"
                    color: theme.text
                    font.pixelSize: 20
                    font.bold: true
                }
                Item { Layout.fillWidth: true }
                Label {
                    text: Api.status
                    color: theme.warn
                    visible: Api.status.length > 0
                    font.pixelSize: 12
                }
                Repeater {
                    id: shelfRepeater
                    model: Api.shelfButtons().split(",").filter(function(id) { return id.length > 0; })
                    Button {
                        text: root.shelfLabel(modelData)
                        onClicked: root.shelfAction(modelData)
                    }
                }
                Button { text: "⚙"; onClicked: shelfDialog.open() }
                Button { text: "Профили"; onClicked: profilesDialog.open() }
            }
        }

        Loader {
            id: pageLoader
            anchors.top: topbar.bottom
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.bottom: parent.bottom
            source: "LibraryPage.qml"
            onLoaded: {
                if (root.page === "model") {
                    item.modelId = root.selectedModelId
                }
                if (item.tagFilter !== undefined) {
                    item.tagFilter = root.tagFilter
                    item.collectionFilter = root.collectionFilter
                }
            }
        }
    }

    UploadDialog { id: uploadDialog }
    PrinterDialog { id: printerDialog }
    UrlImportDialog { id: urlDialog }
    ShelfDialog { id: shelfDialog }
    ProfilesDialog { id: profilesDialog }

    Window {
        id: sidebarWindow
        title: "MeshVault — панель"
        width: 320
        height: 700
        visible: false
        onClosing: {
            close.accepted = false;
            root.toggleSidebar();
        }
    }

    onPageChanged: {
        const sources = {
            library: "LibraryPage.qml",
            model: "ModelPage.qml",
            printers: "PrintersPage.qml",
            jobs: "JobsPage.qml",
            settings: "SettingsPage.qml"
        };
        const source = sources[root.page] || "LibraryPage.qml";
        if (pageLoader.source !== source) {
            pageLoader.source = source;
        }
    }

    component NavButton : Item {
        id: nav
        Layout.fillWidth: true
        Layout.preferredHeight: 46
        property string text: ""
        property string icon: ""
        property bool active: false
        signal clicked()

        Rectangle {
            anchors.fill: parent
            radius: theme.smallRadius
            color: nav.active ? theme.accent : (mouse.containsMouse ? theme.panelAlt : "transparent")
            opacity: nav.active ? 0.9 : 1
        }
        Row {
            anchors.centerIn: parent
            spacing: 10
            Label { text: nav.icon; color: nav.active ? theme.bg : theme.accent; font.pixelSize: 16 }
            Label {
                text: nav.text
                color: nav.active ? theme.bg : (mouse.containsMouse ? theme.text : theme.muted)
                font.pixelSize: 14
                font.bold: nav.active
            }
        }
        MouseArea {
            id: mouse
            anchors.fill: parent
            hoverEnabled: true
            cursorShape: Qt.PointingHandCursor
            onClicked: nav.clicked()
        }
    }
}
