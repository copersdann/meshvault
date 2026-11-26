#include "ApiClient.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QHttpMultiPart>
#include <QHttpPart>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QNetworkReply>
#include <QRegularExpression>
#include <QSettings>
#include <QStandardPaths>
#include <QUrl>

ApiClient *ApiClient::instance()
{
    static ApiClient client;
    return &client;
}

ApiClient::ApiClient(QObject *parent)
    : QObject(parent),
      m_manager(new QNetworkAccessManager(this))
{
    QSettings settings;
    const QString savedUrl = settings.value(QStringLiteral("serverUrl"),
                                            QStringLiteral("http://127.0.0.1:8000")).toString();
    m_token = settings.value(QStringLiteral("token")).toString();
    setServerUrl(savedUrl);
}

QString ApiClient::serverUrl() const { return m_serverUrl; }
QString ApiClient::token() const { return m_token; }
QVariantList ApiClient::models() const { return m_models; }
QString ApiClient::searchQuery() const { return m_searchQuery; }
QString ApiClient::sortOrder() const { return m_sortOrder; }
int ApiClient::catalogLimit() const { return m_catalogLimit; }
int ApiClient::modelsTotal() const { return m_modelsTotal; }
QVariantMap ApiClient::modelStats() const { return m_modelStats; }
QVariantList ApiClient::printers() const { return m_printers; }
QVariantList ApiClient::jobs() const { return m_jobs; }
QVariantMap ApiClient::currentModel() const { return m_currentModel; }
QString ApiClient::glbLocalUrl() const { return m_glbLocalUrl; }
QString ApiClient::status() const { return m_status; }
QString ApiClient::lastError() const { return m_lastError; }
QVariantMap ApiClient::health() const { return m_health; }
QVariantMap ApiClient::importConfig() const { return m_importConfig; }
QVariantList ApiClient::slicers() const { return m_slicers; }
QVariantMap ApiClient::printability() const { return m_printability; }
QVariantList ApiClient::collections() const { return m_collections; }
QVariantList ApiClient::categories() const { return m_categories; }
QVariantList ApiClient::tags() const { return m_tags; }

void ApiClient::setServerUrl(const QString &value)
{
    QString trimmed = value.trimmed();
    while (trimmed.endsWith(QLatin1Char('/'))) {
        trimmed.chop(1);
    }
    if (trimmed.isEmpty()) {
        trimmed = QStringLiteral("http://127.0.0.1:8000");
    }
    if (m_serverUrl == trimmed) {
        return;
    }
    m_serverUrl = trimmed;
    QSettings settings;
    settings.setValue(QStringLiteral("serverUrl"), m_serverUrl);
    emit configChanged();
    refreshModels();
    checkHealth();
}

void ApiClient::setToken(const QString &value)
{
    if (m_token == value) {
        return;
    }
    m_token = value;
    QSettings settings;
    settings.setValue(QStringLiteral("token"), m_token);
    emit configChanged();
    refreshModels();
    checkHealth();
}

QNetworkRequest ApiClient::makeRequest(const QString &path) const
{
    QNetworkRequest request(QUrl(m_serverUrl + path));
    if (!m_token.isEmpty()) {
        request.setRawHeader("Authorization", QByteArray("Bearer ") + m_token.toUtf8());
    }
    return request;
}

QString ApiClient::urlWithToken(const QString &relativeUrl) const
{
    if (relativeUrl.isEmpty()) {
        return {};
    }
    QString url = relativeUrl;
    if (url.startsWith(QLatin1Char('/'))) {
        url = m_serverUrl + url;
    }
    if (!m_token.isEmpty()) {
        const QString encoded = QString::fromUtf8(QUrl::toPercentEncoding(m_token));
        url += url.contains(QLatin1Char('?')) ? QStringLiteral("&token=") + encoded
                                              : QStringLiteral("?token=") + encoded;
    }
    return url;
}

QString ApiClient::cacheDir() const
{
    const QString path = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation)
                         + QStringLiteral("/cache");
    QDir().mkpath(path);
    return path;
}

