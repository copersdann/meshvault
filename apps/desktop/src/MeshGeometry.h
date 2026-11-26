#pragma once

#include <QByteArray>
#include <QQuick3DGeometry>
#include <QUrl>

/// Геометрия, загружаемая из GLB напрямую (без Balsam/asset importer Qt).
class MeshGeometry : public QQuick3DGeometry
{
    Q_OBJECT
    Q_PROPERTY(QUrl source READ source WRITE setSource NOTIFY sourceChanged)
    Q_PROPERTY(bool loaded READ loaded NOTIFY loadedChanged)
    Q_PROPERTY(bool flatShading READ flatShading WRITE setFlatShading NOTIFY flatShadingChanged)

public:
    QUrl source() const;
    void setSource(const QUrl &value);
    bool loaded() const;
    bool flatShading() const;
    void setFlatShading(bool value);
    Q_INVOKABLE void refresh() { update(); }

signals:
    void sourceChanged();
    void loadedChanged();
    void flatShadingChanged();
    void loadFailed(const QString &message);

private:
    bool loadGlb(const QString &path, QString *error);

    QUrl m_source;
    QByteArray m_bytes;
    bool m_loaded = false;
    bool m_flatShading = false;
};
