package com.seuapp;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Locale;

public class RideVoiceService extends AccessibilityService {

    public static final String ACTION_TEST_CLICK = "com.seuapp.action.TEST_CLICK";

    private WindowManager windowManager;
    private WindowManager.LayoutParams clickTargetParams;

    private TextView clickTarget;

    private final Handler scanHandler = new Handler();
    private TextToSpeech tts;

    private AppPrefs prefs;
    private int clickTargetSize = 28;
    private float dragStartRawX;
    private float dragStartRawY;
    private int dragStartX;
    private int dragStartY;
    private boolean targetMoved;
    private String lastOfferKey;
    private long lastOfferTime;
    private long lastFeedbackTime;
    private String lastFeedbackKey;
    private long lastTouchUpTime = 0;
    private final Handler singleTapHandler = new Handler();
    private final Runnable singleTapRunnable = new Runnable() {
        @Override
        public void run() {
            if (prefs != null && prefs.enabled()) {
                performAutomaticClick();
            }
        }
    };

    private final BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (intent != null && ACTION_TEST_CLICK.equals(intent.getAction())) {
                performAutomaticClick();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        prefs = new AppPrefs(this);

        IntentFilter commandFilter = new IntentFilter(ACTION_TEST_CLICK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, commandFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, commandFilter);
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        clickTarget = new TextView(this);
        clickTarget.setTextSize(18);
        clickTarget.setGravity(Gravity.CENTER);
        clickTarget.setPadding(8, 8, 8, 8);
        clickTarget.setContentDescription("Mira de clique automático");
        clickTarget.setOnTouchListener(this::onClickTargetTouch);

        clickTargetParams = new WindowManager.LayoutParams(
            dp(clickTargetSize),
            dp(clickTargetSize),
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                0
        );
        clickTargetParams.gravity = Gravity.TOP | Gravity.START;
        clickTargetParams.x = prefs.clickTargetX() >= 0 ? prefs.clickTargetX() : dp(24);
        clickTargetParams.y = prefs.clickTargetY() >= 0 ? prefs.clickTargetY() : dp(180);

        syncClickTarget();
        setTargetAppearance(Color.rgb(0, 230, 118), "•");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("pt", "BR"));
            }
        });
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            | AccessibilityEvent.TYPE_WINDOWS_CHANGED
                | AccessibilityEvent.TYPE_VIEW_CLICKED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;

        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.notificationTimeout = 100;
        info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;

        setServiceInfo(info);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (prefs == null || !prefs.enabled()) return;

        // CORREÇÃO CRÍTICA: Ignorar eventos vindos do próprio aplicativo!
        CharSequence eventPkg = event.getPackageName();
        if (eventPkg != null && getPackageName().equals(eventPkg.toString())) {
            return;
        }

        try {
            syncClickTarget();
            RecognizedOffer offer = OfferParser.parse(accessibilityText(event));
            if (offer != null) {
                String offerKey = String.format(Locale.US, "%.4f:%s", offer.pricePerKm, offer.pickupAddress);
                long now = System.currentTimeMillis();
                if (!offerKey.equals(lastOfferKey) || now - lastOfferTime > 5000) {
                    lastOfferKey = offerKey;
                    lastOfferTime = now;

                    String rating = offer.calculatedClassification(
                            prefs.excellentMinimum(),
                            prefs.goodMinimum()
                    );
                    String feedbackKey = rating + ":" + offerKey;
                    long nowFeedback = System.currentTimeMillis();
                    if (!feedbackKey.equals(lastFeedbackKey) || nowFeedback - lastFeedbackTime > 1200) {
                        lastFeedbackKey = feedbackKey;
                        lastFeedbackTime = nowFeedback;
                        applyOfferFeedback(offer);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String accessibilityText(AccessibilityEvent event) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            CharSequence rootPkg = root.getPackageName();
            if (rootPkg != null && getPackageName().equals(rootPkg.toString())) {
                root.recycle();
                return "";
            }
        }

        StringBuilder text = new StringBuilder();
        for (CharSequence item : event.getText()) {
            if (item != null) text.append(item).append(' ');
        }

        AccessibilityNodeInfo source = event.getSource();
        if (source != null) {
            CharSequence srcPkg = source.getPackageName();
            if (srcPkg != null && getPackageName().equals(srcPkg.toString())) {
                source.recycle();
                if (root != null) root.recycle();
                return "";
            }
            appendNodeText(source, text);
            source.recycle();
        }

        if (root != null) {
            appendNodeText(root, text);
            root.recycle();
        }
        return text.toString();
    }

    private void appendNodeText(AccessibilityNodeInfo node, StringBuilder text) {
        CharSequence nodeText = node.getText();
        if (nodeText != null) text.append(nodeText).append(' ');

        CharSequence description = node.getContentDescription();
        if (description != null) text.append(description).append(' ');

        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                appendNodeText(child, text);
                child.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() {
        if (prefs != null) {
            prefs.status("Serviço interrompido");
        }
    }

    private void syncClickTarget() {
        if (clickTarget == null || windowManager == null || prefs == null) return;

        boolean shouldShow = prefs.clickTarget();
        boolean isAttached = clickTarget.getParent() != null;

        if (shouldShow && !isAttached) {
            try {
                windowManager.addView(clickTarget, clickTargetParams);
            } catch (Exception ignored) {
            }
        } else if (!shouldShow && isAttached) {
            try {
                windowManager.removeView(clickTarget);
            } catch (Exception ignored) {
            }
        }
    }

    private boolean onClickTargetTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartRawX = event.getRawX();
                dragStartRawY = event.getRawY();
                dragStartX = clickTargetParams.x;
                dragStartY = clickTargetParams.y;
                targetMoved = false;
                return true;

            case MotionEvent.ACTION_MOVE:
                int maxX = Math.max(0, view.getResources().getDisplayMetrics().widthPixels - view.getWidth());
                int maxY = Math.max(0, view.getResources().getDisplayMetrics().heightPixels - view.getHeight());
                clickTargetParams.x = clamp(dragStartX + Math.round(event.getRawX() - dragStartRawX), 0, maxX);
                clickTargetParams.y = clamp(dragStartY + Math.round(event.getRawY() - dragStartRawY), 0, maxY);
                targetMoved = targetMoved
                    || Math.abs(event.getRawX() - dragStartRawX) > dp(4)
                    || Math.abs(event.getRawY() - dragStartRawY) > dp(4);

                if (clickTarget.getParent() != null) {
                    try {
                        windowManager.updateViewLayout(clickTarget, clickTargetParams);
                    } catch (Exception ignored) {
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                prefs.setClickTargetPosition(clickTargetParams.x, clickTargetParams.y);
                if (event.getActionMasked() == MotionEvent.ACTION_UP && !targetMoved) {
                    long now = System.currentTimeMillis();
                    if (now - lastTouchUpTime < 350) {
                        singleTapHandler.removeCallbacks(singleTapRunnable);
                        lastTouchUpTime = 0;
                        boolean newState = !prefs.enabled();
                        prefs.setEnabled(newState);
                        if (newState) {
                            setTargetAppearance(Color.rgb(0, 230, 118), "•");
                            Toast.makeText(this, "▶️ Robô Ativado!", Toast.LENGTH_SHORT).show();
                        } else {
                            setTargetAppearance(Color.GRAY, "⏸");
                            Toast.makeText(this, "⏸️ Robô Pausado!", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        lastTouchUpTime = now;
                        singleTapHandler.removeCallbacks(singleTapRunnable);
                        singleTapHandler.postDelayed(singleTapRunnable, 350);
                    }
                }
                return true;

            default:
                return false;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void performAutomaticClick() {
        performAutomaticClickWithCount(2);
    }

    private void performAutomaticClickWithCount(int tapCount) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            prefs.status("Clique automático requer Android 7.0 ou superior");
            return;
        }

        if (clickTarget == null || clickTarget.getWidth() <= 0 || clickTarget.getHeight() <= 0) {
            prefs.status("Mira ainda não está pronta para clicar");
            return;
        }

        int[] location = new int[2];
        clickTarget.getLocationOnScreen(location);
        boolean targetWasAttached = clickTarget.getParent() != null;

        setTargetAppearance(Color.YELLOW, "•");
        if (targetWasAttached) {
            try {
                windowManager.removeView(clickTarget);
            } catch (Exception exception) {
                prefs.status("Não foi possível liberar o ponto da mira");
                return;
            }
        }

        Path clickPath = new Path();
        clickPath.moveTo(
                location[0] + clickTarget.getWidth() / 2f,
                location[1] + clickTarget.getHeight() / 2f
        );

        Handler handler = new Handler();
        for (int i = 0; i < tapCount; i++) {
            handler.postDelayed(() -> dispatchSingleTap(clickPath), i * 150);
        }
        handler.postDelayed(() -> restoreClickTarget(targetWasAttached), (tapCount - 1) * 150 + 300);
    }

    private void dispatchSingleTap(Path clickPath) {
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 80))
                .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                prefs.status("Clique automático executado");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                prefs.status("Clique automático cancelado");
            }
        }, null);
    }

    private void restoreClickTarget(boolean targetWasAttached) {
        if (clickTarget == null) return;

        if (prefs != null && !prefs.enabled()) {
            setTargetAppearance(Color.GRAY, "⏸");
        } else {
            setTargetAppearance(Color.rgb(0, 230, 118), "•");
        }

        if (targetWasAttached && clickTarget.getParent() == null) {
            try {
                windowManager.addView(clickTarget, clickTargetParams);
            } catch (Exception ignored) {
            }
        }
    }

    private void setTargetAppearance(int color, String symbol) {
        if (clickTarget == null) return;

        if (prefs != null && !prefs.enabled() && color != Color.YELLOW) {
            color = Color.GRAY;
            symbol = "⏸";
        }

        clickTarget.setText(symbol);
        clickTarget.setTextColor(color);
        clickTarget.setAlpha(0.65f); // Anti-burn-in para tela AMOLED

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(Color.TRANSPARENT);
        shape.setStroke(Math.max(3, clickTargetSize / 18), color);
        clickTarget.setBackground(shape);
    }

    private void applyOfferFeedback(RecognizedOffer offer) {
        if (offer == null || clickTarget == null) return;

        String rating = offer.calculatedClassification(
            prefs.excellentMinimum(),
            prefs.goodMinimum()
        );

        if (rating.equals("RUIM")) {
            setTargetAppearance(Color.rgb(198, 35, 35), "•");
            new Handler().postDelayed(() -> setTargetAppearance(Color.rgb(0, 230, 118), "•"), 250);
            return;
        }

        int color = rating.equals("EXCELENTE")
                ? Color.rgb(255, 193, 7)
                : Color.rgb(0, 230, 118);

        setTargetAppearance(color, "•");
        new Handler().postDelayed(() -> setTargetAppearance(Color.rgb(0, 230, 118), "•"), 350);

        if (rating.equals("EXCELENTE") || rating.equals("BOA")) {
            performAutomaticClick();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        scanHandler.removeCallbacksAndMessages(null);

        try {
            unregisterReceiver(commandReceiver);
        } catch (Exception ignored) {
        }

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        if (windowManager != null && clickTarget != null) {
            try {
                windowManager.removeView(clickTarget);
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }
}
