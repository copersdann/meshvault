package com.meshvault.app.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class MeshData(val positions: FloatArray, val normals: FloatArray) {
    val vertexCount: Int get() = positions.size / 3
}

object StlParser {
    fun cube(): MeshData {
        val positions = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        fun tri(nx: Float, ny: Float, nz: Float, a: FloatArray, b: FloatArray, c: FloatArray) {
            for (v in arrayOf(a, b, c)) {
                positions.add(v[0]); positions.add(v[1]); positions.add(v[2])
                normals.add(nx); normals.add(ny); normals.add(nz)
            }
        }
        tri(0f, 0f, 1f, floatArrayOf(-1f,-1f,1f), floatArrayOf(1f,-1f,1f), floatArrayOf(1f,1f,1f))
        tri(0f, 0f, 1f, floatArrayOf(-1f,-1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(-1f,1f,1f))
        tri(0f, 0f, -1f, floatArrayOf(1f,-1f,-1f), floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,1f,-1f))
        tri(0f, 0f, -1f, floatArrayOf(1f,-1f,-1f), floatArrayOf(-1f,1f,-1f), floatArrayOf(1f,1f,-1f))
        tri(0f, 1f, 0f, floatArrayOf(-1f,1f,1f), floatArrayOf(1f,1f,1f), floatArrayOf(1f,1f,-1f))
        tri(0f, 1f, 0f, floatArrayOf(-1f,1f,1f), floatArrayOf(1f,1f,-1f), floatArrayOf(-1f,1f,-1f))
        tri(0f, -1f, 0f, floatArrayOf(-1f,-1f,-1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,-1f,1f))
        tri(0f, -1f, 0f, floatArrayOf(-1f,-1f,-1f), floatArrayOf(1f,-1f,1f), floatArrayOf(-1f,-1f,1f))
        tri(1f, 0f, 0f, floatArrayOf(1f,-1f,1f), floatArrayOf(1f,-1f,-1f), floatArrayOf(1f,1f,-1f))
        tri(1f, 0f, 0f, floatArrayOf(1f,-1f,1f), floatArrayOf(1f,1f,-1f), floatArrayOf(1f,1f,1f))
        tri(-1f, 0f, 0f, floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,-1f,1f), floatArrayOf(-1f,1f,1f))
        tri(-1f, 0f, 0f, floatArrayOf(-1f,-1f,-1f), floatArrayOf(-1f,1f,1f), floatArrayOf(-1f,1f,-1f))
        return MeshData(positions.toFloatArray(), normals.toFloatArray())
    }

    fun parse(bytes: ByteArray): MeshData {
        if (isBinary(bytes)) return parseBinary(bytes)
        return parseAscii(bytes)
    }

    private fun isBinary(bytes: ByteArray): Boolean {
        if (bytes.size < 84) return false
        val count = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return count in 1..10_000_000 && 84 + count * 50 <= bytes.size
    }

    private fun parseBinary(bytes: ByteArray): MeshData {
        val count = ByteBuffer.wrap(bytes, 80, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val positions = FloatArray(count * 9)
        val normals = FloatArray(count * 9)
        val buffer = ByteBuffer.wrap(bytes, 84, count * 50).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val nx = buffer.float
            val ny = buffer.float
            val nz = buffer.float
            for (v in 0..2) {
                val x = buffer.float
                val y = buffer.float
                val z = buffer.float
                val o = i * 9 + v * 3
                positions[o] = x
                positions[o + 1] = y
                positions[o + 2] = z
                normals[o] = nx
                normals[o + 1] = ny
                normals[o + 2] = nz
            }
            buffer.short // attribute byte count
        }
        return MeshData(positions, normals)
    }

    private fun parseAscii(bytes: ByteArray): MeshData {
        val text = String(bytes, Charsets.UTF_8)
        val vertices = mutableListOf<Float>()
        val normalRegex = Regex("""facet\s+normal\s+([-\d.eE+]+)\s+([-\d.eE+]+)\s+([-\d.eE+]+)""")
        val vertexRegex = Regex("""vertex\s+([-\d.eE+]+)\s+([-\d.eE+]+)\s+([-\d.eE+]+)""")
        var normal = floatArrayOf(0f, 0f, 1f)
        var triangleVertices = mutableListOf<Float>()
        val facetNormals = mutableListOf<Float>()

        fun flush() {
            if (triangleVertices.size == 9) {
                vertices.addAll(triangleVertices.toList())
                repeat(3) {
                    facetNormals.add(normal[0]); facetNormals.add(normal[1]); facetNormals.add(normal[2])
                }
            }
            triangleVertices = mutableListOf()
        }

        text.lineSequence().forEach { line ->
            normalRegex.find(line)?.let { m ->
                normal = floatArrayOf(
                    m.groupValues[1].toFloatOrNull() ?: 0f,
                    m.groupValues[2].toFloatOrNull() ?: 0f,
                    m.groupValues[3].toFloatOrNull() ?: 1f,
                )
            }
            vertexRegex.find(line)?.let { m ->
                triangleVertices.addAll(
                    listOf(
                        m.groupValues[1].toFloatOrNull() ?: 0f,
                        m.groupValues[2].toFloatOrNull() ?: 0f,
                        m.groupValues[3].toFloatOrNull() ?: 0f,
                    )
                )
            }
            if (line.contains("endloop", ignoreCase = true)) flush()
        }

        return MeshData(vertices.toFloatArray(), facetNormals.toFloatArray())
    }
}