void ApiClient::setStatus(const QString &value)
{
    if (m_status != value) {
        m_status = value;
        emit statusChanged();
    }
}

void ApiClient::setError(const QString &value)
{
    setStatus(QString());
    if (m_lastError != value) {
        m_lastError = value;
        emit errorChanged();
    }
}

void ApiClient::handleJson(QNetworkReply *reply,
                           const std::function<void(const QJsonDocument &)> &onSuccess)
{
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, onSuccess]() {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            setError(reply->errorString());
            return;
        }
        QJsonParseError parseError;
        const QJsonDocument document = QJsonDocument::fromJson(reply->readAll(), &parseError);
        if (parseError.error != QJsonParseError::NoError) {
            setError(QStringLiteral("Invalid server response: ") + parseError.errorString());
            return;
        }
        if (m_lastError.isEmpty() == false) {
            setError(QString());
        }
        onSuccess(document);
    });
}

void ApiClient::postJson(const QString &path, const QJsonObject &body,
                         const std::function<void(const QJsonDocument &)> &onSuccess)
{
    QNetworkRequest request = makeRequest(path);
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    QNetworkReply *reply = m_manager->post(request, QJsonDocument(body).toJson());
    handleJson(reply, onSuccess);
}

void ApiClient::setSearchQuery(const QString &value)
{
    if (m_searchQuery == value) {
        return;
    }
    m_searchQuery = value;
    emit searchChanged();
    refreshModels(m_trashMode);
}

void ApiClient::setSortOrder(const QString &value)
{
    if (m_sortOrder == value) {
        return;
    }
    m_sortOrder = value.isEmpty() ? QStringLiteral("created_desc") : value;
    emit searchChanged();
    refreshModels(m_trashMode);
}

void ApiClient::setCatalogLimit(int value)
{
    value = qBound(20, value, 200);
    if (m_catalogLimit == value)
        return;
    m_catalogLimit = value;
    emit catalogChanged();
    refreshModels(m_trashMode);
}

