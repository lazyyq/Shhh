package kyklab.quiet;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class Utils {
    private static final String TAG = "Utils";

    public static boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager =
                (ActivityManager) App.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }


    public static boolean isHeadsetConnected(Context context) {
        return isWiredHeadsetConnected(context) || isBluetoothHeadsetConnected(context);
    }

    public static boolean isWiredHeadsetConnected(Context context) {
        AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Toast.makeText(context, "isWiredHeadsetConnected(): audioManager is null",
                    Toast.LENGTH_SHORT).show();
        }
        return audioManager != null && audioManager.isWiredHeadsetOn();
    }

    public static boolean isBluetoothHeadsetConnected(Context context) {
        AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            Toast.makeText(context, "isBluetoothHeadsetConnected(): audioManager is null",
                    Toast.LENGTH_SHORT).show();
        }
        return audioManager != null && audioManager.isBluetoothA2dpOn();
    }

    public static boolean isCallActive(Context context) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager == null) {
            Toast.makeText(context, "isCallActive(): telephonyManager is null",
                    Toast.LENGTH_SHORT).show();
        }
        return telephonyManager != null &&
                telephonyManager.getCallState() == TelephonyManager.CALL_STATE_OFFHOOK;
    }

    public static int getStreamVolume(Context context, int streamType) {
        AudioManager audioManager =
                (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            return audioManager.getStreamVolume(streamType);
        } else {
            Toast.makeText(context, "getStreamVolume(): audioManager is null",
                    Toast.LENGTH_SHORT).show();
            return -1;
        }
    }

    public static void muteStreamVolume(Context context, int streamType) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) {
            return;
        }
        audioManager.setStreamVolume(streamType, 0, AudioManager.FLAG_SHOW_UI);
    }

    public static boolean isDarkMode(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    @Nullable
    public static Bitmap getBitmapWithText(Context context, int width, int height,
                                           @DrawableRes int bgVectorDrawableId, @ColorInt int bgColor,
                                           String text, float textSize, @ColorInt int textColor) {
        // Draw vector drawable on canvas
        Drawable drawable = ContextCompat.getDrawable(context, bgVectorDrawableId);
        if (drawable == null) {
            Log.e(TAG, "getBitmapWithText(): drawable is null");
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setTint(bgColor); // 40% opacity
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        // Text options
        Paint textPaint = new Paint();
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(textColor);
        textPaint.setTextSize(getPxFromDp(context, textSize));
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        // Draw text on canvas
        Rect rect = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), rect);
        canvas.drawText(text, bitmap.getWidth() / 2f,
                (bitmap.getHeight() + Math.abs(rect.top - rect.bottom)) / 2f, textPaint);

        return bitmap;
    }

    public static float getPxFromDp(Context context, float dp) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
