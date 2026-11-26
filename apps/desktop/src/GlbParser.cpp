#include "GlbParser.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QString>
#include <QList>

#include <cmath>
#include <cstring>

namespace {

struct Mat4 {
    float m[16] = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
};

Mat4 multiply(const Mat4 &a, const Mat4 &b)
{
    Mat4 result;
    for (int column = 0; column < 4; ++column) {
        for (int row = 0; row < 4; ++row) {
            float sum = 0.0f;
            for (int k = 0; k < 4; ++k)
                sum += a.m[k * 4 + row] * b.m[column * 4 + k];
            result.m[column * 4 + row] = sum;
        }
    }
    return result;
}

Mat4 nodeMatrix(const QJsonObject &node)
{
    if (node.contains(QStringLiteral("matrix"))) {
        const QJsonArray values = node.value(QStringLiteral("matrix")).toArray();
        Mat4 matrix;
        if (values.size() == 16) {
            for (int i = 0; i < 16; ++i)
                matrix.m[i] = static_cast<float>(values.at(i).toDouble());
        }
        return matrix;
    }

    Mat4 matrix;
    const QJsonArray translation = node.value(QStringLiteral("translation")).toArray();
    const QJsonArray rotation = node.value(QStringLiteral("rotation")).toArray();
    const QJsonArray scale = node.value(QStringLiteral("scale")).toArray();

    float x = 0, y = 0, z = 0, w = 1;
    if (rotation.size() == 4) {
        x = static_cast<float>(rotation.at(0).toDouble());
        y = static_cast<float>(rotation.at(1).toDouble());
        z = static_cast<float>(rotation.at(2).toDouble());
        w = static_cast<float>(rotation.at(3).toDouble());
    }
    float sx = 1, sy = 1, sz = 1;
    if (scale.size() == 3) {
        sx = static_cast<float>(scale.at(0).toDouble());
        sy = static_cast<float>(scale.at(1).toDouble());
        sz = static_cast<float>(scale.at(2).toDouble());
    }

    matrix.m[0] = sx * (1 - 2 * (y * y + z * z));
    matrix.m[1] = sx * (2 * (x * y + z * w));
    matrix.m[2] = sx * (2 * (x * z - y * w));
    matrix.m[4] = sy * (2 * (x * y - z * w));
    matrix.m[5] = sy * (1 - 2 * (x * x + z * z));
    matrix.m[6] = sy * (2 * (y * z + x * w));
    matrix.m[8] = sz * (2 * (x * z + y * w));
    matrix.m[9] = sz * (2 * (y * z - x * w));
    matrix.m[10] = sz * (1 - 2 * (x * x + y * y));
    if (translation.size() == 3) {
        matrix.m[12] = static_cast<float>(translation.at(0).toDouble());
        matrix.m[13] = static_cast<float>(translation.at(1).toDouble());
        matrix.m[14] = static_cast<float>(translation.at(2).toDouble());
    }
    return matrix;
}

void transformPoint(const Mat4 &matrix, float &x, float &y, float &z)
{
    const float tx = matrix.m[0] * x + matrix.m[4] * y + matrix.m[8] * z + matrix.m[12];
    const float ty = matrix.m[1] * x + matrix.m[5] * y + matrix.m[9] * z + matrix.m[13];
    const float tz = matrix.m[2] * x + matrix.m[6] * y + matrix.m[10] * z + matrix.m[14];
    x = tx; y = ty; z = tz;
}

void transformDirection(const Mat4 &matrix, float &x, float &y, float &z)
{
    const float tx = matrix.m[0] * x + matrix.m[4] * y + matrix.m[8] * z;
    const float ty = matrix.m[1] * x + matrix.m[5] * y + matrix.m[9] * z;
    const float tz = matrix.m[2] * x + matrix.m[6] * y + matrix.m[10] * z;
    const float length = std::sqrt(tx * tx + ty * ty + tz * tz);
    if (length > 1e-8f) {
        x = tx / length; y = ty / length; z = tz / length;
    }
}

int componentSize(int componentType)
{
    switch (componentType) {
    case 5120: case 5121: return 1;
    case 5122: case 5123: return 2;
    case 5125: case 5126: return 4;
    default: return 0;
    }
}

int componentCount(const QString &type)
{
    if (type == QStringLiteral("SCALAR")) return 1;
    if (type == QStringLiteral("VEC2")) return 2;
    if (type == QStringLiteral("VEC3")) return 3;
    if (type == QStringLiteral("VEC4")) return 4;
    if (type == QStringLiteral("MAT4")) return 16;
    return 0;
}

bool readAccessor(const QJsonObject &document, const QByteArray &binary, int index,
                  QByteArray *data, int *count, int *componentType)
{
    const QJsonArray accessors = document.value(QStringLiteral("accessors")).toArray();
    if (index < 0 || index >= accessors.size())
        return false;
    const QJsonObject accessor = accessors.at(index).toObject();
    *componentType = accessor.value(QStringLiteral("componentType")).toInt();
    *count = accessor.value(QStringLiteral("count")).toInt();
    const int components = componentCount(accessor.value(QStringLiteral("type")).toString());
    const int size = componentSize(*componentType);
    if (*count <= 0 || components <= 0 || size <= 0)
        return false;
    const int viewIndex = accessor.value(QStringLiteral("bufferView")).toInt(-1);
    const QJsonArray views = document.value(QStringLiteral("bufferViews")).toArray();
    if (viewIndex < 0 || viewIndex >= views.size())
        return false;
    const QJsonObject view = views.at(viewIndex).toObject();
    const int offset = view.value(QStringLiteral("byteOffset")).toInt(0)
                       + accessor.value(QStringLiteral("byteOffset")).toInt(0);
    const int length = (*count) * components * size;
    if (offset < 0 || offset + length > binary.size())
        return false;
    *data = QByteArray(binary.constData() + offset, length);
    return true;
}

void appendPrimitive(const QJsonObject &document, const QByteArray &binary,
                     const QJsonObject &primitive, const Mat4 &world, GlbMeshData *out)
{
    const QJsonObject attributes = primitive.value(QStringLiteral("attributes")).toObject();
    QByteArray positions, normals, indexData;
    int count = 0, positionType = 0, normalCount = 0, normalType = 0;
    int indexCount = 0, indexType = 0;

    if (!readAccessor(document, binary,
                      attributes.value(QStringLiteral("POSITION")).toInt(-1),
                      &positions, &count, &positionType)
        || positionType != 5126)
        return;
    readAccessor(document, binary,
                 attributes.value(QStringLiteral("NORMAL")).toInt(-1),
                 &normals, &normalCount, &normalType);
    const bool hasIndices = readAccessor(document, binary,
                                         primitive.value(QStringLiteral("indices")).toInt(-1),
                                         &indexData, &indexCount, &indexType);
    if (hasIndices && indexType != 5123 && indexType != 5125)
        return;
    if (!hasIndices)
        indexCount = count;

    const int baseVertex = out->vertexCount;
    const int oldSize = out->vertices.size();
    out->vertices.resize(oldSize + count * 24);
    float *vertexOutput = reinterpret_cast<float *>(out->vertices.data() + oldSize);
    const float *sourcePositions = reinterpret_cast<const float *>(positions.constData());
    const float *sourceNormals = (normalType == 5126 && normalCount == count)
                                     ? reinterpret_cast<const float *>(normals.constData())
                                     : nullptr;

    for (int i = 0; i < count; ++i) {
        float x = sourcePositions[i * 3];
        float y = sourcePositions[i * 3 + 1];
        float z = sourcePositions[i * 3 + 2];
        transformPoint(world, x, y, z);
        vertexOutput[i * 6] = x;
        vertexOutput[i * 6 + 1] = y;
        vertexOutput[i * 6 + 2] = z;

        float nx = sourceNormals ? sourceNormals[i * 3] : 0.0f;
        float ny = sourceNormals ? sourceNormals[i * 3 + 1] : 1.0f;
        float nz = sourceNormals ? sourceNormals[i * 3 + 2] : 0.0f;
        transformDirection(world, nx, ny, nz);
        vertexOutput[i * 6 + 3] = nx;
        vertexOutput[i * 6 + 4] = ny;
        vertexOutput[i * 6 + 5] = nz;

        out->boundsMin.setX(qMin(out->boundsMin.x(), x));
        out->boundsMin.setY(qMin(out->boundsMin.y(), y));
        out->boundsMin.setZ(qMin(out->boundsMin.z(), z));
        out->boundsMax.setX(qMax(out->boundsMax.x(), x));
        out->boundsMax.setY(qMax(out->boundsMax.y(), y));
        out->boundsMax.setZ(qMax(out->boundsMax.z(), z));
    }
    out->vertexCount += count;

    const int oldIndexSize = out->indices.size();
    out->indices.resize(oldIndexSize + indexCount * 4);
    quint32 *indexOutput = reinterpret_cast<quint32 *>(out->indices.data() + oldIndexSize);
    if (hasIndices && indexType == 5125) {
        const quint32 *source = reinterpret_cast<const quint32 *>(indexData.constData());
        for (int i = 0; i < indexCount; ++i)
            indexOutput[i] = baseVertex + source[i];
    } else if (hasIndices) {
        const quint16 *source = reinterpret_cast<const quint16 *>(indexData.constData());
        for (int i = 0; i < indexCount; ++i)
            indexOutput[i] = baseVertex + source[i];
    } else {
        for (int i = 0; i < indexCount; ++i)
            indexOutput[i] = baseVertex + i;
    }
    out->indexCount += indexCount;
}

void traverseNodes(const QJsonObject &document, const QByteArray &binary,
                   int nodeIndex, const Mat4 &parent, GlbMeshData *out)
{
    const QJsonArray nodes = document.value(QStringLiteral("nodes")).toArray();
    if (nodeIndex < 0 || nodeIndex >= nodes.size())
        return;
    const QJsonObject node = nodes.at(nodeIndex).toObject();
    const Mat4 world = multiply(parent, nodeMatrix(node));

    const int meshIndex = node.value(QStringLiteral("mesh")).toInt(-1);
    const QJsonArray meshes = document.value(QStringLiteral("meshes")).toArray();
    if (meshIndex >= 0 && meshIndex < meshes.size()) {
        const QJsonArray primitives = meshes.at(meshIndex).toObject()
                                          .value(QStringLiteral("primitives")).toArray();
        for (const QJsonValue &value : primitives)
            appendPrimitive(document, binary, value.toObject(), world, out);
    }

    const QJsonArray children = node.value(QStringLiteral("children")).toArray();
    for (const QJsonValue &child : children)
        traverseNodes(document, binary, child.toInt(), world, out);
}

/// Делит каждый треугольник на отдельные вершины и ставит нормаль грани.
void applyFlatShading(GlbMeshData *out)
{
    const int stride = 24;
    const int triangleCount = out->indexCount / 3;
    if (triangleCount <= 0)
        return;

    QByteArray newVertices;
    QByteArray newIndices;
    newVertices.resize(triangleCount * 3 * stride);
    newIndices.resize(triangleCount * 3 * 4);
    float *vertexOutput = reinterpret_cast<float *>(newVertices.data());
    quint32 *indexOutput = reinterpret_cast<quint32 *>(newIndices.data());
    const float *source = reinterpret_cast<const float *>(out->vertices.constData());
    const quint32 *sourceIndices = reinterpret_cast<const quint32 *>(out->indices.constData());

    for (int triangle = 0; triangle < triangleCount; ++triangle) {
        float positions[3][3];
        for (int corner = 0; corner < 3; ++corner) {
            const int vertexIndex = static_cast<int>(sourceIndices[triangle * 3 + corner]);
            for (int axis = 0; axis < 3; ++axis)
                positions[corner][axis] = source[vertexIndex * 6 + axis];
        }
        const float ax = positions[1][0] - positions[0][0];
        const float ay = positions[1][1] - positions[0][1];
        const float az = positions[1][2] - positions[0][2];
        const float bx = positions[2][0] - positions[0][0];
        const float by = positions[2][1] - positions[0][1];
        const float bz = positions[2][2] - positions[0][2];
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        const float length = std::sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1e-9f) {
            nx /= length; ny /= length; nz /= length;
        } else {
            nx = 0; ny = 1; nz = 0;
        }
        for (int corner = 0; corner < 3; ++corner) {
            const int offset = (triangle * 3 + corner) * 6;
            for (int axis = 0; axis < 3; ++axis)
                vertexOutput[offset + axis] = positions[corner][axis];
            vertexOutput[offset + 3] = nx;
            vertexOutput[offset + 4] = ny;
            vertexOutput[offset + 5] = nz;
            indexOutput[triangle * 3 + corner] = triangle * 3 + corner;
        }
    }