void ApiClient::refreshModels(bool trash)
{
    m_trashMode = trash;
    QString path = QStringLiteral("/api/models?limit=") + QString::number(m_catalogLimit);
    if (!m_searchQuery.trimmed().isEmpty()) {
        const QString encoded = QString::fromUtf8(QUrl::toPercentEncoding(m_searchQuery.trimmed()));
        path += QStringLiteral("&q=") + encoded;
    }
    if (!m_sortOrder.isEmpty()) {
        path += QStringLiteral("&sort=") + m_sortOrder;
    }
    if (trash) {
        path += QStringLiteral("&trash=true");
    }
    QNetworkReply *reply = m_manager->get(makeRequest(path));
    handleJson(reply, [this](const QJsonDocument &document) {
        QVariantList result;
        const QJsonArray items = document.object().value(QStringLiteral("items")).toArray();
        for (const QJsonValue &value : items) {
            const QJsonObject item = value.toObject();
            const QJsonObject latest = item.value(QStringLiteral("latest_version")).toObject();
            QVariantMap map;
            map[QStringLiteral("id")] = item.value(QStringLiteral("id")).toString();
            map[QStringLiteral("name")] = item.value(QStringLiteral("name")).toString();
            map[QStringLiteral("description")] = item.value(QStringLiteral("description")).toString();
            map[QStringLiteral("status")] = item.value(QStringLiteral("status")).toString();
            map[QStringLiteral("version")] = latest.value(QStringLiteral("version")).toInt(1);
            map[QStringLiteral("format")] = latest.value(QStringLiteral("source_format")).toString();
            map[QStringLiteral("size")] = latest.value(QStringLiteral("size")).toVariant();
            map[QStringLiteral("triangles")] = latest.value(QStringLiteral("triangles")).toInt();
            map[QStringLiteral("thumbUrl")] =
                urlWithToken(latest.value(QStringLiteral("thumb_url")).toString());

            QStringList tags;
            for (const QJsonValue &tag : item.value(QStringLiteral("tags")).toArray()) {
                const QString name = tag.toObject().value(QStringLiteral("name")).toString();
                if (!name.isEmpty()) tags.append(name);
            }
            map[QStringLiteral("tags")] = tags;

            QStringList collections;
            for (const QJsonValue &collection : item.value(QStringLiteral("collections")).toArray()) {
                const QString name = collection.toObject().value(QStringLiteral("name")).toString();
                if (!name.isEmpty()) collections.append(name);
            }
            map[QStringLiteral("collections")] = collections;

            if (!latest.value(QStringLiteral("bbox_x")).isNull()) {
                map[QStringLiteral("bbox")] =
                    QStringLiteral("%1 × %2 × %3 мм")
                        .arg(latest.value(QStringLiteral("bbox_x")).toDouble())
                        .arg(latest.value(QStringLiteral("bbox_y")).toDouble())
                        .arg(latest.value(QStringLiteral("bbox_z")).toDouble());
            }
            map[QStringLiteral("createdAt")] = item.value(QStringLiteral("created_at")).toString();
            map[QStringLiteral("watertight")] = latest.value(QStringLiteral("watertight")).toVariant();
            map[QStringLiteral("volume")] = latest.value(QStringLiteral("volume")).toVariant();
            result.append(map);
        }
        m_models = result;
        m_modelsTotal = document.object().value(QStringLiteral("total")).toInt(result.size());

        int ready = 0, processing = 0, failed = 0;
        qlonglong totalSize = 0;
        QVariantMap formats;
        for (const QVariant &value : result) {
            const QVariantMap item = value.toMap();
            const QString status = item.value(QStringLiteral("status")).toString();
            if (status == QStringLiteral("ready")) ++ready;
            else if (status == QStringLiteral("processing")) ++processing;
            else if (status == QStringLiteral("failed")) ++failed;
            totalSize += item.value(QStringLiteral("size")).toLongLong();
            const QString format = item.value(QStringLiteral("format")).toString();
            if (!format.isEmpty())
                formats[format] = formats.value(format).toInt() + 1;
        }
        m_modelStats[QStringLiteral("ready")] = ready;
        m_modelStats[QStringLiteral("processing")] = processing;
        m_modelStats[QStringLiteral("failed")] = failed;
        m_modelStats[QStringLiteral("size")] = totalSize;
        m_modelStats[QStringLiteral("loaded")] = result.size();
        m_modelStats[QStringLiteral("formats")] = formats;
        emit modelsChanged();
    });
}

void ApiClient::refreshPrinters()
{
    QNetworkReply *reply = m_manager->get(makeRequest(QStringLiteral("/api/printers")));
    handleJson(reply, [this](const QJsonDocument &document) {
        QVariantList result;
        for (const QJsonValue &value : document.array()) {
            const QJsonObject item = value.toObject();
            QVariantMap map;
            map[QStringLiteral("id")] = item.value(QStringLiteral("id")).toString();
            map[QStringLiteral("name")] = item.value(QStringLiteral("name")).toString();
            map[QStringLiteral("host")] = item.value(QStringLiteral("host")).toString();
            map[QStringLiteral("enabled")] = item.value(QStringLiteral("enabled")).toBool();
            result.append(map);
        }
        m_printers = result;
        emit printersChanged();
    });
}

void ApiClient::refreshJobs()
{
    QNetworkReply *reply = m_manager->get(makeRequest(QStringLiteral("/api/jobs?active_only=false")));
    handleJson(reply, [this](const QJsonDocument &document) {
        QVariantList result;
        for (const QJsonValue &value : document.array()) {
            const QJsonObject item = value.toObject();
            QVariantMap map;
            map[QStringLiteral("id")] = item.value(QStringLiteral("id")).toString();
            map[QStringLiteral("modelId")] = item.value(QStringLiteral("model_id")).toString();
            map[QStringLiteral("modelName")] = item.value(QStringLiteral("model_name")).toString();
            map[QStringLiteral("printerName")] = item.value(QStringLiteral("printer_name")).toString();
            map[QStringLiteral("status")] = item.value(QStringLiteral("status")).toString();
            map[QStringLiteral("filename")] = item.value(QStringLiteral("filename")).toString();
            map[QStringLiteral("progress")] = item.value(QStringLiteral("progress")).toDouble();
            map[QStringLiteral("message")] = item.value(QStringLiteral("message")).toString();
            result.append(map);
        }
        m_jobs = result;
        emit jobsChanged();
    });
}

