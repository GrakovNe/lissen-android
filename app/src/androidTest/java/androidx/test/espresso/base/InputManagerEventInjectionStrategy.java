package androidx.test.espresso.base;

import android.hardware.input.InputManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import androidx.test.espresso.InjectEventSecurityException;
import androidx.test.platform.app.InstrumentationRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Patched drop-in replacement for Espresso's InputManagerEventInjectionStrategy.
 * <p>
 * Android 16 (API 36) removed InputManager.getInstance(). The original Espresso class
 * (espresso-core ≤ 3.7.0-alpha01) unconditionally calls getDeclaredMethod("getInstance")
 * and throws NoSuchMethodException. This shadow class catches that exception and falls
 * back to Context.getSystemService(InputManager.class) so tests run on API 36+.
 */
final class InputManagerEventInjectionStrategy implements EventInjectionStrategy {

    private static final String TAG = "EventInjectionStrategy";
    private static final long KEYBOARD_DISMISSAL_DELAY_MILLIS = 1000L;

    private boolean initComplete = false;
    private Method injectInputEventMethod;
    private Method setSourceMotionMethod;
    private Object instanceInputManagerObject;
    private int asyncEventMode;
    private int syncEventMode;

    InputManagerEventInjectionStrategy() {
    }

    InputManagerEventInjectionStrategy initialize() {
        if (initComplete) {
            return this;
        }
        Log.d(TAG, "Creating injection strategy with input manager.");
        try {
            Class<?> inputManagerClass = Class.forName("android.hardware.input.InputManager");

            // Android 16 (API 36) removed InputManager.getInstance(); use getSystemService instead.
            try {
                Method instanceGetter = inputManagerClass.getDeclaredMethod("getInstance");
                instanceGetter.setAccessible(true);
                instanceInputManagerObject = instanceGetter.invoke(null);
            } catch (NoSuchMethodException e) {
                instanceInputManagerObject = InstrumentationRegistry.getInstrumentation()
                        .getTargetContext()
                        .getSystemService(InputManager.class);
            }

            injectInputEventMethod = instanceInputManagerObject.getClass()
                    .getDeclaredMethod("injectInputEvent", InputEvent.class, Integer.TYPE);
            injectInputEventMethod.setAccessible(true);

            Field syncField = inputManagerClass.getField("INJECT_INPUT_EVENT_MODE_WAIT_FOR_FINISH");
            syncField.setAccessible(true);
            syncEventMode = syncField.getInt(inputManagerClass);

            if (Build.VERSION.SDK_INT >= 28) {
                asyncEventMode = 0;
            } else {
                Field asyncField = inputManagerClass.getField("INJECT_INPUT_EVENT_MODE_ASYNC");
                asyncField.setAccessible(true);
                asyncEventMode = asyncField.getInt(inputManagerClass);
            }

            setSourceMotionMethod = MotionEvent.class.getDeclaredMethod("setSource", Integer.TYPE);

            initComplete = true;
        } catch (ClassNotFoundException
                 | IllegalAccessException
                 | InvocationTargetException
                 | NoSuchFieldException
                 | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public boolean injectKeyEvent(KeyEvent event) throws InjectEventSecurityException {
        try {
            return (Boolean) injectInputEventMethod.invoke(
                    instanceInputManagerObject, event, syncEventMode);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SecurityException) {
                throw new InjectEventSecurityException(cause);
            }
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            throw new InjectEventSecurityException(e);
        }
    }

    @Override
    public boolean injectMotionEvent(MotionEvent event, boolean sync)
            throws InjectEventSecurityException {
        return innerInjectMotionEvent(event, true, sync);
    }

    private boolean innerInjectMotionEvent(MotionEvent event, boolean first, boolean sync)
            throws InjectEventSecurityException {
        if ((event.getSource() & 2) == 0 && !isFromTouchpadInGlassDevice(event)) {
            try {
                setSourceMotionMethod.invoke(event, 4098);
            } catch (Exception ignored) {
            }
        }

        int mode = sync ? syncEventMode : asyncEventMode;

        try {
            return (Boolean) injectInputEventMethod.invoke(instanceInputManagerObject, event, mode);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SecurityException) {
                if (first) {
                    Log.w(TAG, "Error performing a ViewAction! soft keyboard dismissal animation "
                            + "may have been in the way. Retrying once after: 1000 millis");
                    SystemClock.sleep(KEYBOARD_DISMISSAL_DELAY_MILLIS);
                    try {
                        innerInjectMotionEvent(event, false, sync);
                    } catch (InjectEventSecurityException ignored) {
                    }
                    return false;
                } else {
                    throw new InjectEventSecurityException(
                            "Check if Espresso is clicking outside the app (system dialog, "
                                    + "navigation bar if edge-to-edge is enabled, etc.).",
                            cause);
                }
            }
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            throw new InjectEventSecurityException(e);
        }
    }

    private static boolean isFromTouchpadInGlassDevice(MotionEvent event) {
        if (!Build.DEVICE.contains("glass") && !Build.DEVICE.contains("Glass")
                && !Build.DEVICE.contains("wingman")) {
            return false;
        }
        return (event.getSource() & 1048584) != 0;
    }
}
