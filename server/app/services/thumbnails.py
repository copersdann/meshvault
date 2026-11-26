"""Встроенная генерация миниатюр: проекция геометрии через trimesh + Pillow.

Изображение строится из треугольников GLB; внешние рендеры не нужны.
"""

from __future__ import annotations

import io
import math
from pathlib import Path

import numpy as np
import trimesh
from PIL import Image, ImageDraw, ImageFilter, ImageFont

from ..config import get_settings

settings = get_settings()

MAX_PREVIEW_FACES = 1600


def _background(size: int) -> Image.Image:
    """Тёмный градиент с мягким световым пятном за моделью."""
    y = np.linspace(0.0, 1.0, size)[:, None]
    top = np.array([20.0, 28.0, 41.0])
    bottom = np.array([8.0, 12.0, 18.0])
    gradient = bottom + (top - bottom) * (1.0 - y)
    pixels = np.broadcast_to(gradient[:, None, :], (size, size, 3)).copy()

    yy, xx = np.mgrid[0:size, 0:size]
    distance = np.sqrt(
        ((xx - size * 0.5) / (size * 0.75)) ** 2
        + ((yy - size * 0.38) / (size * 0.65)) ** 2
    )
    glow = np.clip(1.0 - distance, 0.0, 1.0)[..., None] * np.array([12.0, 22.0, 34.0])
    return Image.fromarray(np.clip(pixels + glow, 0, 255).astype(np.uint8), "RGB")


