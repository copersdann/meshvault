#include <QDateTime>
#include <QDebug>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QGuiApplication>
#include <QMutex>
#include <QMutexLocker>
#include <QQmlApplicationEngine>
#include <QQmlError>
#include <QQuickStyle>
#include <QStandardPaths>
#include <QStringConverter>
#include <QTextStream>
#include <QUrl>
#include <QtGlobal>

#include <cstdlib>

#include "ApiClient.h"
#include "MeshGeometry.h"
#include "ViewerDecorGeometry.h"

namespace {

QMutex logMutex;
QString logPath;

QString logFilePath()
{
    const QString dir = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation);
    QDir().mkpath(dir);
    return dir + QStringLiteral("/meshvault.log");
}

void writeLog(const QString &line)
{
    QMutexLocker locker(&logMutex);
    static QFile file;

    if (!file.isOpen()) {
        const QString rotated = logPath + QStringLiteral(".1");
        if (QFile::exists(logPath) && QFileInfo(logPath).size() > 5 * 1024 * 1024) {
            QFile::remove(rotated);
            QFile::rename(logPath, rotated);
        }
        file.setFileName(logPath);
        if (!file.open(QIODevice::WriteOnly | QIODevice::Append | QIODevice::Text)) {
            return;
        }
    }

    if (file.isOpen()) {
        QTextStream stream(&file);
        stream.setEncoding(QStringConverter::Utf8);
        stream << line << Qt::endl;
        stream.flush();
        file.flush();
    }
}

void messageHandler(QtMsgType type, const QMessageLogContext &context, const QString &message)
{
    QString level;
    switch (type) {
    case QtDebugMsg:
        level = QStringLiteral("DEBUG");
        break;
    case QtInfoMsg:
        level = QStringLiteral("INFO");
        break;
    case QtWarningMsg:
        level = QStringLiteral("WARN");
        break;
    case QtCriticalMsg:
        level = QStringLiteral("ERROR");
        break;
    case QtFatalMsg:
        level = QStringLiteral("FATAL");
        break;
    }

    QString line = QStringLiteral("[%1] [%2] %3")
                       .arg(
                           QDateTime::currentDateTime().toString(QStringLiteral("yyyy-MM-dd HH:mm:ss.zzz")),
                           level,
                           message
                       );
    if (context.file && context.file[0] != '\0') {
        line += QStringLiteral(" (%1:%2)")
                    .arg(QString::fromUtf8(context.file))
                    .arg(context.line);
    }
    writeLog(line);

    if (type == QtFatalMsg) {
        std::abort();
    }
}

} // namespace

int main(int argc, char *argv[])
{
    QGuiApplication app(argc, argv);
    QCoreApplication::setApplicationName(QStringLiteral("MeshVault"));
    QCoreApplication::setApplicationVersion(QStringLiteral("0.1.0"));
    QCoreApplication::setOrganizationName(QStringLiteral("MeshVault"));

    logPath = logFilePath();
    qInstallMessageHandler(messageHandler);
    qInfo() << "MeshVault starting; log:" << logPath;

    QQuickStyle::setStyle(QStringLiteral("Fusion"));

    QQmlApplicationEngine engine;
    qmlRegisterSingletonInstance("MeshVault", 1, 0, "Api", ApiClient::instance());
    qmlRegisterType<MeshGeometry>("MeshVault", 1, 0, "MeshGeometry");
    qmlRegisterType<ViewerDecorGeometry>("MeshVault", 1, 0, "ViewerDecorGeometry");

    const QUrl url(QStringLiteral("qrc:/qml/main.qml"));
    QObject::connect(
        &engine,
        &QQmlApplicationEngine::objectCreationFailed,
        &app,
        []() { QCoreApplication::exit(-1); },
        Qt::QueuedConnection
    );
    QObject::connect(
        &engine,
        &QQmlApplicationEngine::warnings,
        [](const QList<QQmlError> &errors) {
            for (const QQmlError &error : errors) {
                qWarning() << "QML:" << error.toString();
            }
        }
    );

    engine.load(url);
    if (engine.rootObjects().isEmpty()) {
        qCritical() << "QML engine produced no root object";
        return -1;
    }

    return app.exec();
}
