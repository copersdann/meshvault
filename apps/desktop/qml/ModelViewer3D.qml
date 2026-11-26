import QtQuick 2.15
import QtQuick.Controls 2.15
import QtQuick3D
import QtQuick3D.Helpers
import MeshVault 1.0

/// 3D-слой: только сцена и камера. Всё состояние приходит через settings.
Item {
    id: viewer
    property string glbUrl: ""
    property real modelSize: 120
    property real modelHeight: 100
    property real bboxX: 0
    property real bboxY: 0
    property real bboxZ: 0
    property QtObject settings: null
    property string errorText: ""

    Theme { id: theme }

    Component.onCompleted: {
        if (glbUrl.length > 0) meshGeometry.source = glbUrl;
        Qt.callLater(frame);
    }
    onGlbUrlChanged: {
        if (glbUrl.length > 0) {
            errorText = "";
            meshGeometry.source = glbUrl;
            Qt.callLater(frame);
        }
    }
    onModelSizeChanged: Qt.callLater(frame)

    View3D {
        id: view3d
        anchors.fill: parent
        camera: settings && settings.orthographic ? orthoCamera : perspectiveCamera

        environment: SceneEnvironment {
            backgroundMode: SceneEnvironment.Color
            clearColor: settings ? settings.backgroundColor : "#151d27"
            antialiasingMode: SceneEnvironment.MSAA
            antialiasingQuality: SceneEnvironment.High
            specularAAEnabled: true
            tonemapMode: SceneEnvironment.TonemapModeAces
            aoEnabled: settings ? settings.aoEnabled : true
            aoStrength: 45
            aoDistance: Math.max(5, viewer.modelSize * 0.2)
            aoSoftness: 35
            aoDither: true
            debugSettings: DebugSettings {
                wireframeEnabled: settings ? settings.wireframe : false
            }
        }

        Node {
            id: originNode
            PerspectiveCamera {
                id: perspectiveCamera
                z: 150
                clipNear: 1
                clipFar: Math.max(5000, viewer.modelSize * (settings ? settings.clipFarScale : 20))
                fieldOfView: settings ? settings.fov : 45
            }
            OrthographicCamera {
                id: orthoCamera
                visible: settings && settings.orthographic
                z: 150
                clipNear: 1
                clipFar: Math.max(5000, viewer.modelSize * (settings ? settings.clipFarScale : 20))
                horizontalMagnification: settings ? settings.orthoZoom : 1
                verticalMagnification: settings ? settings.orthoZoom : 1
            }
        }

        OrbitCameraController {
            anchors.fill: parent
            origin: originNode
            camera: settings && settings.orthographic ? orthoCamera : perspectiveCamera
            panEnabled: true
            xSpeed: settings ? settings.navSpeed : 0.12
            ySpeed: settings ? settings.navSpeed : 0.12
        }

        Timer {
            interval: 16
            repeat: true
            running: settings && settings.autoRotate
            onTriggered: originNode.eulerRotation.y += (settings ? settings.autoRotateSpeed : 0.25)
        }

        DirectionalLight {
            eulerRotation.x: -50
            eulerRotation.y: -30
            brightness: settings ? settings.keyLight : 1.2
            color: "#ffffff"
            castsShadow: settings ? settings.shadowsEnabled : true
            shadowFactor: settings ? settings.shadowStrength : 0.75
            shadowBias: settings ? settings.shadowBias : 0.002
        }
        DirectionalLight {
            eulerRotation.x: -15
            eulerRotation.y: 120
            brightness: settings ? settings.fillLight : 0.45
            color: "#9fc0ff"
        }
        DirectionalLight {
            eulerRotation.x: 60
            eulerRotation.y: 45
            brightness: settings ? settings.rimLight : 0.25
            color: "#ffd8a8"
        }

        Model {
            id: mesh
            visible: viewer.glbUrl.length > 0
            eulerRotation.x: -90
            castsShadows: settings ? settings.shadowsEnabled : true
            receivesShadows: settings ? settings.shadowsEnabled : true
            geometry: MeshGeometry {
                id: meshGeometry
                flatShading: settings ? settings.flatShading : true
                onLoadFailed: viewer.errorText = message
                onLoadedChanged: { if (loaded) { refresh(); Qt.callLater(frame); } }
            }
            materials: [
                PrincipledMaterial {
                    baseColor: settings ? settings.modelColor : "#c9d6e5"
                    metalness: settings ? settings.metalness : 0.05
                    roughness: settings ? settings.roughness : 0.5
                    specularAmount: settings ? settings.specular : 0.35
                    cullMode: Material.BackFaceCulling
                    vertexColorsEnabled: false
                    lighting: (settings && settings.lit) ? PrincipledMaterial.FragmentLighting
                                                         : PrincipledMaterial.NoLighting
                }
            ]
        }

        Model {
            id: floor
            visible: settings && settings.showGrid && viewer.glbUrl.length > 0
            source: "#Rectangle"
            eulerRotation.x: -90
            position: Qt.vector3d(0, -viewer.modelHeight / 2 - 2.6, 0)
            scale: Qt.vector3d(viewer.modelSize * (settings ? settings.gridScale : 2) / 100,
                               viewer.modelSize * (settings ? settings.gridScale : 2) / 100, 1)
            receivesShadows: true
            materials: [PrincipledMaterial {
                baseColor: settings ? settings.floorColor : "#0f1720"
                roughness: 0.95
                metalness: 0
            }]
        }

        Model {
            visible: settings && settings.showGrid && viewer.glbUrl.length > 0
            opacity: settings ? settings.gridOpacity : 0.55
            position: Qt.vector3d(0, -viewer.modelHeight / 2 - 2, 0)
            geometry: ViewerDecorGeometry {
                extent: viewer.modelSize * (settings ? settings.gridScale : 2)
                mode: 0
                divisions: settings ? settings.gridDivisions : 10
            }
            materials: [PrincipledMaterial {
                lighting: PrincipledMaterial.NoLighting
                vertexColorsEnabled: true
                baseColor: settings ? settings.gridColor : "#44546a"
            }]
        }

        Model {
            visible: settings && settings.showAxes && viewer.glbUrl.length > 0
            position: Qt.vector3d(0, -viewer.modelHeight / 2 - 2, 0)
            scale: Qt.vector3d(1, settings ? settings.axesScale : 0.75, 1)
            geometry: ViewerDecorGeometry {
                extent: viewer.modelSize * (settings ? settings.gridScale : 2)
                mode: 1
            }
            materials: [PrincipledMaterial {
                lighting: PrincipledMaterial.NoLighting
                vertexColorsEnabled: true
                lineWidth: 2
            }]
        }

        Model {
            visible: settings && settings.showDimensions && viewer.glbUrl.length > 0
            geometry: ViewerDecorGeometry {
                mode: 2
                extent: viewer.modelSize * 2
                boxSize: Qt.vector3d(viewer.bboxX, viewer.bboxZ, viewer.bboxY)
            }
            materials: [PrincipledMaterial {
                lighting: PrincipledMaterial.NoLighting
                vertexColorsEnabled: true
                lineWidth: 2
            }]
        }
    }

    Button {
        id: settingsButton
        anchors.top: parent.top
        anchors.left: parent.left
        anchors.margins: 8
        text: "⚙ Вид"
        checkable: true
        checked: controlsPanel.visible
        onClicked: controlsPanel.visible = !controlsPanel.visible
        background: Rectangle {
            radius: theme.smallRadius
            color: settingsButton.hovered ? theme.accentSoft : theme.glassStrong
            border.color: theme.glassBorder
            border.width: 1
        }
        contentItem: Label {
            text: settingsButton.text
            color: theme.text
            font.pixelSize: 11
            horizontalAlignment: Text.AlignHCenter
            verticalAlignment: Text.AlignVCenter
        }
    }

    ViewerControls {
        id: controlsPanel
        anchors.top: parent.top
        anchors.right: parent.right
        anchors.margins: 8
        anchors.topMargin: 8
        visible: false
        settings: viewer.settings
        viewer: viewer
    }

    Rectangle {
        anchors.bottom: parent.bottom
        anchors.left: parent.left
        anchors.margins: 10
        visible: settings && settings.showDimensions && viewer.glbUrl.length > 0
        radius: theme.smallRadius
        color: theme.glassStrong
        border.color: theme.glassBorder
        border.width: 1
        Label {
            anchors.margins: 8
            text: "X " + viewer.bboxX.toFixed(1) + "  •  Y " + viewer.bboxY.toFixed(1)
                  + "  •  Z " + viewer.bboxZ.toFixed(1) + " мм"
            color: theme.text
            font.pixelSize: 11
        }
    }

    Rectangle {
        anchors.centerIn: parent
        width: 320
        height: 56
        visible: settings && settings.showStatus &&
                 (viewer.glbUrl.length === 0 || viewer.errorText.length > 0)
        radius: theme.radius
        color: theme.glassStrong
        border.color: theme.glassBorder
        Label {
            anchors.centerIn: parent
            text: viewer.errorText.length > 0 ? viewer.errorText : "Нет доступного GLB-превью"
            color: theme.muted
            font.pixelSize: 13
        }
    }

    function frame() {
        const radius = Math.max(viewer.modelSize, viewer.modelHeight) * 0.75;
        originNode.position = Qt.vector3d(0, 0, 0);
        perspectiveCamera.z = Math.max(150,
            radius / Math.tan((settings ? settings.fov : 45) * Math.PI / 360) * 1.3);
        orthoCamera.z = perspectiveCamera.z;
        setView("iso");
    }

    function setView(preset) {
        if (preset === "top") originNode.eulerRotation = Qt.vector3d(-90, 0, 0);
        else if (preset === "front") originNode.eulerRotation = Qt.vector3d(0, 0, 0);
        else if (preset === "right") originNode.eulerRotation = Qt.vector3d(0, -90, 0);
        else if (preset === "bottom") originNode.eulerRotation = Qt.vector3d(90, 0, 0);
        else originNode.eulerRotation = Qt.vector3d(-25, -35, 0);
    }
}
