#include "MeshGeometry.h"

#include "GlbParser.h"

#include <QFile>

QUrl MeshGeometry::source() const
{
    return m_source;
}

bool MeshGeometry::loaded() const
{
    return m_loaded;
}

bool MeshGeometry::flatShading() const
{
    return m_flatShading;
}

void MeshGeometry::setFlatShading(bool value)
{
    if (m_flatShading == value)
        return;
    m_flatShading = value;
    emit flatShadingChanged();
    if (!m_bytes.isEmpty()) {
        QString error;
        const bool ok = loadGlb(QString(), &error);
        if (!ok)
            emit loadFailed(error);
    }
}

void MeshGeometry::setSource(const QUrl &value)
{
    if (m_source == value)
        return;
    m_source = value;
    emit sourceChanged();

    QString error;
    const bool ok = loadGlb(value.toLocalFile(), &error);
    if (!ok)
        emit loadFailed(error.isEmpty() ? QStringLiteral("Не удалось прочитать GLB") : error);
    if (m_loaded != ok) {
        m_loaded = ok;
        emit loadedChanged();
    }
}

bool MeshGeometry::loadGlb(const QString &path, QString *error)
{
    if (!path.isEmpty()) {
        QFile file(path);
        if (!file.open(QIODevice::ReadOnly)) {
            if (error)
                *error = file.errorString();
            return false;
        }
        m_bytes = file.readAll();
        file.close();
    }

    GlbMeshData data;
    if (!GlbParser::parse(m_bytes, &data, error, m_flatShading))
        return false;

    clear();
    setVertexData(data.vertices);
    setIndexData(data.indices);
    setStride(24);
    addAttribute(QQuick3DGeometry::Attribute::PositionSemantic, 0,
                 QQuick3DGeometry::Attribute::F32Type);
    addAttribute(QQuick3DGeometry::Attribute::NormalSemantic, 12,
                 QQuick3DGeometry::Attribute::F32Type);
    addAttribute(QQuick3DGeometry::Attribute::IndexSemantic, 0,
                 QQuick3DGeometry::Attribute::U32Type);
    setPrimitiveType(QQuick3DGeometry::PrimitiveType::Triangles);
    setBounds(data.boundsMin, data.boundsMax);
    update();
    return true;
}
