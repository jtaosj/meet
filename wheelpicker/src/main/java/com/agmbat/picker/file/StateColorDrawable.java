package com.agmbat.picker.file;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.support.annotation.ColorInt;

/**
 * 按下状态与普通状态下显示不同的颜色
 */
public class StateColorDrawable extends StateBaseDrawable {

    public StateColorDrawable(@ColorInt int pressedColor) {
        this(Color.TRANSPARENT, pressedColor);
    }

    public StateColorDrawable(@ColorInt int normalColor, @ColorInt int pressedColor) {
        addState(new ColorDrawable(normalColor), new ColorDrawable(pressedColor));
    }

}