void ApiClient::checkHealth()
{
    QNetworkReply *reply = m_manager->get(QNetworkRequest{QUrl(m_serverUrl + QStringLiteral("/api/health"))});
    handleJson(reply, [this](const QJsonDocument &document) {
        m_health = document.object().toVariantMap();
        emit healthChanged();
    });
}

void ApiClient::downloadGlb(const QString &url)
{
    if (url.isEmpty()) {
        m_glbLocalUrl.clear();
        emit glbChanged();
        return;
    }

    QNetworkRequest request{QUrl(url)};
    if (!m_token.isEmpty()) {
        request.setRawHeader("Authorization", QByteArray("Bearer ") + m_token.toUtf8());
    }
    QNetworkReply *reply = m_manager->get(request);
    setStatus(QStringLiteral("Загрузка 3D-модели..."));
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, url]() {
        reply->deleteLater();
        setStatus(QString());
        if (reply->error() != QNetworkReply::NoError) {
            setError(reply->errorString());
            return;
        }
        const QString modelId = m_currentModel.value(QStringLiteral("id")).toString();
        const QString fileName = QStringLiteral("%1.glb").arg(modelId);
        const QString filePath = cacheDir() + QLatin1Char('/') + fileName;
        QFile file(filePath);
        if (!file.open(QIODevice::WriteOnly)) {
            setError(file.errorString());
            return;
        }
        file.write(reply->readAll());
        file.close();
        m_glbLocalUrl = QUrl::fromLocalFile(filePath).toString();
        emit glbChanged();
        Q_UNUSED(url)
    });
}

void ApiClient::openModel(const QString &modelId)
{
    QNetworkReply *reply = m_manager->get(
        makeRequest(QStringLiteral("/api/models/") + modelId));
    handleJson(reply, [this](const QJsonDocument &document) {
        QJsonObject item = document.object();
        const QJsonArray versions = item.value(QStringLiteral("versions")).toArray();
        const QJsonObject latest = versions.isEmpty() ? QJsonObject() : versions.first().toObject();

        QVariantMap map = item.toVariantMap();
        map[QStringLiteral("thumbUrl")] =
            urlWithToken(item.value(QStringLiteral("name")).toString().isEmpty()
                             ? QString()
                             : latest.value(QStringLiteral("thumb_url")).toString());
        m_currentModel = map;
        emit currentModelChanged();

        downloadGlb(urlWithToken(latest.value(QStringLiteral("glb_url")).toString()));
    });
}

void ApiClient::uploadModel(const QString &filePath, const QString &name)
{
    QFile file(filePath);
    if (!file.open(QIODevice::ReadOnly)) {
        setError(file.errorString());
        return;
    }
    const QByteArray data = file.readAll();
    file.close();

    auto *multiPart = new QHttpMultiPart(QHttpMultiPart::FormDataType);
    QHttpPart filePart;
    const QString disposition = QStringLiteral("form-data; name=\"file\"; filename=\"%1\"")
                                    .arg(QFileInfo(filePath).fileName());
    filePart.setHeader(QNetworkRequest::ContentDispositionHeader, QVariant(disposition));
    filePart.setHeader(QNetworkRequest::ContentTypeHeader,
                       QVariant(QStringLiteral("application/octet-stream")));
    filePart.setBody(data);
    multiPart->append(filePart);

    if (!name.trimmed().isEmpty()) {
        QHttpPart namePart;
        namePart.setHeader(QNetworkRequest::ContentDispositionHeader,
                           QVariant(QStringLiteral("form-data; name=\"name\"")));
        namePart.setBody(name.trimmed().toUtf8());
        multiPart->append(namePart);
    }

    QNetworkReply *reply = m_manager->post(makeRequest(QStringLiteral("/api/models")), multiPart);
    multiPart->setParent(reply);
    setStatus(QStringLiteral("Загрузка модели..."));
    handleJson(reply, [this](const QJsonDocument &) {
        setStatus(QString());
        refreshModels();
        emit operationFinished(QStringLiteral("Модель загружена"));
    });
}

