import QtQuick 2.15

/// Состояние настроек вьювера (без UI и без бизнес-логики).
QtObject {
    property bool showGrid: true
    property int gridDivisions: 10
    property real gridScale: 2.0
    property color gridColor: "#44546a"
    property real gridOpacity: 0.55

    property bool showAxes: true
    property real axesScale: 0.75
    property color axisXColor: "#f2474d"
    property color axisYColor: "#52e56b"
    property color axisZColor: "#5c8cff"

    property bool lit: true
    property bool aoEnabled: true
    property bool shadowsEnabled: true
    property bool flatShading: true
    property bool showDimensions: true
    property real shadowStrength: 0.75
    property real shadowBias: 0.002
    property color floorColor: "#0f1720"
    property bool wireframe: false
    property real roughness: 0.5
    property real metalness: 0.05
    property real specular: 0.35
    property color modelColor: "#c9d6e5"

    property real keyLight: 1.2
    property real fillLight: 0.45
    property real rimLight: 0.25
    property color backgroundColor: "#151d27"

    property real fov: 45
    property real navSpeed: 0.12
    property real autoRotateSpeed: 0.25
    property bool autoRotate: false
    property bool orthographic: false
    property real orthoZoom: 1.0
    property real clipFarScale: 20
    property bool showStatus: true

    signal resetRequested()

    function reset() {
        showGrid = true; gridDivisions = 10; gridScale = 2.0;
        gridColor = "#44546a"; gridOpacity = 0.55;
        showAxes = true; axesScale = 0.75;
        axisXColor = "#f2474d"; axisYColor = "#52e56b"; axisZColor = "#5c8cff";
        lit = true; aoEnabled = true; shadowsEnabled = true;
        flatShading = true; showDimensions = true;
        shadowStrength = 0.75; shadowBias = 0.002; floorColor = "#0f1720";
        wireframe = false;
        roughness = 0.5; metalness = 0.05; specular = 0.35; modelColor = "#c9d6e5";
        keyLight = 1.2; fillLight = 0.45; rimLight = 0.25; backgroundColor = "#151d27";
        fov = 45; navSpeed = 0.12; autoRotateSpeed = 0.25; autoRotate = false;
        orthographic = false; orthoZoom = 1.0; clipFarScale = 20; showStatus = true;
        resetRequested();
    }
}
