package com.meshvault.app.ui.main;

import com.meshvault.app.data.StlParser;
import com.meshvault.app.ui.CameraPreset;
import com.meshvault.app.ui.GCodeModel;
import com.meshvault.app.ui.GCodeParser;
import com.meshvault.app.ui.ModelTransform;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Проверки парсеров и состояния 3D-просмотрщика. */
public class MainScreenViewModelTest {

    @Test
    public void stlCube_hasTwelveTriangles() {
        Assert.assertEquals(36, StlParser.INSTANCE.cube().getVertexCount());
    }

    @Test
    public void modelTransform_resetRestoresDefaults() {
        ModelTransform transform = new ModelTransform(2f, 1f, 1f, 0f, 0f, 90f, 10f, 0f, 0f);
        transform.reset();
        Assert.assertEquals(1f, transform.getScaleX(), 0.0001f);
        Assert.assertEquals(0f, transform.getRotZ(), 0.0001f);
        Assert.assertEquals(0f, transform.getOffsetX(), 0.0001f);
    }

    @Test
    public void cameraPresets_coverMainViews() {
        List<CameraPreset> presets = Arrays.asList(CameraPreset.values());
        Assert.assertFalse(presets.isEmpty());
        Assert.assertTrue(presets.contains(CameraPreset.TOP));
        Assert.assertTrue(presets.contains(CameraPreset.ISO));
    }

    @Test
    public void gcodeParser_readsLayersAndMoves() {
        String gcode = "; тестовый G-code\n"
                + "G1 Z0.2 F1200\n"
                + "G1 X10 Y10 E0.1\n"
                + "G1 X20 Y10 E0.2\n"
                + "G1 Z0.4\n"
                + "G1 X20 Y20 E0.3\n"
                + "G0 X0 Y0\n";
        GCodeModel result = GCodeParser.INSTANCE.parse(gcode);
        Assert.assertEquals(2, result.getLayerCount());
        Assert.assertEquals(4, result.getSegments().size());
        Assert.assertTrue(result.getLengthMm() > 0);
    }
}