void ApiClient::deleteModel(const QString &modelId, bool permanent)
{
    const QString path = QStringLiteral("/api/models/") + modelId
                         + (permanent ? QStringLiteral("?permanent=true") : QString());
    QNetworkReply *reply = m_manager->deleteResource(makeRequest(path));
    handleJson(reply, [this, modelId, permanent](const QJsonDocument &) {
        if (m_currentModel.value(QStringLiteral("id")).toString() == modelId) {
            m_currentModel.clear();
            m_glbLocalUrl.clear();
            emit currentModelChanged();
            emit glbChanged();
        }
        refreshModels(m_trashMode);
        emit operationFinished(permanent ? QStringLiteral("Модель удалена навсегда")
                                         : QStringLiteral("Модель перемещена в корзину"));
    });
}

void ApiClient::restoreModel(const QString &modelId)
{
    postJson(QStringLiteral("/api/models/") + modelId + QStringLiteral("/restore"),
             QJsonObject(), [this](const QJsonDocument &) {
                 refreshModels(m_trashMode);
                 emit operationFinished(QStringLiteral("Модель восстановлена"));
             });
}

void ApiClient::uploadFiles(const QStringList &filePaths)
{
    for (const QString &path : filePaths) {
        if (!path.trimmed().isEmpty()) {
            uploadModel(path, QString());
        }
    }
}

void ApiClient::openInSlicer(const QString &modelId, int version, const QString &slicerId)
{
    QJsonObject body;
    if (!slicerId.isEmpty()) {
        body[QStringLiteral("slicer_id")] = slicerId;
    }
    const QString path = QStringLiteral("/api/models/%1/versions/%2/open-in-slicer")
                             .arg(modelId).arg(version);
    postJson(path, body, [this](const QJsonDocument &document) {
        const QString name = document.object().value(QStringLiteral("slicer")).toString();
        emit operationFinished(QStringLiteral("Открыто в слайсере: ") + name);
    });
}

void ApiClient::exportModel(const QString &modelId, int version, const QString &format)
{
    const QString path = QStringLiteral("/api/models/%1/versions/%2/export?format=%3")
                             .arg(modelId).arg(version).arg(format);
    QNetworkReply *reply = m_manager->get(makeRequest(path));
    setStatus(QStringLiteral("Экспорт модели..."));
    QObject::connect(reply, &QNetworkReply::finished, this,
                     [this, reply, modelId, version, format]() {
        reply->deleteLater();
        setStatus(QString());
        if (reply->error() != QNetworkReply::NoError) {
            setError(reply->errorString());
            return;
        }
        QString base = m_currentModel.value(QStringLiteral("name")).toString();
        if (base.isEmpty()) {
            base = modelId;
        }
        base.replace(QRegularExpression(QStringLiteral("[^A-Za-z0-9_\\-]+")), QStringLiteral("_"));
        const QString fileName = QStringLiteral("%1-v%2.%3").arg(base).arg(version).arg(format);
        const QString dir = QStandardPaths::writableLocation(QStandardPaths::DownloadLocation);
        QDir().mkpath(dir);
        const QString filePath = dir + QLatin1Char('/') + fileName;
        QFile file(filePath);
        if (!file.open(QIODevice::WriteOnly)) {
            setError(file.errorString());
            return;
        }
        file.write(reply->readAll());
        file.close();
        emit operationFinished(QStringLiteral("Экспортировано: ") + filePath);
    });
}

