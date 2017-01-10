package com.example.ncku.hospitalnavigation.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.DisplayMetrics;

import java.io.InputStream;

/**
 * Created by user on 2016/12/22.
 */

public class Utils {

    public static Bitmap getBitmap(Context context, int resourceId) {
        InputStream inputStream = context.getResources().openRawResource(resourceId);
        return BitmapFactory.decodeStream(inputStream, null, null);
    }

}