def _draw_label(image: Image.Image, name: str, size: int) -> None:
    draw = ImageDraw.Draw(image)
    font_path = Path(r"C:\Windows\Fonts\arialbd.ttf")
    try:
        font = ImageFont.truetype(str(font_path), max(18, size // 22))
    except OSError:
        font = ImageFont.load_default()

    label = (name or "3D model").strip()
    if len(label) > 34:
        label = label[:31] + "..."
    bbox = draw.textbbox((0, 0), label, font=font)
    text_width = bbox[2] - bbox[0]
    draw.text(
        ((size - text_width) / 2, size * 0.78),
        label,
        fill=(220, 228, 236),
        font=font,
    )


def _placeholder(name: str, out_path: Path, size: int) -> None:
    """Заглушка, если геометрию прочитать не удалось."""
    image = _background(size)
    draw = ImageDraw.Draw(image)

    cx, cy = size / 2, size * 0.44
    radius = size * 0.22
    top = (cx, cy - radius)
    left = (cx - radius * 0.95, cy - radius * 0.45)
    right = (cx + radius * 0.95, cy - radius * 0.45)
    bottom = (cx, cy + radius * 0.55)
    left_bottom = (cx - radius * 0.95, cy + radius * 0.45)
    right_bottom = (cx + radius * 0.95, cy + radius * 0.45)

    accent = (255, 122, 26)
    muted = (90, 105, 120)
    draw.line([top, left, left_bottom, bottom, right_bottom, right, top], fill=accent, width=4)
    draw.line([left, right], fill=muted, width=3)
    draw.line([bottom, top], fill=muted, width=3)
    draw.line([bottom, right], fill=muted, width=3)
    draw.line([bottom, left], fill=muted, width=3)

    _draw_label(image, name, size)
    image.save(out_path, format="PNG")


def _project(points: np.ndarray) -> np.ndarray:
    """Изометрическая ортографическая проекция в координаты экрана + глубина."""
    yaw = math.radians(35)
    pitch = math.radians(25)
    cyaw, syaw = math.cos(yaw), math.sin(yaw)
    cpitch, spitch = math.cos(pitch), math.sin(pitch)
    rotated = np.empty_like(points)
    rotated[:, 0] = points[:, 0] * cyaw - points[:, 1] * syaw
    rotated[:, 1] = (points[:, 0] * syaw + points[:, 1] * cyaw) * cpitch - points[:, 2] * spitch
    rotated[:, 2] = (points[:, 0] * syaw + points[:, 1] * cyaw) * spitch + points[:, 2] * cpitch
    return rotated


def _render_model_image(glb_bytes: bytes, size: int) -> Image.Image | None:
    scene = trimesh.load(io.BytesIO(glb_bytes), file_type="glb", force="scene")
    to_geometry = getattr(scene, "to_geometry", None)
    mesh = to_geometry() if callable(to_geometry) else None
    if mesh is None or not isinstance(mesh, trimesh.Trimesh) or len(mesh.faces) == 0:
        return None

    vertices = np.asarray(mesh.vertices, dtype=float)
    faces = np.asarray(mesh.faces, dtype=int)
    if len(faces) > MAX_PREVIEW_FACES:
        indices = np.linspace(0, len(faces) - 1, num=MAX_PREVIEW_FACES, dtype=int)
        faces = faces[indices]

    # Рисуем в 2× больше и уменьшаем с LANCZOS — так исчезает «лестница» на рёбрах.
    render_size = size * 2
    rotated = _project(vertices)
    span_x = float(rotated[:, 0].max() - rotated[:, 0].min())
    span_y = float(rotated[:, 1].max() - rotated[:, 1].min())
    span = max(span_x, span_y, 1e-6)
    scale = (render_size * 0.62) / span
    center_x = float((rotated[:, 0].max() + rotated[:, 0].min()) / 2)
    center_y = float((rotated[:, 1].max() + rotated[:, 1].min()) / 2)

    screen_x = (rotated[:, 0] - center_x) * scale + render_size / 2
    screen_y = -(rotated[:, 1] - center_y) * scale + render_size * 0.42

    triangles = rotated[faces]
    normals = np.cross(triangles[:, 1] - triangles[:, 0], triangles[:, 2] - triangles[:, 0])
    lengths = np.linalg.norm(normals, axis=1)
    lengths[lengths == 0] = 1
    normals = normals / lengths[:, None]

    key = np.array([-0.35, -0.55, 0.76], dtype=float)
    key /= np.linalg.norm(key)
    fill = np.array([0.62, 0.30, 0.45], dtype=float)
    fill /= np.linalg.norm(fill)
    depth = triangles[:, :, 2].mean(axis=1)
    depth_range = max(float(depth.max() - depth.min()), 1e-6)
    brightness = (
        0.30
        + 0.55 * np.abs(normals @ key)
        + 0.15 * np.abs(normals @ fill)
        - 0.12 * (depth - depth.min()) / depth_range
    )
    brightness = np.clip(brightness, 0.15, 1.0)
    order = np.argsort(depth)

    image = _background(render_size)

    # Мягкая тень под моделью.
    shadow = Image.new("RGBA", (render_size, render_size), (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.ellipse(
        (
            render_size * 0.24,
            render_size * 0.79,
            render_size * 0.76,
            render_size * 0.88,
        ),
        fill=(0, 0, 0, 110),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(render_size * 0.018))
    image = Image.alpha_composite(image.convert("RGBA"), shadow).convert("RGB")

    draw = ImageDraw.Draw(image)
    for index in order:
        p0, p1, p2 = faces[index]
        shade = float(brightness[index])
        color = (
            int(46 + 105 * shade),
            int(82 + 112 * shade),
            int(120 + 105 * shade),
        )
        edge = tuple(max(0, channel - 34) for channel in color)
        polygon = [
            (screen_x[p0], screen_y[p0]),
            (screen_x[p1], screen_y[p1]),
            (screen_x[p2], screen_y[p2]),
        ]
        draw.polygon(polygon, fill=color, outline=edge)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def generate_thumbnail(
    glb_bytes: bytes,
    name: str,
    out_path: Path,
    size: int | None = None,
) -> bool:
    size = size or settings.thumbnail_size
    out_path.parent.mkdir(parents=True, exist_ok=True)

    image: Image.Image | None = None
    if glb_bytes:
        try:
            image = _render_model_image(glb_bytes, size)
        except Exception:
            image = None

    if image is None:
        _placeholder(name, out_path, size)
        return True

    # Название показывает карточка — дублировать его в миниатюре не нужно.
    image.save(out_path, format="PNG")
    return True