void ApiClient::fetchPrintability(const QString &modelId, int version, const QString &printerId)
{
    QString path = QStringLiteral("/api/models/%1/versions/%2/printability")
                       .arg(modelId).arg(version);
    if (!printerId.isEmpty()) {
        path += QStringLiteral("?printer_id=") + printerId;
    }
    QNetworkReply *reply = m_manager->get(makeRequest(path));
    handleJson(reply, [this](const QJsonDocument &document) {
        m_printability = document.object().toVariantMap();
        emit printabilityChanged();
    });
}

void ApiClient::refreshSlicers()
{
    QNetworkReply *reply = m_manager->get(makeRequest(QStringLiteral("/api/slicers")));
    handleJson(reply, [this](const QJsonDocument &document) {
        m_slicers = document.array().toVariantList();
        emit slicersChanged();
    });
}

void ApiClient::addSlicer(const QString &name, const QString &path)
{
    QJsonObject body;
    body[QStringLiteral("name")] = name;
    body[QStringLiteral("path")] = path;
    postJson(QStringLiteral("/api/slicers"), body, [this](const QJsonDocument &document) {
        m_slicers = document.array().toVariantList();
        emit slicersChanged();
        emit operationFinished(QStringLiteral("Слайсер добавлен"));
    });
}

void ApiClient::removeSlicer(const QString &slicerId)
{
    const QString path = QStringLiteral("/api/slicers/")
                         + QString::fromUtf8(QUrl::toPercentEncoding(slicerId));
    QNetworkReply *reply = m_manager->deleteResource(makeRequest(path));
    handleJson(reply, [this](const QJsonDocument &document) {
        m_slicers = document.array().toVariantList();
        emit slicersChanged();
        emit operationFinished(QStringLiteral("Слайсер удалён"));
    });
}

void ApiClient::addPrinter(const QString &name, const QString &host, const QString &apiKey,
                           double bedX, double bedY)
{
    QJsonObject profile;
    profile[QStringLiteral("bed_x")] = bedX;
    profile[QStringLiteral("bed_y")] = bedY;
    QJsonObject body;
    body[QStringLiteral("name")] = name;
    body[QStringLiteral("host")] = host;
    body[QStringLiteral("api_key")] = apiKey;
    body[QStringLiteral("profile_json")] = profile;

    postJson(QStringLiteral("/api/printers"), body, [this](const QJsonDocument &) {
        refreshPrinters();
        emit operationFinished(QStringLiteral("Принтер добавлен"));
    });
}

void ApiClient::probePrinter(const QString &host, const QString &apiKey)
{
    QJsonObject body;
    body[QStringLiteral("host")] = host;
    body[QStringLiteral("api_key")] = apiKey;
    postJson(QStringLiteral("/api/printers/probe"), body, [this](const QJsonDocument &document) {
        const QJsonObject result = document.object();
        const bool ok = result.value(QStringLiteral("ok")).toBool();
        emit probeResult(ok, result.value(QStringLiteral("message")).toString());
    });
}

void ApiClient::deletePrinter(const QString &printerId)
{
    QNetworkReply *reply = m_manager->deleteResource(
        makeRequest(QStringLiteral("/api/printers/") + printerId));
    handleJson(reply, [this](const QJsonDocument &) { refreshPrinters(); });
}

void ApiClient::testPrinter(const QString &printerId)
{
    postJson(QStringLiteral("/api/printers/") + printerId + QStringLiteral("/test"),
             QJsonObject(), [this](const QJsonDocument &document) {
                 const QJsonObject result = document.object();
                 emit probeResult(result.value(QStringLiteral("ok")).toBool(),
                                  result.value(QStringLiteral("message")).toString());
             });
}

