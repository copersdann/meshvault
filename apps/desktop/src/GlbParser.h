#pragma once

#include <QByteArray>
#include <QVector3D>

/// Результат разбора GLB: готовые interleaved-вершины и индексы.
struct GlbMeshData
{
    QByteArray vertices;   // position(3 float) + normal(3 float), stride 24
    QByteArray indices;    // uint32
    QVector3D boundsMin;
    QVector3D boundsMax;
    int vertexCount = 0;
    int indexCount = 0;

    bool isValid() const { return vertexCount > 0 && indexCount > 0; }
};

/// Чистая бизнес-логика: разбор GLB без привязки к Qt Quick 3D.
class GlbParser
{
public:
    static bool parse(const QByteArray &bytes, GlbMeshData *out,
                      QString *error = nullptr, bool flatShading = false);
};
