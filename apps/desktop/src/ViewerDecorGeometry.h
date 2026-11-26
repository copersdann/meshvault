#pragma once

#include <QQuick3DGeometry>
#include <QVector3D>

/// Сетка стола и цветные оси X/Y/Z для 3D-вьювера (аналог OrcaSlicer).
class ViewerDecorGeometry : public QQuick3DGeometry
{
    Q_OBJECT
    Q_PROPERTY(qreal extent READ extent WRITE setExtent NOTIFY extentChanged)
    Q_PROPERTY(int mode READ mode WRITE setMode NOTIFY modeChanged)
    Q_PROPERTY(int divisions READ divisions WRITE setDivisions NOTIFY divisionsChanged)
    Q_PROPERTY(QVector3D boxSize READ boxSize WRITE setBoxSize NOTIFY boxSizeChanged)

public:
    explicit ViewerDecorGeometry(QQuick3DObject *parent = nullptr);

    qreal extent() const;
    void setExtent(qreal value);
    int mode() const;
    void setMode(int value);
    int divisions() const;
    void setDivisions(int value);
    QVector3D boxSize() const;
    void setBoxSize(const QVector3D &value);

signals:
    void extentChanged();
    void modeChanged();
    void divisionsChanged();
    void boxSizeChanged();

private:
    void rebuild();

    qreal m_extent = 200;
    int m_mode = 0; // 0 — сетка, 1 — оси
    int m_divisions = 10;
    QVector3D m_boxSize = QVector3D(100, 100, 100);
};