void ApiClient::attachGcode(const QString &modelId, int version, const QString &filePath)
{
    QFile file(filePath);
    if (!file.open(QIODevice::ReadOnly)) {
        setError(file.errorString());
        return;
    }
    const QByteArray data = file.readAll();
    file.close();

    auto *multiPart = new QHttpMultiPart(QHttpMultiPart::FormDataType);
    QHttpPart filePart;
    filePart.setHeader(QNetworkRequest::ContentDispositionHeader,
                       QVariant(QStringLiteral("form-data; name=\"file\"; filename=\"%1\"")
                                    .arg(QFileInfo(filePath).fileName())));
    filePart.setHeader(QNetworkRequest::ContentTypeHeader,
                       QVariant(QStringLiteral("application/octet-stream")));
    filePart.setBody(data);
    multiPart->append(filePart);

    const QString path = QStringLiteral("/api/models/%1/versions/%2/gcode").arg(modelId).arg(version);
    QNetworkReply *reply = m_manager->post(makeRequest(path), multiPart);
    multiPart->setParent(reply);
    setStatus(QStringLiteral("Загрузка G-code..."));
    handleJson(reply, [this, modelId](const QJsonDocument &) {
        setStatus(QString());
        openModel(modelId);
        emit operationFinished(QStringLiteral("G-code прикреплён"));
    });
}

void ApiClient::startPrint(const QString &modelId, const QString &printerId)
{
    QJsonObject body;
    body[QStringLiteral("printer_id")] = printerId;
    postJson(QStringLiteral("/api/models/") + modelId + QStringLiteral("/print"),
             body, [this](const QJsonDocument &) {
                 refreshJobs();
                 emit operationFinished(QStringLiteral("Печать запущена"));
             });
}

void ApiClient::pauseJob(const QString &jobId)
{
    postJson(QStringLiteral("/api/jobs/") + jobId + QStringLiteral("/pause"),
             QJsonObject(), [this](const QJsonDocument &) { refreshJobs(); });
}

void ApiClient::resumeJob(const QString &jobId)
{
    postJson(QStringLiteral("/api/jobs/") + jobId + QStringLiteral("/resume"),
             QJsonObject(), [this](const QJsonDocument &) { refreshJobs(); });
}

void ApiClient::cancelJob(const QString &jobId)
{
    postJson(QStringLiteral("/api/jobs/") + jobId + QStringLiteral("/cancel"),
             QJsonObject(), [this](const QJsonDocument &) { refreshJobs(); });
}

void ApiClient::getImportConfig()
{
    QNetworkReply *reply = m_manager->get(makeRequest(QStringLiteral("/api/import/config")));
    handleJson(reply, [this](const QJsonDocument &document) {
        m_importConfig = document.object().toVariantMap();
        emit importConfigChanged();
    });
}

void ApiClient::setImportConfig(const QString &directory, int intervalMin)
{
    QJsonObject body;
    body[QStringLiteral("directory")] = directory;
    body[QStringLiteral("interval_min")] = intervalMin;
    QNetworkRequest request = makeRequest(QStringLiteral("/api/import/config"));
    request.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));

    QNetworkReply *reply = m_manager->sendCustomRequest(request, "PUT",
                                                        QJsonDocument(body).toJson());
    handleJson(reply, [this](const QJsonDocument &document) {
        m_importConfig = document.object().toVariantMap();
        emit importConfigChanged();
        emit operationFinished(QStringLiteral("Настройки импорта сохранены"));
    });
}

void ApiClient::scanImport()
{
    postJson(QStringLiteral("/api/import/scan"), QJsonObject(),
             [this](const QJsonDocument &document) {
                 const QVariantMap result = document.object().toVariantMap();
                 emit importResult(result);
                 refreshModels();
                 getImportConfig();
                 emit operationFinished(QStringLiteral("Сканирование завершено"));
             });
}

void ApiClient::scanImportDryRun()
{
    postJson(QStringLiteral("/api/import/scan?dry_run=true"), QJsonObject(),
             [this](const QJsonDocument &document) {
                 emit importPreviewResult(document.object().toVariantMap());
                 emit operationFinished(QStringLiteral("Предпросмотр импорта завершён"));
             });
}

void ApiClient::retryVersion(const QString &modelId, int version)
{
    const QString path = QStringLiteral("/api/models/%1/versions/%2/retry")
                             .arg(modelId)
                             .arg(version);
    postJson(path, QJsonObject(), [this, modelId](const QJsonDocument &) {
        openModel(modelId);
        emit operationFinished(QStringLiteral("Повторная обработка запущена"));
    });
}