    out->vertices = newVertices;
    out->indices = newIndices;
    out->vertexCount = triangleCount * 3;
    out->indexCount = triangleCount * 3;
}

} // namespace

bool GlbParser::parse(const QByteArray &bytes, GlbMeshData *out,
                      QString *error, bool flatShading)
{
    if (!out || bytes.size() < 20 || bytes.left(4) != QByteArray("glTF", 4)) {
        if (error) *error = QStringLiteral("Не GLB-файл");
        return false;
    }

    quint32 jsonLength = 0, jsonType = 0;
    std::memcpy(&jsonLength, bytes.constData() + 12, 4);
    std::memcpy(&jsonType, bytes.constData() + 16, 4);
    if (jsonType != 0x4E4F534A || 20 + static_cast<int>(jsonLength) > bytes.size()) {
        if (error) *error = QStringLiteral("Некорректный JSON-чанк GLB");
        return false;
    }

    int position = 20 + static_cast<int>(jsonLength);
    const QByteArray jsonBytes = bytes.mid(20, static_cast<int>(jsonLength));
    quint32 binaryLength = 0;
    if (position + 8 <= bytes.size()) {
        quint32 chunkLength = 0, chunkType = 0;
        std::memcpy(&chunkLength, bytes.constData() + position, 4);
        std::memcpy(&chunkType, bytes.constData() + position + 4, 4);
        position += 8;
        if (chunkType == 0x004E4942)
            binaryLength = qMin<quint32>(chunkLength, static_cast<quint32>(bytes.size() - position));
    }
    const QByteArray binary = bytes.mid(position, static_cast<int>(binaryLength));

    QJsonParseError parseError;
    const QJsonDocument json = QJsonDocument::fromJson(jsonBytes, &parseError);
    if (parseError.error != QJsonParseError::NoError) {
        if (error) *error = parseError.errorString();
        return false;
    }

    const QJsonObject document = json.object();
    const QJsonArray scenes = document.value(QStringLiteral("scenes")).toArray();
    const QJsonArray nodes = document.value(QStringLiteral("nodes")).toArray();
    if (nodes.isEmpty()) {
        if (error) *error = QStringLiteral("В GLB нет узлов");
        return false;
    }

    out->boundsMin = QVector3D(1e9f, 1e9f, 1e9f);
    out->boundsMax = QVector3D(-1e9f, -1e9f, -1e9f);
    const Mat4 identity;
    QList<int> children;
    for (const QJsonValue &node : nodes)
        for (const QJsonValue &child : node.toObject().value(QStringLiteral("children")).toArray())
            children.append(child.toInt());

    if (!scenes.isEmpty()) {
        const QJsonArray roots = scenes.first().toObject().value(QStringLiteral("nodes")).toArray();
        for (const QJsonValue &root : roots)
            traverseNodes(document, binary, root.toInt(), identity, out);
    } else {
        for (int i = 0; i < nodes.size(); ++i) {
            if (!children.contains(i))
                traverseNodes(document, binary, i, identity, out);
        }
    }
    if (!out->isValid()) {
        for (int i = 0; i < nodes.size(); ++i)
            traverseNodes(document, binary, i, identity, out);
    }
    if (!out->isValid()) {
        if (error) *error = QStringLiteral("В GLB нет геометрии");
        return false;
    }
    if (flatShading)
        applyFlatShading(out);
    return true;
}
