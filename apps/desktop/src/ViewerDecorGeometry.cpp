#include "ViewerDecorGeometry.h"

#include <QVector3D>

#include <cmath>

ViewerDecorGeometry::ViewerDecorGeometry(QQuick3DObject *parent)
    : QQuick3DGeometry(parent)
{
    rebuild();
}

qreal ViewerDecorGeometry::extent() const
{
    return m_extent;
}

void ViewerDecorGeometry::setExtent(qreal value)
{
    if (qFuzzyCompare(m_extent, value))
        return;
    m_extent = qMax<qreal>(20, value);
    rebuild();
    emit extentChanged();
}

int ViewerDecorGeometry::divisions() const
{
    return m_divisions;
}

void ViewerDecorGeometry::setDivisions(int value)
{
    value = qBound(2, value, 40);
    if (m_divisions == value)
        return;
    m_divisions = value;
    rebuild();
    emit divisionsChanged();
}

QVector3D ViewerDecorGeometry::boxSize() const
{
    return m_boxSize;
}

void ViewerDecorGeometry::setBoxSize(const QVector3D &value)
{
    if (m_boxSize == value)
        return;
    m_boxSize = value;
    rebuild();
    emit boxSizeChanged();
}

int ViewerDecorGeometry::mode() const
{
    return m_mode;
}

void ViewerDecorGeometry::setMode(int value)
{
    if (m_mode == value)
        return;
    m_mode = value;
    rebuild();
    emit modeChanged();
}

void ViewerDecorGeometry::rebuild()
{
    // Вершина: позиция (3 float) + цвет (4 float) = 28 байт.
    QByteArray data;
    const float half = static_cast<float>(m_extent / 2.0);

    auto appendVertex = [&data](float x, float y, float z, float r, float g, float b, float a) {
        const int offset = data.size();
        data.resize(offset + 28);
        float *p = reinterpret_cast<float *>(data.data() + offset);
        p[0] = x; p[1] = y; p[2] = z;
        p[3] = r; p[4] = g; p[5] = b; p[6] = a;
    };
    auto appendLine = [&appendVertex](float x1, float y1, float z1,
                                      float x2, float y2, float z2,
                                      float r, float g, float b, float a) {
        appendVertex(x1, y1, z1, r, g, b, a);
        appendVertex(x2, y2, z2, r, g, b, a);
    };

    if (m_mode == 0) {
        const int divisions = m_divisions;
        const float step = half * 2.0f / divisions;
        for (int i = -divisions; i <= divisions; ++i) {
            const float coordinate = i * step;
            const bool center = (i == 0);
            const float r = center ? 0.34f : 0.14f;
            const float g = center ? 0.40f : 0.18f;
            const float b = center ? 0.48f : 0.23f;
            appendLine(-half, 0, coordinate, half, 0, coordinate, r, g, b, 1);
            appendLine(coordinate, 0, -half, coordinate, 0, half, r, g, b, 1);
        }
    } else if (m_mode == 1) {
        const float axisLength = half * 0.75f;
        appendLine(0, 0, 0, axisLength, 0, 0, 0.95f, 0.28f, 0.30f, 1); // X — красная
        appendLine(0, 0, 0, 0, axisLength, 0, 0.32f, 0.90f, 0.42f, 1); // Y — зелёная
        appendLine(0, 0, 0, 0, 0, axisLength, 0.36f, 0.55f, 1.0f, 1);  // Z — синяя
    } else {
        // Габаритный параллелепипед (режим измерения).
        const float hx = m_boxSize.x() / 2, hy = m_boxSize.y() / 2, hz = m_boxSize.z() / 2;
        const float corners[8][3] = {
            {-hx, -hy, -hz}, {hx, -hy, -hz}, {hx, hy, -hz}, {-hx, hy, -hz},
            {-hx, -hy, hz}, {hx, -hy, hz}, {hx, hy, hz}, {-hx, hy, hz}
        };
        const int edges[12][2] = {
            {0,1}, {1,2}, {2,3}, {3,0},
            {4,5}, {5,6}, {6,7}, {7,4},
            {0,4}, {1,5}, {2,6}, {3,7}
        };
        for (const auto &edge : edges) {
            const float *a = corners[edge[0]];
            const float *b = corners[edge[1]];
            appendLine(a[0], a[1], a[2], b[0], b[1], b[2], 0.25f, 0.85f, 0.75f, 0.9f);
        }
    }

    clear();
    setVertexData(data);
    setStride(28);
    addAttribute(QQuick3DGeometry::Attribute::PositionSemantic, 0,
                 QQuick3DGeometry::Attribute::F32Type);
    addAttribute(QQuick3DGeometry::Attribute::ColorSemantic, 12,
                 QQuick3DGeometry::Attribute::F32Type);
    setPrimitiveType(QQuick3DGeometry::PrimitiveType::Lines);
    if (m_mode == 2)
        setBounds(-m_boxSize / 2, m_boxSize / 2);
    else
        setBounds(QVector3D(-half, 0, -half), QVector3D(half, 0, half));
    update();
}
