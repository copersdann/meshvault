#pragma once

#include <QNetworkAccessManager>
#include <QObject>
#include <QStringList>
#include <QVariantList>
#include <QVariantMap>

#include <functional>

class QJsonDocument;
class QJsonObject;
class QNetworkReply;
class QHttpMultiPart;

class ApiClient : public QObject
{
    Q_OBJECT

public:
    static ApiClient *instance();

    Q_PROPERTY(QString serverUrl READ serverUrl WRITE setServerUrl NOTIFY configChanged)
    Q_PROPERTY(QString token READ token WRITE setToken NOTIFY configChanged)
    Q_PROPERTY(QVariantList models READ models NOTIFY modelsChanged)
    Q_PROPERTY(QString searchQuery READ searchQuery WRITE setSearchQuery NOTIFY searchChanged)
    Q_PROPERTY(QString sortOrder READ sortOrder WRITE setSortOrder NOTIFY searchChanged)
    Q_PROPERTY(int catalogLimit READ catalogLimit WRITE setCatalogLimit NOTIFY catalogChanged)
    Q_PROPERTY(int modelsTotal READ modelsTotal NOTIFY modelsChanged)
    Q_PROPERTY(QVariantMap modelStats READ modelStats NOTIFY modelsChanged)
    Q_PROPERTY(QVariantList printers READ printers NOTIFY printersChanged)
    Q_PROPERTY(QVariantList jobs READ jobs NOTIFY jobsChanged)
    Q_PROPERTY(QVariantMap currentModel READ currentModel NOTIFY currentModelChanged)
    Q_PROPERTY(QString glbLocalUrl READ glbLocalUrl NOTIFY glbChanged)
    Q_PROPERTY(QString status READ status NOTIFY statusChanged)
    Q_PROPERTY(QString lastError READ lastError NOTIFY errorChanged)
    Q_PROPERTY(QVariantMap health READ health NOTIFY healthChanged)
    Q_PROPERTY(QVariantMap importConfig READ importConfig NOTIFY importConfigChanged)
    Q_PROPERTY(QVariantList slicers READ slicers NOTIFY slicersChanged)
    Q_PROPERTY(QVariantMap printability READ printability NOTIFY printabilityChanged)
    Q_PROPERTY(QVariantList collections READ collections NOTIFY metaChanged)
    Q_PROPERTY(QVariantList categories READ categories NOTIFY metaChanged)
    Q_PROPERTY(QVariantList tags READ tags NOTIFY metaChanged)

    QString serverUrl() const;
    QString token() const;
    QVariantList models() const;
    QString searchQuery() const;
    QString sortOrder() const;
    int catalogLimit() const;
    int modelsTotal() const;
    QVariantMap modelStats() const;
    QVariantList printers() const;
    QVariantList jobs() const;
    QVariantMap currentModel() const;
    QString glbLocalUrl() const;
    QString status() const;
    QString lastError() const;
    QVariantMap health() const;
    QVariantMap importConfig() const;
    QVariantList slicers() const;
    QVariantMap printability() const;
    QVariantList collections() const;
    QVariantList categories() const;
    QVariantList tags() const;

public slots:
    void setServerUrl(const QString &value);
    void setToken(const QString &value);
    void refreshModels(bool trash = false);
    void setSearchQuery(const QString &value);
    void setSortOrder(const QString &value);
    void setCatalogLimit(int value);
    void refreshPrinters();
    void refreshJobs();
    void checkHealth();
    void openModel(const QString &modelId);
    void uploadModel(const QString &filePath, const QString &name);
    void uploadFiles(const QStringList &filePaths);
    void deleteModel(const QString &modelId, bool permanent = false);
    void restoreModel(const QString &modelId);
    void openInSlicer(const QString &modelId, int version, const QString &slicerId = QString());
    void exportModel(const QString &modelId, int version, const QString &format);
    void fetchPrintability(const QString &modelId, int version, const QString &printerId = QString());
    void refreshSlicers();
    void addSlicer(const QString &name, const QString &path);
    void removeSlicer(const QString &slicerId);
    void addPrinter(const QString &name, const QString &host, const QString &apiKey,
                    double bedX, double bedY);
    void probePrinter(const QString &host, const QString &apiKey);
    void deletePrinter(const QString &printerId);
    void testPrinter(const QString &printerId);
    void attachGcode(const QString &modelId, int version, const QString &filePath);
    void startPrint(const QString &modelId, const QString &printerId);
    void pauseJob(const QString &jobId);
    void resumeJob(const QString &jobId);
    void cancelJob(const QString &jobId);
    void getImportConfig();
    void setImportConfig(const QString &directory, int intervalMin);
    void scanImport();
    void scanImportDryRun();
    void retryVersion(const QString &modelId, int version);
    void refreshMeta();
    void importUrl(const QString &url);
    QString layoutValue(const QString &key, const QString &fallback = QString()) const;
    void setLayoutValue(const QString &key, const QString &value);
    QString shelfButtons() const;
    void setShelfButtons(const QString &ids);
    QStringList layoutProfiles() const;
    void saveLayoutProfile(const QString &name);
    void loadLayoutProfile(const QString &name);

signals:
    void configChanged();
    void modelsChanged();
    void searchChanged();
    void catalogChanged();
    void printersChanged();
    void jobsChanged();
    void currentModelChanged();
    void glbChanged();
    void statusChanged();
    void errorChanged();
    void healthChanged();
    void importConfigChanged();
    void slicersChanged();
    void printabilityChanged();
    void metaChanged();
    void layoutChanged();
    void importResult(const QVariantMap &result);
    void importPreviewResult(const QVariantMap &result);
    void probeResult(bool ok, const QString &message);
    void operationFinished(const QString &message);

private:
    explicit ApiClient(QObject *parent = nullptr);

    QNetworkRequest makeRequest(const QString &path) const;
    void postJson(const QString &path, const QJsonObject &body,
                  const std::function<void(const QJsonDocument &)> &onSuccess);
    void downloadGlb(const QString &url);
    void setStatus(const QString &value);
    void setError(const QString &value);
    void handleJson(QNetworkReply *reply,
                    const std::function<void(const QJsonDocument &)> &onSuccess);
    QString urlWithToken(const QString &relativeUrl) const;
    QString cacheDir() const;

    QNetworkAccessManager *m_manager = nullptr;
    QString m_serverUrl;
    QString m_token;
    QVariantList m_models;
    QString m_searchQuery;
    QString m_sortOrder = QStringLiteral("created_desc");
    int m_catalogLimit = 200;
    int m_modelsTotal = 0;
    QVariantMap m_modelStats;
    QVariantList m_printers;
    QVariantList m_jobs;
    QVariantMap m_currentModel;
    QString m_glbLocalUrl;
    QString m_status;
    QString m_lastError;
    QVariantMap m_health;
    QVariantMap m_importConfig;
    QVariantList m_slicers;
    QVariantMap m_printability;
    QVariantList m_collections;
    QVariantList m_categories;
    QVariantList m_tags;
    bool m_trashMode = false;
};
