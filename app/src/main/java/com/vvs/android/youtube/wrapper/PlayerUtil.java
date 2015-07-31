package com.vvs.android.youtube.wrapper;

import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.PaintDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;

/**
 * Created by vasiliy.simonov on 28/07/15.
 */
public class PlayerUtil {

    public static PaintDrawable createGradient(final int[] colors, final float[] offsets) {
        assert colors.length == offsets.length;
        ShapeDrawable.ShaderFactory shaderFactory = new ShapeDrawable.ShaderFactory() {
            @Override
            public Shader resize(int width, int height) {
                LinearGradient linearGradient = new LinearGradient(0, 0, 0, height,
                        colors,
                        offsets,
                        Shader.TileMode.REPEAT);
                return linearGradient;
            }
        };

        PaintDrawable paint = new PaintDrawable();
        paint.setShape(new RectShape());
        paint.setShaderFactory(shaderFactory);
        return paint;
    }
}