void ApiClient::importUrl(const QString &url)
{
    QJsonObject body;
    body[QStringLiteral("url")] = url;
    postJson(QStringLiteral("/api/import/url"), body, [this](const QJsonDocument &document) {
        const QVariantMap result = document.object().toVariantMap();
        emit operationFinished(
            QStringLiteral("Импортировано: %1, дубликатов: %2")
                .arg(result.value(QStringLiteral("added")).toInt())
                .arg(result.value(QStringLiteral("duplicates")).toInt())
        );
        refreshModels();
    });
}

namespace {
constexpr const char *kDefaultShelf = "library,upload,url,refresh,catalog,shelf";
const QStringList kLayoutKeys = {
    QStringLiteral("sidebarDetached"),
    QStringLiteral("catalogDetached"),
    QStringLiteral("sort"),
    QStringLiteral("cardWidth"),
    QStringLiteral("compact"),
    QStringLiteral("showPreview"),
    QStringLiteral("showStats"),
    QStringLiteral("shelfButtons"),
};
} // namespace

QString ApiClient::layoutValue(const QString &key, const QString &fallback) const
{
    QSettings settings;
    return settings.value(QStringLiteral("layout/") + key, fallback).toString();
}

void ApiClient::setLayoutValue(const QString &key, const QString &value)
{
    QSettings settings;
    settings.setValue(QStringLiteral("layout/") + key, value);
}

QString ApiClient::shelfButtons() const
{
    QString value = layoutValue(QStringLiteral("shelfButtons"), QString::fromLatin1(kDefaultShelf));
    // Миграция после отказа от внешних каталогов: «3D-стоки» → «По ссылке».
    value.replace(QStringLiteral("stocks"), QStringLiteral("url"));
    return value;
}

void ApiClient::setShelfButtons(const QString &ids)
{
    setLayoutValue(QStringLiteral("shelfButtons"), ids);
    emit layoutChanged();
}

QStringList ApiClient::layoutProfiles() const
{
    QSettings settings;
    return settings.value(QStringLiteral("ui/profiles")).toStringList();
}

void ApiClient::saveLayoutProfile(const QString &name)
{
    if (name.trimmed().isEmpty()) return;
    QSettings settings;
    QVariantMap profile;
    for (const QString &key : kLayoutKeys) {
        profile.insert(key, settings.value(QStringLiteral("layout/") + key));
    }
    settings.setValue(QStringLiteral("profiles/") + name.trimmed(), profile);
    QStringList names = layoutProfiles();
    if (!names.contains(name.trimmed())) names.append(name.trimmed());
    settings.setValue(QStringLiteral("ui/profiles"), names);
}

void ApiClient::loadLayoutProfile(const QString &name)
{
    QSettings settings;
    const QVariantMap profile = settings.value(QStringLiteral("profiles/") + name).toMap();
    for (auto it = profile.constBegin(); it != profile.constEnd(); ++it) {
        settings.setValue(QStringLiteral("layout/") + it.key(), it.value());
    }
    emit layoutChanged();
}

void ApiClient::refreshMeta()
{
    const auto namesIn = [](const QJsonDocument &document) {
        QVariantList names;
        for (const QJsonValue &value : document.array()) {
            const QString name = value.toObject().value(QStringLiteral("name")).toString();
            if (!name.isEmpty()) names.append(name);
        }
        return names;
    };

    handleJson(m_manager->get(makeRequest(QStringLiteral("/api/collections"))),
               [this, namesIn](const QJsonDocument &document) {
                   m_collections = namesIn(document);
                   emit metaChanged();
               });
    handleJson(m_manager->get(makeRequest(QStringLiteral("/api/categories"))),
               [this, namesIn](const QJsonDocument &document) {
                   m_categories = namesIn(document);
                   emit metaChanged();
               });
    handleJson(m_manager->get(makeRequest(QStringLiteral("/api/tags"))),
               [this, namesIn](const QJsonDocument &document) {
                   m_tags = namesIn(document);
                   emit metaChanged();
               });
}
