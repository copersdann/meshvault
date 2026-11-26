cmake_minimum_required(VERSION 3.21)

project(MeshVaultDesktop LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)
set(CMAKE_AUTORCC ON)

find_package(Qt6 REQUIRED COMPONENTS
    Core
    Gui
    Qml
    Quick
    QuickControls2
    Quick3D
    Quick3DHelpers
    QuickDialogs2
    Network
)

qt_standard_project_setup()

qt_add_executable(MeshVaultDesktop
    src/main.cpp
    src/ApiClient.h
    src/ApiClient.cpp
    src/GlbParser.h
    src/GlbParser.cpp
    src/MeshGeometry.h
    src/MeshGeometry.cpp
    src/ViewerDecorGeometry.h
    src/ViewerDecorGeometry.cpp
    qml/qml.qrc
)

target_include_directories(MeshVaultDesktop PRIVATE src)

target_link_libraries(MeshVaultDesktop PRIVATE
    Qt6::Core
    Qt6::Gui
    Qt6::Qml
    Qt6::Quick
    Qt6::QuickControls2
    Qt6::Quick3D
    Qt6::Quick3DHelpers
    Qt6::QuickDialogs2
    Qt6::Network
)

set_target_properties(MeshVaultDesktop PROPERTIES
    WIN32_EXECUTABLE ON
    QT_QML_MODULE_NO_IMPORT_SCAN TRUE
)
