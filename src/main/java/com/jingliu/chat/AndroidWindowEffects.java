package com.jingliu.chat;

import android.animation.ValueAnimator;
import android.app.Dialog;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.window.BackEvent;
import android.window.OnBackAnimationCallback;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import java.util.function.Consumer;

final class AndroidWindowEffects {
    static Runnable registerBack(Window window, Runnable commit, Consumer<Float> preview, Runnable cancel) {
        OnBackInvokedDispatcher dispatcher = window.getOnBackInvokedDispatcher();
        OnBackInvokedCallback callback = Build.VERSION.SDK_INT >= 34 && preview != null
            ? PredictiveBack.callback(commit, preview, cancel) : commit::run;
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
        return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
    }

    static void animatePanelBack(Dialog dialog, Window window, View panel, boolean bottom, int travel) {
        Runnable unregister = registerBack(window, dialog::cancel, progress -> {
            if (!ValueAnimator.areAnimatorsEnabled()) return;
            panel.animate().cancel();
            if (bottom) panel.setTranslationY(travel * progress);
            else {
                panel.setScaleX(1f - .06f * progress);
                panel.setScaleY(1f - .06f * progress);
            }
            panel.setAlpha(1f - .28f * progress);
        }, () -> panel.animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f).setDuration(150).start());
        releaseOnDetach(window.getDecorView(), unregister);
    }

    static void blurPanelBackdrop(Window window, int radius) {
        WindowManager manager = window.getWindowManager();
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        Consumer<Boolean> update = enabled -> {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.setBlurBehindRadius(enabled ? radius : 0);
            attributes.dimAmount = enabled ? .22f : .42f;
            window.setAttributes(attributes);
        };
        manager.addCrossWindowBlurEnabledListener(window.getDecorView().getContext().getMainExecutor(), update);
        releaseOnDetach(window.getDecorView(), () -> manager.removeCrossWindowBlurEnabledListener(update));
    }

    private static void releaseOnDetach(View decor, Runnable release) {
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }

            @Override public void onViewDetachedFromWindow(View view) {
                view.removeOnAttachStateChangeListener(this);
                release.run();
            }
        });
    }

    private static final class PredictiveBack {
        static OnBackInvokedCallback callback(Runnable commit, Consumer<Float> preview, Runnable cancel) {
            return new OnBackAnimationCallback() {
                @Override public void onBackStarted(BackEvent event) { preview.accept(0f); }
                @Override public void onBackProgressed(BackEvent event) { preview.accept(event.getProgress()); }
                @Override public void onBackCancelled() { cancel.run(); }
                @Override public void onBackInvoked() { commit.run(); }
            };
        }
    }
}
