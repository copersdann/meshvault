import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import MeshVault 1.0

Item {
    id: page
    Theme { id: theme }

    Component.onCompleted: Api.refreshJobs()

    Timer {
        interval: 3000
        running: true
        repeat: true
        onTriggered: Api.refreshJobs()
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 12

        Label { text: "История и активные задания"; color: theme.text; font.pixelSize: 16; font.bold: true }

        ListView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            model: Api.jobs
            delegate: JobRow { item: modelData }
        }

        Label {
            Layout.fillWidth: true
            visible: Api.jobs.length === 0
            text: "Заданий пока нет. Откройте модель и нажмите «Начать печать»."
            color: theme.muted
            horizontalAlignment: Text.AlignHCenter
            font.pixelSize: 13
        }
    }
}
