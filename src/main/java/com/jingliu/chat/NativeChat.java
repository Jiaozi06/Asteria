package com.jingliu.chat;

import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.jingliu.chat.ChatState.*;
import static com.jingliu.chat.MainActivity.json;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

final class NativeChat {
    private final MainActivity host;
    private final FrameLayout root;
    private final MainActivity.Services services;
    private final ChatState state;
    private final Set<JSONObject> unfolded = new HashSet<>();
    private final Map<String, ImageButton> speechButtons = new HashMap<>();
    private String speaking = "";
    private String offeredUpdate = "";
    private Context context;
    private boolean dark, hasKey, upload, recording, transcribing, renderPending, checking;
    private boolean messagesDirty;
    private boolean voiceMode, holdingVoice;
    private View voiceTouchTarget;
    private Runnable voiceHoldStart;
    private float voiceDownX, voiceDownY;
    private boolean voiceTouchConsumed, voiceCancel;
    private String heldVoiceText = "";
    private String keyStatus = "", pendingUrl, pendingModel;
    private ApiProvider pendingProvider, providerDraft;
    private ApiProtocol pendingProtocol, protocolDraft;
    private int pendingContextWindow;
    private String contextWindowDraft = "";
    private String providerNameDraft, pendingProviderName, keyManagementEndpoint;
    private int screen;
    private JSONObject editedOptionsChat;
    private EditText instructionEditor;
    private String instructionDraft = "";
    private static final int CHAT = 0, SETTINGS = 1, API_SETTINGS = 2, FONT_SETTINGS = 3, MODEL_SETTINGS = 4, KEY_SETTINGS = 5, ABOUT = 6, INSTRUCTION_SETTINGS = 7;
    private static final int EXPERIMENTS = 8;
    private static final int NEBULA = 9;
    private static final String PREDICTIVE_BACK = "experimentalPredictiveBack";
    private static final String BLUR_PANELS = "experimentalBlurPanels";
    private static final String GLASS_COMPOSER = "experimentalGlassComposer";
    private enum Motion { NONE, PUSH, POP, FADE }
    private enum NebulaRange {
        DAY(1, "最近 1 天"), WEEK(7, "最近 7 天"), MONTH(30, "最近 30 天"), ALL(0, "全部");

        final int days;
        final String label;

        NebulaRange(int days, String label) {
            this.days = days;
            this.label = label;
        }
    }
    private static final PathInterpolator EASE = new PathInterpolator(.22f, .75f, .23f, 1f);
    private View screenView;
    private final android.util.SparseArray<Bitmap> backPages = new android.util.SparseArray<>();
    private ImageView backPeek;
    private View backMoving;
    private int renderedScreen = CHAT;
    private boolean committingSystemBack;
    private HistoryDrawer drawer;
    private boolean settingsFromDrawer, apiFromSettings, managingHistory;
    private Dialog historyImportProgress;
    private int settingsReturnScroll = -1;
    private String historyFilter = "";
    private Set<String> historyBodyMatches = Collections.emptySet();
    private int historySearchRequest;
    private Runnable historySearchTask;
    private final Set<String> selectedChats = new HashSet<>();
    private LinearLayout historyPanel, historyBrowse, historyManage, historyShortcuts, historySelection;
    private EditText historySearch;
    private TextView historyCount, historyPin, historyDelete, historyEmpty;
    private HistorySelectionList historyList;
    private HistoryAdapter historyAdapter;
    private List<MemoryNebula.Entry> nebulaEntries = Collections.emptyList();
    private MemoryNebula.Kind nebulaCategory;
    private boolean nebulaWorking;
    private boolean nebulaHasLegacy;
    private AtomicBoolean nebulaStop;
    private MemoryNebula.Progress nebulaProgressState;
    private int nebulaJobId;
    private Runnable nebulaAfterReply;
    private int nebulaLimit = 12;
    private String nebulaStatus = "";
    private NebulaSky nebulaSky;
    private ScrollView nebulaScroll;
    private HorizontalScrollView nebulaFilterStrip;
    private LinearLayout nebulaFilters, nebulaCards;
    private ProgressBar nebulaProgressBar;
    private TextView nebulaCount, nebulaFeedback, nebulaScan, nebulaClear;
    private int bg, surface, ink, muted, edge, blue, tint, blueSolid;
    private LinearLayout layout, messages, attachments, tools, attachmentPanel;
    private HorizontalScrollView attachmentStrip;
    private TextView attachmentStatus;
    private JSONObject pendingAttachment;
    private Bitmap pendingThumbnail;
    private final android.util.LruCache<String, Bitmap> attachmentThumbnails = new android.util.LruCache<>(24);
    private boolean attachmentPanelOpen;
    private LinearLayout chatFooter, editHeader;
    private View editShade;
    private EditSession editing;
    private ScrollView transcript;
    private TextView welcome, chatTitle, chatModel, think, search, keyFeedback, verify, saveOnly, manageKeys;
    private TextView balanceFooter;
    private String balanceLabel = "";
    private long balanceRefreshAfter;
    private int balanceConfig;
    private boolean balanceRunning, balanceRefreshPending;
    private final Runnable fetchBalance = this::fetchBalance;
    private TextView voicePad;
    private FrameLayout voiceOverlay;
    private VoiceHalo voiceHalo;
    private TextView voiceHint;
    private ImageButton send, mic, add, returnBottom;
    private EditText input, apiKey, apiUrl, apiModel, apiWindow, providerName;
    private LinearLayout modelRow;
    private ImageButton modelArrow;
    private TextView modelsFeedback, refreshModels, providerRow, protocolRow, resetAddress;
    private String[] upstreamModels = {};
    private String modelsStatus = "";
    private Runnable modelLookup;
    private int modelLookupId;
    private String draft = "";
    private String urlDraft, modelDraft, keyDraft = "";
    private LinearLayout live;
    private JSONObject liveReply;
    private String liveText = "";
    private MarkdownReply liveAnswer;
    private TextView liveStatus, liveSourceLink;
    private ThinkingPanel liveThinking;

    private static final class EditSession {
        final int index;
        final String normalDraft;
        final List<JSONObject> normalFiles;
        boolean think;
        boolean search;

        EditSession(int index, String normalDraft, List<JSONObject> files, boolean think, boolean search) {
            this.index = index;
            this.normalDraft = normalDraft;
            this.normalFiles = new ArrayList<>(files);
            this.think = think;
            this.search = search;
        }
    }

    NativeChat(MainActivity host, FrameLayout root, MainActivity.Services services, JSONObject stored, boolean coldStart) {
        this.host = host;
        this.root = root;
        this.services = services;
        state = new ChatState(stored);
        voiceMode = services.voiceInputMode();
        if (coldStart && state.current() != null) {
            state.activate(null);
            save();
        } else if (state.current() != null && !loadConversation(state.current())) {
            state.activate(null);
        }
        updateKey();
    }

    private void updateKey() {
        String endpoint = state.endpoint();
        hasKey = !state.effectiveModel().isEmpty() && (!state.custom() || !endpoint.isEmpty())
            && (!ApiProvider.requiresKey(endpoint) || services.hasKey(endpoint));
        invalidateBalance();
    }

    private boolean supportsBalance() {
        return hasKey && state.apiProvider() == ApiProvider.DEEPSEEK && state.endpoint().isEmpty();
    }

    private void invalidateBalance() {
        balanceConfig++;
        balanceRefreshAfter = 0;
        balanceRefreshPending = false;
        balanceLabel = "";
        root.removeCallbacks(fetchBalance);
        updateBalanceFooter();
        refreshBalance(false);
    }

    void refreshBalance(boolean afterReply) {
        if (afterReply) balanceRefreshAfter = 0;
        if (screen != CHAT || !supportsBalance() || SystemClock.uptimeMillis() < balanceRefreshAfter) return;
        root.removeCallbacks(fetchBalance);
        root.postDelayed(fetchBalance, afterReply ? 900 : 0);
    }

    private void fetchBalance() {
        if (screen != CHAT || !supportsBalance()) return;
        if (balanceRunning) {
            balanceRefreshPending = true;
            return;
        }
        balanceRunning = true;
        balanceRefreshAfter = SystemClock.uptimeMillis() + 60_000;
        int config = balanceConfig;
        services.deepSeekBalance(label -> {
            balanceRunning = false;
            if (config == balanceConfig && supportsBalance()) {
                balanceLabel = label;
                updateBalanceFooter();
            }
            if (balanceRefreshPending) {
                balanceRefreshPending = false;
                refreshBalance(false);
            }
        });
    }

    private void updateBalanceFooter() {
        if (balanceFooter == null) return;
        balanceFooter.setText(balanceLabel);
        balanceFooter.setVisibility(balanceLabel.isEmpty() || !supportsBalance() ? View.GONE : View.VISIBLE);
    }

    private void colors() {
        String preference = state.data.optString("theme", "system");
        dark = "dark".equals(preference) || ("system".equals(preference) && host.systemDark());
        context = new ContextThemeWrapper(host, dark ? R.style.Jingliu_Dark : R.style.Jingliu_Light);
        bg = dark ? 0xff101216 : 0xffffffff;
        surface = dark ? 0xff1b1f26 : 0xfff5f7fb;
        ink = dark ? 0xfff0f3fa : 0xff172235;
        muted = dark ? 0xffaab5c7 : 0xff526078;
        edge = dark ? 0xff303947 : 0xffdfe6f1;
        blue = dark ? 0xff83aaff : 0xff255ed0;
        tint = dark ? 0xff202f4d : 0xffeaf2ff;
        blueSolid = dark ? 0xff77a1ff : 0xff3268df;
        host.bars(dark, bg);
    }

    void refreshTheme() {
        if (screen == INSTRUCTION_SETTINGS && instructionEditor != null) {
            instructionDraft = instructionEditor.getText().toString();
        }
        if (screen == API_SETTINGS) {
            captureEndpointFields();
            keyDraft = apiKey.getText().toString();
        }
        show(Motion.NONE, screen == CHAT && drawer != null && drawer.isRevealed());
    }

    void attachmentPickerPending() { upload = true; }

    void show() { show(Motion.NONE, false); }

    private void show(Motion motion, boolean reopenDrawer) {
        colors();
        View previous = screenView;
        boolean continueBack = committingSystemBack && motion == Motion.POP && previous != null
            && previous == backMoving && previous.getTranslationX() != 0f;
        float exitDirection = continueBack ? Math.signum(previous.getTranslationX()) : 1f;
        clearPageBackPreview(continueBack);
        if (motion == Motion.PUSH && Build.VERSION.SDK_INT >= 34 && ValueAnimator.areAnimatorsEnabled()
            && (state.data.optBoolean(PREDICTIVE_BACK) || screen == SETTINGS || screen == EXPERIMENTS)) {
            captureBackPage(previous);
        }
        if (motion == Motion.POP) backPages.remove(renderedScreen);
        if (screen == CHAT || motion == Motion.NONE) backPages.clear();
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            View child = root.getChildAt(i);
            child.animate().withEndAction(null).cancel();
            if (child != previous) root.removeView(child);
        }
        if (previous != null && !continueBack) {
            previous.setTranslationX(0);
            previous.setAlpha(1f);
            previous.setScaleX(1f);
            previous.setScaleY(1f);
        }
        layout = vertical();
        layout.setBackgroundColor(bg);
        layout.setFocusableInTouchMode(true);
        if (screen == CHAT) {
            buildChat();
            buildHistory();
            FrameLayout chatCard = new FrameLayout(context);
            chatCard.addView(layout, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            voiceOverlay = new FrameLayout(context);
            voiceHalo = new VoiceHalo(context, dark);
            voiceOverlay.addView(voiceHalo, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            voiceHint = text("", 14, dark ? 0xfff3f6ff : 0xff263959);
            pad(voiceHint, 13, 4);
            voiceHint.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
            FrameLayout.LayoutParams hint = new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            hint.bottomMargin = dp(33);
            voiceOverlay.addView(voiceHint, hint);
            voiceOverlay.setVisibility(View.GONE);
            chatCard.addView(voiceOverlay, new FrameLayout.LayoutParams(MATCH_PARENT, dp(236), Gravity.BOTTOM));
            drawer = new HistoryDrawer(context, chatCard, historyPanel, historyBackdrop(), bg, dark ? 0x55000000 : 0x12b0bdd3,
                this::onHistoryOpening, this::onHistoryClosed);
            screenView = drawer;
        } else {
            if (screen == API_SETTINGS) buildApiSettings();
            else if (screen == KEY_SETTINGS) buildKeySettings();
            else if (screen == FONT_SETTINGS) buildFontSettings();
            else if (screen == MODEL_SETTINGS) buildModelSettings();
            else if (screen == ABOUT) buildAbout();
            else if (screen == INSTRUCTION_SETTINGS) buildInstructionSettings();
            else if (screen == EXPERIMENTS) buildExperiments();
            else if (screen == NEBULA) buildNebula();
            else buildSettingsHome();
            screenView = layout;
        }
        root.addView(screenView, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        renderedScreen = screen;
        if (screen == CHAT && reopenDrawer) drawer.restoreOpen();
        host.updateSystemBack();
        refreshBalance(false);
        if (previous == null) return;
        if (motion == Motion.NONE || !ValueAnimator.areAnimatorsEnabled() || root.getWidth() == 0) {
            root.removeView(previous);
            return;
        }
        previous.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        screenView.setTranslationX(0);
        screenView.setAlpha(1f);
        if (motion == Motion.POP) {
            previous.bringToFront();
            previous.animate().translationX(root.getWidth() * exitDirection).alpha(.8f).setInterpolator(EASE)
                .setDuration(continueBack ? 170 : 245)
                .withEndAction(() -> root.removeView(previous)).start();
        } else {
            if (motion == Motion.PUSH) screenView.setTranslationX(root.getWidth());
            else screenView.setAlpha(.25f);
            screenView.animate().translationX(0).alpha(1f).setDuration(motion == Motion.PUSH ? 270 : 180).setInterpolator(EASE)
                .withEndAction(() -> root.removeView(previous)).start();
        }
    }

    private void save() {
        persist();
    }

    private boolean persist() {
        if (services.saveState(state.data, messagesDirty)) {
            messagesDirty = false;
            return true;
        }
        toast("本地记录写入失败。");
        return false;
    }

    private void saveMessages() {
        messagesDirty = true;
        save();
    }

    boolean handlesBack() {
        return screen != CHAT || (drawer != null && drawer.isRevealed()) || attachmentPanelOpen || editing != null || voiceMode;
    }

    private void captureBackPage(View page) {
        if (page == null || page.getWidth() == 0 || page.getHeight() == 0) return;
        float scale = Math.min(1f, 800f / Math.max(page.getWidth(), page.getHeight()));
        Bitmap preview = Bitmap.createBitmap(Math.max(1, Math.round(page.getWidth() * scale)),
            Math.max(1, Math.round(page.getHeight() * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(preview);
        canvas.scale(scale, scale);
        try {
            page.draw(canvas);
            backPages.put(screen, preview);
        } catch (IllegalArgumentException | IllegalStateException cannotRender) {
            backPages.remove(screen);
            preview.recycle();
        }
    }

    void previewBack(float progress, boolean fromRight) {
        if (screenView == null || !state.data.optBoolean(PREDICTIVE_BACK) || !ValueAnimator.areAnimatorsEnabled()) return;
        if (screen == CHAT) {
            if (drawer != null && !managingHistory) drawer.previewBack(progress);
            return;
        }
        float direction = fromRight ? -1f : 1f;
        if (backMoving == null || progress == 0f) {
            if (backMoving != null) {
                backMoving.animate().withEndAction(null).cancel();
                clearPageBackPreview(false);
            }
            backMoving = screenView;
            backMoving.animate().withEndAction(null).cancel();
            for (int i = root.getChildCount() - 1; i >= 0; i--) {
                View child = root.getChildAt(i);
                if (child != backMoving) {
                    child.animate().withEndAction(null).cancel();
                    root.removeViewAt(i);
                }
            }
            Bitmap preview = backPages.get(screen);
            if (preview != null) {
                backPeek = new ImageView(context);
                backPeek.setScaleType(ImageView.ScaleType.FIT_XY);
                backPeek.setImageBitmap(preview);
                backPeek.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                root.addView(backPeek, 0, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
            }
        }
        backMoving.setTranslationX(direction * root.getWidth() * .32f * progress);
        backMoving.setScaleX(1f - .08f * progress);
        backMoving.setScaleY(1f - .08f * progress);
        backMoving.setAlpha(1f - .10f * progress);
        if (backPeek != null) backPeek.setAlpha(.68f + .32f * progress);
    }

    void cancelBackPreview() {
        if (screen == CHAT && drawer != null) {
            drawer.cancelBackPreview();
        } else if (backMoving != null) {
            backMoving.animate().translationX(0).scaleX(1f).scaleY(1f).alpha(1f)
                .setInterpolator(EASE).setDuration(160).withEndAction(() -> clearPageBackPreview(false)).start();
        }
    }

    private void clearPageBackPreview(boolean keepPose) {
        if (backPeek != null) root.removeView(backPeek);
        backPeek = null;
        if (backMoving != null && !keepPose) {
            backMoving.setTranslationX(0);
            backMoving.setScaleX(1f);
            backMoving.setScaleY(1f);
            backMoving.setAlpha(1f);
        }
        backMoving = null;
    }

    void commitSystemBack() {
        committingSystemBack = true;
        try { back(); }
        finally { committingSystemBack = false; }
    }

    boolean back() {
        if (screen != CHAT) {
            services.hideKeyboard();
            if (screen == KEY_SETTINGS) {
                screen = API_SETTINGS;
                keyManagementEndpoint = null;
                show(Motion.POP, false);
                queueModelLookup();
                return true;
            }
            if (screen == INSTRUCTION_SETTINGS) {
                closeInstructionSettings();
                return true;
            }
            if (screen == MODEL_SETTINGS) {
                editedOptionsChat = null;
                returnToChat(false);
                return true;
            }
            if (screen == FONT_SETTINGS || screen == ABOUT || screen == EXPERIMENTS || screen == NEBULA) {
                screen = SETTINGS;
                show(Motion.POP, false);
                return true;
            }
            if (screen == API_SETTINGS) {
                urlDraft = modelDraft = null;
                keyDraft = "";
                stopModelLookup();
                if (apiFromSettings) {
                    screen = SETTINGS;
                    show(Motion.POP, false);
                    return true;
                }
            }
            returnToChat(settingsFromDrawer);
            return true;
        }
        if (drawer != null && drawer.isRevealed()) {
            services.hideKeyboard();
            if (managingHistory) setHistoryMode(false);
            else drawer.close();
            return true;
        }
        if (attachmentPanelOpen) {
            setAttachmentPanel(false, true);
            return true;
        }
        if (editing != null) { closeEdit(); return true; }
        if (voiceMode) {
            if (recording || transcribing) services.endVoice(true);
            useVoiceInput(false);
            controls();
            return true;
        }
        return false;
    }

    private void returnToChat(boolean openDrawer) {
        screen = CHAT;
        show(Motion.POP, openDrawer);
        if (settingsReturnScroll >= 0) {
            int offset = settingsReturnScroll;
            afterTranscriptLayout(view -> view.scrollTo(0, offset));
        }
    }

    private int dp(float value) { return Math.round(value * host.getResources().getDisplayMetrics().density); }
    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout horizontal() {
        LinearLayout view = new LinearLayout(context);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(fill);
        result.setCornerRadius(dp(radius));
        if (stroke != 0) result.setStroke(dp(1), stroke);
        return result;
    }

    private GradientDrawable userBubble(int fill) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        float round = dp(18);
        float corner = dp(4);
        shape.setCornerRadii(new float[]{round, round, round, round, corner, corner, round, round});
        return shape;
    }
    private void buttonBackground(View view, int fill, int stroke, int radius) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x28a7c3ff : 0x203268df),
            box(fill, stroke, radius), box(0xffffffff, 0, radius)));
    }
    private void pad(View view, int x, int y) { view.setPadding(dp(x), dp(y), dp(x), dp(y)); }
    private void gap(LinearLayout into, int height) { into.addView(new View(context), MATCH_PARENT, dp(height)); }
    private void weight(LinearLayout row) { row.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1)); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private void bold(TextView view) { view.setTypeface(null, Typeface.BOLD); }
    private TextView action(String label, Runnable run) {
        TextView view = text(label, 14, blue);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(44));
        pad(view, 12, 5);
        buttonBackground(view, 0, 0, 12);
        view.setOnClickListener(v -> run.run());
        return view;
    }
    private ImageButton icon(int res, String label, Runnable run) {
        ImageButton view = new ImageButton(context);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(res);
        view.setImageTintList(ColorStateList.valueOf(blue));
        view.setContentDescription(label);
        buttonBackground(view, 0, 0, 14);
        view.setOnClickListener(v -> run.run());
        return view;
    }
    private EditText field(String hint, boolean password) {
        EditText view = new EditText(context);
        view.setTextSize(15);
        view.setTextColor(ink);
        view.setHintTextColor(muted);
        view.setHint(hint);
        view.setSingleLine(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD :
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        if (password) view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        pad(view, 14, 8);
        view.setMinHeight(dp(52));
        view.setBackground(box(surface, edge, 14));
        return view;
    }
    private void divider(LinearLayout parent) {
        View line = new View(context);
        line.setBackgroundColor(edge);
        parent.addView(line, MATCH_PARENT, dp(1));
    }
    private void header(String title, int leftIcon, String leftName, Runnable left, boolean chat) {
        FrameLayout bar = new FrameLayout(context);
        layout.addView(bar, MATCH_PARENT, dp(58));
        FrameLayout.LayoutParams leading = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.START | Gravity.CENTER_VERTICAL);
        leading.setMarginStart(dp(8));
        bar.addView(icon(leftIcon, leftName, left), leading);

        LinearLayout titles = vertical();
        titles.setGravity(Gravity.CENTER_VERTICAL);
        int alignment = chat ? Gravity.START : Gravity.CENTER;
        TextView heading = text(title, chat ? 16 : 18, ink);
        bold(heading);
        heading.setGravity(alignment | Gravity.CENTER_VERTICAL);
        heading.setSingleLine();
        heading.setEllipsize(TextUtils.TruncateAt.END);
        if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        titles.addView(heading, MATCH_PARENT, WRAP_CONTENT);
        if (chat) {
            chatTitle = heading;
            chatModel = text("", 11, dark ? 0xff9ba2ad : 0xff667180);
            chatModel.setSingleLine();
            chatModel.setEllipsize(TextUtils.TruncateAt.END);
            titles.addView(chatModel, MATCH_PARENT, WRAP_CONTENT);
        }
        FrameLayout.LayoutParams position = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.START | Gravity.CENTER_VERTICAL);
        position.setMarginStart(dp(61));
        boolean showLog = chat && services.requestLoggingEnabled();
        position.setMarginEnd(dp(chat ? (showLog ? 148 : 104) : 61));
        bar.addView(titles, position);
        if (chat) {
            FrameLayout.LayoutParams trailing = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
            trailing.setMarginEnd(dp(8));
            bar.addView(icon(R.drawable.ic_new_chat, "新对话", () -> activate(null)), trailing);
            if (showLog) {
                FrameLayout.LayoutParams logs = new FrameLayout.LayoutParams(dp(44), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
                logs.setMarginEnd(dp(56));
                bar.addView(icon(R.drawable.ic_request_log, "请求日志", this::showRequestLogs), logs);
            }
            FrameLayout.LayoutParams options = new FrameLayout.LayoutParams(dp(44), dp(48), Gravity.END | Gravity.CENTER_VERTICAL);
            options.setMarginEnd(dp(showLog ? 100 : 56));
            bar.addView(icon(R.drawable.ic_settings, "对话高级设置", this::openChatOptions), options);
        }
        divider(layout);
    }

    private void buildChat() {
        transcript = new ScrollView(context);
        transcript.setFillViewport(true);
        transcript.setClipToPadding(false);
        transcript.setVerticalScrollBarEnabled(false);
        header("新对话", R.drawable.ic_menu, "对话历史", () -> drawer.open(), true);
        FrameLayout chatBody = new FrameLayout(context);
        layout.addView(chatBody, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        chatBody.addView(transcript, MATCH_PARENT, MATCH_PARENT);
        editShade = new View(context);
        editShade.setOnClickListener(v -> closeEdit());
        editShade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        chatBody.addView(editShade, MATCH_PARENT, MATCH_PARENT);
        returnBottom = icon(R.drawable.ic_chevron, "回到底部", this::smoothBottom);
        buttonBackground(returnBottom, surface, edge, 24);
        returnBottom.setElevation(dp(3));
        returnBottom.setTooltipText("回到底部");
        returnBottom.setVisibility(View.GONE);
        FrameLayout.LayoutParams jump = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.BOTTOM | Gravity.END);
        jump.setMarginEnd(dp(14));
        jump.bottomMargin = dp(10);
        chatBody.addView(returnBottom, jump);
        transcript.setOnScrollChangeListener((view, x, y, oldX, oldY) -> {
            if (view == transcript) updateReturnBottom();
        });
        transcript.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (view == transcript) updateReturnBottom();
        });
        FrameLayout content = new FrameLayout(context);
        transcript.addView(content, MATCH_PARENT, WRAP_CONTENT);
        content.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (view.getParent() == transcript) updateReturnBottom();
        });
        LinearLayout greeting = vertical();
        greeting.setGravity(Gravity.CENTER);
        ImageView mark = new ImageView(context);
        mark.setImageResource(R.drawable.ic_mark);
        mark.setImageTintList(ColorStateList.valueOf(blue));
        mark.setScaleType(ImageView.ScaleType.CENTER);
        mark.setBackground(box(tint, 0, 23));
        greeting.addView(mark, dp(70), dp(70));
        gap(greeting, 16);
        welcome = text("今天，聊点什么？", 24, ink);
        bold(welcome);
        welcome.setGravity(Gravity.CENTER);
        greeting.addView(welcome, WRAP_CONTENT, WRAP_CONTENT);
        content.addView(greeting, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER));

        messages = vertical();
        pad(messages, 18, 16);
        content.addView(messages, MATCH_PARENT, WRAP_CONTENT);
        LinearLayout footer = chatFooter = vertical();
        pad(footer, 13, 0);
        boolean glass = state.data.optBoolean(GLASS_COMPOSER) && GlassComposer.supported();
        if (glass) {
            chatBody.addView(footer, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM));
            ScrollView scroll = transcript;
            footer.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
                if (transcript != scroll || content.getPaddingBottom() == b - t) return;
                boolean follow = nearBottom();
                int overlap = b - t;
                content.setPadding(0, 0, 0, overlap);
                jump.bottomMargin = overlap + dp(10);
                returnBottom.setLayoutParams(jump);
                if (follow) bottom();
            });
        } else {
            layout.addView(footer, MATCH_PARENT, WRAP_CONTENT);
        }

        LinearLayout composer = glass ? new GlassComposer(context, dark, surface, edge, dp(20)) : vertical();
        pad(composer, 7, 5);
        if (!glass) composer.setBackground(box(surface, edge, 20));
        footer.addView(composer, MATCH_PARENT, WRAP_CONTENT);
        editHeader = horizontal();
        editHeader.setBackground(box(dark ? 0xff252e40 : 0xffeaf2ff, 0, 13));
        TextView editTitle = text("修改输入", 14, ink);
        bold(editTitle);
        pad(editTitle, 10, 0);
        editHeader.addView(editTitle, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        editHeader.addView(icon(R.drawable.ic_close, "取消修改", this::closeEdit), dp(43), dp(42));
        composer.addView(editHeader, MATCH_PARENT, dp(42));
        attachmentStrip = new HorizontalScrollView(context);
        attachmentStrip.setHorizontalScrollBarEnabled(false);
        attachments = horizontal();
        pad(attachments, 3, 4);
        attachmentStrip.addView(attachments, WRAP_CONTENT, WRAP_CONTENT);
        composer.addView(attachmentStrip, MATCH_PARENT, WRAP_CONTENT);
        attachmentStatus = text("正在上传附件…", 13, blue);
        pad(attachmentStatus, 6, 3);
        composer.addView(attachmentStatus, MATCH_PARENT, WRAP_CONTENT);
        input = new EditText(context);
        ChatFont.apply(input, 16, ChatFont.scale(state.data));
        input.setTextColor(ink);
        input.setHintTextColor(muted);
        input.setHint("发消息或按住说话");
        input.setContentDescription("消息");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(5);
        input.setMinHeight(dp(44));
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setBackground(null);
        pad(input, 8, 9);
        input.setText(draft);
        input.setSelection(input.length());
        input.setOnFocusChangeListener((view, focused) -> {
            if (focused) setAttachmentPanel(false, true);
            updateInputHint();
        });
        input.setOnTouchListener((view, event) -> voiceTouch(view, event, false));
        composer.addView(input, MATCH_PARENT, WRAP_CONTENT);
        voicePad = action("按住说话", () -> {
            if (recording) services.endVoice(false);
            else startVoice(false);
        });
        voicePad.setOnTouchListener((view, event) -> voiceTouch(view, event, true));
        composer.addView(voicePad, MATCH_PARENT, dp(45));

        tools = horizontal();
        composer.addView(tools, MATCH_PARENT, WRAP_CONTENT);
        think = action("深度思考", () -> toggle("think"));
        search = action("智能搜索", () -> toggle("search"));
        chipIcon(think, R.drawable.ic_insight);
        chipIcon(search, R.drawable.ic_globe);
        think.setTextSize(12);
        search.setTextSize(12);
        setChip(think, false);
        setChip(search, false);
        addToolChip(think);
        addToolChip(search);
        weight(tools);
        add = icon(R.drawable.ic_attach, "添加附件", this::showAttachments);
        mic = icon(R.drawable.ic_mic, "切换到语音输入", () -> {
            if (recording || transcribing) return;
            setAttachmentPanel(false, true);
            useVoiceInput(!voiceMode);
            input.clearFocus();
            services.hideKeyboard();
            controls();
        });
        send = icon(R.drawable.ic_send, "发送消息", this::submit);
        tools.addView(add, dp(42), dp(44));
        tools.addView(mic, dp(42), dp(44));
        LinearLayout.LayoutParams sendSize = new LinearLayout.LayoutParams(dp(42), dp(42));
        sendSize.setMarginStart(dp(6));
        tools.addView(send, sendSize);
        attachmentPanel = horizontal();
        pad(attachmentPanel, 1, 7);
        attachmentAction("拍照", R.drawable.ic_camera, () -> selectAttachment(services::takePhoto));
        attachmentAction("相册", R.drawable.ic_photo, () -> selectAttachment(key -> services.pickFile(true, key)));
        attachmentAction("文件", R.drawable.ic_paperclip, () -> selectAttachment(key -> services.pickFile(false, key)));
        footer.addView(attachmentPanel, MATCH_PARENT, WRAP_CONTENT);
        setAttachmentPanel(attachmentPanelOpen, false);
        int footerColor = dark ? 0xff9299a3 : 0xff697486;
        TextView note = text("回答由 AI 生成，仅供参考", 11, footerColor);
        note.setGravity(Gravity.CENTER);
        TextView balance = balanceFooter = text("", 11, footerColor);
        balance.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        FrameLayout footnote = new FrameLayout(context) {
            private boolean separateLine;
            private int firstLineHeight;

            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int width = MeasureSpec.getSize(widthSpec);
                int available = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
                int naturalHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                note.measure(available, naturalHeight);
                boolean showBalance = balance.getVisibility() != GONE;
                if (showBalance) balance.measure(available, naturalHeight);
                separateLine = showBalance && balance.getMeasuredWidth() + dp(8) > (width - note.getMeasuredWidth()) / 2;
                int sameLineBalanceHeight = showBalance && !separateLine ? balance.getMeasuredHeight() : 0;
                firstLineHeight = Math.max(dp(24), Math.max(note.getMeasuredHeight(), sameLineBalanceHeight));
                setMeasuredDimension(width, firstLineHeight + (separateLine ? balance.getMeasuredHeight() : 0));
            }

            @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                int width = right - left;
                int noteLeft = (width - note.getMeasuredWidth()) / 2;
                int noteTop = (firstLineHeight - note.getMeasuredHeight()) / 2;
                note.layout(noteLeft, noteTop, noteLeft + note.getMeasuredWidth(), noteTop + note.getMeasuredHeight());
                if (balance.getVisibility() == GONE) return;
                int balanceTop = separateLine ? firstLineHeight : (firstLineHeight - balance.getMeasuredHeight()) / 2;
                balance.layout(width - balance.getMeasuredWidth(), balanceTop, width, balanceTop + balance.getMeasuredHeight());
            }
        };
        footnote.addView(note, WRAP_CONTENT, WRAP_CONTENT);
        footnote.addView(balance, WRAP_CONTENT, WRAP_CONTENT);
        if (glass) footnote.setBackground(box(dark ? 0xeb121722 : 0xedffffff, 0, 7));
        footer.addView(footnote, MATCH_PARENT, WRAP_CONTENT);
        updateBalanceFooter();

        watch(input, value -> { draft = value; controls(); });
        renderMessages(true);
        updateEditLayout(false);
        controls();
    }

    private void addToolChip(TextView chip) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        params.setMarginEnd(dp(5));
        tools.addView(chip, params);
    }
    private void setChip(TextView chip, boolean selected) {
        chip.setSelected(selected);
        chip.setTextColor(selected ? blue : muted);
        chip.setCompoundDrawableTintList(ColorStateList.valueOf(selected ? blue : muted));
        buttonBackground(chip, selected ? tint : surface, selected ? blue : edge, 22);
        chip.setMinHeight(dp(34));
        pad(chip, 9, 4);
    }

    private void chipIcon(TextView chip, int res) {
        Drawable drawable = context.getDrawable(res);
        drawable.setBounds(0, 0, dp(15), dp(15));
        chip.setCompoundDrawablesRelative(drawable, null, null, null);
        chip.setCompoundDrawablePadding(dp(4));
    }
    private void toggle(String option) {
        if (!state.deepSeekFeatures() && (!"think".equals(option) || !hasThinkingToggle())) {
            if ("think".equals(option)) {
                openChatOptions();
            } else {
                composerNotice("智能搜索", "当前的联网搜索使用 DeepSeek 官方接口。", "API 设置", this::openApiSettings);
            }
            return;
        }
        if (editing != null) {
            if ("think".equals(option)) editing.think = !editing.think;
            else editing.search = !editing.search;
        } else {
            if ("think".equals(option)) state.toggleThinking();
            else put(state.data, option, !state.data.optBoolean(option));
            save();
        }
        controls();
    }

    private boolean hasThinkingToggle() {
        return state.deepSeekFeatures() || state.apiProtocol() == ApiProtocol.GEMINI || state.apiProtocol() == ApiProtocol.OLLAMA;
    }

    private boolean startVoice(boolean hold) {
        if (busy() || (state.deepSeekFeatures() && !requireKey())) return false;
        setAttachmentPanel(false, true);
        return services.beginVoice(!state.deepSeekFeatures(), hold);
    }

    void updateInputHint() {
        if (screen != CHAT || input == null) return;
        String hint = editing != null ? "修改消息" : input.hasFocus() && services.keyboardVisible()
            ? "发消息" : "发消息或按住说话";
        if (!hint.contentEquals(input.getHint() == null ? "" : input.getHint())) input.setHint(hint);
    }

    private boolean voiceTouch(View view, MotionEvent event, boolean immediate) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!immediate && (editing != null || !draft.isEmpty() || busy())) return false;
            if (immediate && recording && !holdingVoice) {
                services.endVoice(false);
                return true;
            }
            voiceTouchTarget = view;
            voiceDownX = event.getRawX();
            voiceDownY = event.getRawY();
            voiceTouchConsumed = immediate;
            view.getParent().requestDisallowInterceptTouchEvent(true);
            voiceHoldStart = () -> {
                if (voiceTouchTarget != view || (!immediate && voiceTouchConsumed)) return;
                voiceTouchConsumed = true;
                voiceCancel = false;
                heldVoiceText = "";
                holdingVoice = true;
                holdingVoice = startVoice(true);
                if (holdingVoice) {
                    services.hideKeyboard();
                    input.clearFocus();
                    updateVoiceOverlay(false);
                }
            };
            if (immediate) voiceHoldStart.run();
            else view.postDelayed(voiceHoldStart, ViewConfiguration.getLongPressTimeout());
            return true;
        }
        if (voiceTouchTarget != view) return false;
        if (action == MotionEvent.ACTION_MOVE) {
            if (holdingVoice) updateVoiceOverlay(event.getRawY() < voiceDownY - dp(64));
            else if (!voiceTouchConsumed && (Math.abs(event.getRawX() - voiceDownX) > dp(12)
                    || Math.abs(event.getRawY() - voiceDownY) > dp(12))) {
                voiceTouchConsumed = true;
                view.removeCallbacks(voiceHoldStart);
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            boolean cancelled = action == MotionEvent.ACTION_CANCEL || voiceCancel
                || (holdingVoice && event.getRawY() < voiceDownY - dp(64));
            view.removeCallbacks(voiceHoldStart);
            if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
            voiceTouchTarget = null;
            if (holdingVoice) {
                holdingVoice = false;
                voiceOverlay.setVisibility(View.GONE);
                voiceHalo.stop();
                chatFooter.setAlpha(1f);
                if (!cancelled) useVoiceInput(true);
                services.endVoice(cancelled);
                if (!cancelled && !heldVoiceText.isEmpty()) applyVoiceText(heldVoiceText);
                heldVoiceText = "";
                controls();
            } else if (!cancelled && !voiceTouchConsumed && view == input) {
                services.showKeyboard(input);
            }
            voiceCancel = false;
        }
        return true;
    }

    private void updateVoiceOverlay(boolean cancel) {
        if (voiceOverlay == null || (voiceOverlay.getVisibility() == View.VISIBLE && voiceCancel == cancel)) return;
        voiceCancel = cancel;
        if (voiceOverlay.getVisibility() != View.VISIBLE) {
            chatFooter.setAlpha(0f);
            voiceOverlay.setVisibility(View.VISIBLE);
            voiceHalo.start();
        }
        voiceHalo.setCancelled(cancel);
        voiceHint.setText(cancel ? "松手取消" : "松手转写 · 上滑取消");
        voiceHint.setTextColor(dark ? 0xfff3f6ff : cancel ? 0xff74313e : 0xff253854);
        voiceHint.setBackground(box(dark ? 0x9c151d35 : 0xc9ffffff, 0, 16));
    }

    void updateVoiceLevel(float level) {
        if (holdingVoice && voiceHalo != null) voiceHalo.setLevel(level);
    }

    private void useVoiceInput(boolean enabled) {
        if (voiceMode == enabled) return;
        voiceMode = enabled;
        services.saveVoiceInputMode(enabled);
        host.updateSystemBack();
    }

    private void applyVoiceText(String text) {
        useVoiceInput(false);
        draft = draft.trim().isEmpty() ? text : draft.trim() + " " + text;
        if (screen == CHAT) {
            input.setText(draft);
            input.setSelection(input.length());
        }
    }

    private boolean busy() { return state.job != null || upload || recording || transcribing || historyImportProgress != null; }

    private void controls() {
        if (screen != CHAT) return;
        boolean generating = state.job != null;
        boolean modifying = editing != null;
        boolean composing = !draft.trim().isEmpty() || !state.attachments.isEmpty();
        boolean official = state.deepSeekFeatures();
        boolean voice = voiceMode && !modifying && !generating;
        updateChatHeader();
        setChip(think, hasThinkingToggle() && (modifying ? editing.think : state.thinkingEnabled()));
        setChip(search, official && (modifying ? editing.search : state.data.optBoolean("search")));
        think.setEnabled(!generating);
        search.setEnabled(!generating);
        add.setEnabled(!busy());
        add.setAlpha(add.isEnabled() ? 1f : .45f);
        input.setVisibility(voice ? View.GONE : View.VISIBLE);
        voicePad.setVisibility(voice ? View.VISIBLE : View.GONE);
        voicePad.setText(recording ? (holdingVoice ? "松手转写" : "点按结束录音") : transcribing ? "正在转写…" : "按住说话");
        voicePad.setEnabled((!transcribing || holdingVoice) && !upload);
        mic.setVisibility(!modifying && !generating && (voice || !composing) ? View.VISIBLE : View.GONE);
        mic.setEnabled(!recording && !transcribing && !upload);
        mic.setContentDescription(voice ? "切换到键盘" : "切换到语音输入");
        mic.setImageResource(voice ? R.drawable.ic_keyboard : R.drawable.ic_mic);
        send.setVisibility(generating || modifying || (!recording && !transcribing && composing) ? View.VISIBLE : View.GONE);
        send.setEnabled(generating || (!upload && composing));
        send.setAlpha(send.isEnabled() ? 1f : .45f);
        send.setImageResource(generating ? R.drawable.ic_stop : R.drawable.ic_send);
        send.setContentDescription(generating ? "停止生成" : modifying ? "重新发送" : "发送消息");
        send.setImageTintList(ColorStateList.valueOf(dark ? 0xff0f1725 : 0xffffffff));
        buttonBackground(send, blueSolid, 0, 13);
        renderAttachments();
    }

    void attachmentPreview(String name, boolean image, Bitmap thumbnail) {
        pendingAttachment = json("name", name, "image", image);
        pendingThumbnail = thumbnail;
        controls();
    }

    private void renderAttachments() {
        boolean visible = !state.attachments.isEmpty() || pendingAttachment != null;
        attachmentStrip.setVisibility(visible ? View.VISIBLE : View.GONE);
        attachmentStatus.setVisibility(upload ? View.VISIBLE : View.GONE);
        attachments.removeAllViews();
        for (JSONObject attachment : state.attachments) {
            renderAttachment(attachment, attachmentThumbnails.get(string(attachment, "id")), false);
        }
        if (pendingAttachment != null) renderAttachment(pendingAttachment, pendingThumbnail, true);
    }

    private void renderAttachment(JSONObject attachment, Bitmap thumbnail, boolean pending) {
        String name = attachment.optString("name");
        boolean image = attachment.optBoolean("image");
        FrameLayout tile = new FrameLayout(context);
        tile.setBackground(box(tint, edge, 12));
        tile.setClipToOutline(true);

        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(image ? 78 : 164), dp(image ? 78 : 56));
        if (attachments.getChildCount() > 0) size.setMarginStart(dp(7));
        attachments.addView(tile, size);
        if (image) {
            ImageView preview = new ImageView(context);
            preview.setContentDescription("图片预览：" + name);
            if (thumbnail != null) {
                preview.setImageBitmap(thumbnail);
                preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                preview.setImageResource(R.drawable.ic_photo);
                preview.setImageTintList(ColorStateList.valueOf(blue));
                preview.setScaleType(ImageView.ScaleType.CENTER);
            }
            tile.addView(preview, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        } else {
            LinearLayout detail = horizontal();
            pad(detail, 8, 0);
            detail.setPaddingRelative(dp(8), 0, dp(pending ? 8 : 34), 0);
            ImageView mark = new ImageView(context);
            mark.setImageResource(R.drawable.ic_request_log);
            mark.setImageTintList(ColorStateList.valueOf(blue));
            detail.addView(mark, dp(24), dp(24));
            TextView title = text(name, 13, ink);
            title.setPaddingRelative(dp(5), 0, 0, 0);
            title.setSingleLine();
            title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            detail.addView(title, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
            tile.addView(detail, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        }
        if (pending) return;
        ImageButton remove = icon(R.drawable.ic_close, "移除附件：" + name, () -> {
            if (!busy()) {
                state.attachments.remove(attachment);
                attachmentThumbnails.remove(string(attachment, "id"));
                controls();
            }
        });
        if (image) {
            remove.setImageTintList(ColorStateList.valueOf(0xffffffff));
            buttonBackground(remove, 0xb02a3040, 0, 14);
        }
        FrameLayout.LayoutParams close = new FrameLayout.LayoutParams(dp(34), dp(34),
            Gravity.END | (image ? Gravity.TOP : Gravity.CENTER_VERTICAL));
        close.setMarginEnd(dp(3));
        if (image) close.topMargin = dp(3);
        tile.addView(remove, close);
    }

    private void updateChatHeader() {
        JSONObject chat = state.current();
        String title = string(chat, "title").replaceAll("\\s+", " ").trim();
        if (title.isEmpty()) title = "新对话";
        if (!title.contentEquals(chatTitle.getText())) chatTitle.setText(title);
        ChatState.Job job = state.job;
        String id = job != null ? string(job.request, "model") :
            editing != null ? state.effectiveModel(editing.search) : state.effectiveModel();
        String model = state.custom() ? id : officialModelName(id);
        if (!model.contentEquals(chatModel.getText())) chatModel.setText(model);
        chatModel.setVisibility(model.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void attachmentAction(String title, int drawable, Runnable run) {
        LinearLayout tile = vertical();
        tile.setGravity(Gravity.CENTER);
        buttonBackground(tile, surface, edge, 16);
        tile.setFocusable(true);
        tile.setContentDescription(title);
        tile.setOnClickListener(view -> run.run());

        ImageView symbol = new ImageView(context);
        symbol.setImageResource(drawable);
        symbol.setImageTintList(ColorStateList.valueOf(blue));
        symbol.setBackground(box(tint, 0, 12));
        symbol.setScaleType(ImageView.ScaleType.CENTER);
        tile.addView(symbol, dp(36), dp(34));
        TextView label = text(title, 13, ink);
        label.setGravity(Gravity.CENTER);
        tile.addView(label, MATCH_PARENT, dp(25));

        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, dp(76), 1);
        if (attachmentPanel.getChildCount() > 0) size.setMarginStart(dp(7));
        attachmentPanel.addView(tile, size);
    }

    private void showAttachments() {
        if (busy()) return;
        if (!attachmentPanelOpen) {
            input.clearFocus();
            services.hideKeyboard();
        }
        setAttachmentPanel(!attachmentPanelOpen, true);
    }

    private void setAttachmentPanel(boolean open, boolean animate) {
        boolean changed = attachmentPanelOpen != open;
        boolean follow = changed && screen == CHAT && nearBottom();
        if (changed && animate && ValueAnimator.areAnimatorsEnabled()) {
            TransitionSet transition = new TransitionSet().addTransition(new Fade()).addTransition(new ChangeBounds());
            transition.setDuration(180).setInterpolator(EASE);
            TransitionManager.beginDelayedTransition(chatFooter, transition);
        }
        attachmentPanelOpen = open;
        attachmentPanel.setVisibility(open ? View.VISIBLE : View.GONE);
        add.setImageResource(open ? R.drawable.ic_close : R.drawable.ic_attach);
        add.setContentDescription(open ? "收起附件选项" : "添加附件");
        if (changed) host.updateSystemBack();
        if (follow) bottom();
    }

    private void selectAttachment(java.util.function.Consumer<String> launch) {
        setAttachmentPanel(false, true);
        if (!state.deepSeekFeatures()) {
            composerNotice("添加附件", "当前的图片和文档上传使用 DeepSeek 官方接口。", "API 设置", this::openApiSettings);
            return;
        }
        if (busy() || !allowProvider() || !requireKey()) return;
        upload = true;
        controls();
        launch.accept(state.attachmentKey());
    }

    private void composerNotice(String title, String explanation, String actionLabel, Runnable open) {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, title);
        TextView note = text(explanation, 14, muted);
        pad(note, 6, 14);
        panel.addView(note, MATCH_PARENT, WRAP_CONTENT);
        if (open != null) sheetActions(panel, "取消", dialog::dismiss, actionLabel, () -> {
            dialog.dismiss();
            open.run();
        });
        showSheet(dialog, panel);
    }
    private boolean requireKey() {
        if (hasKey) return true;
        openApiSettings();
        return false;
    }
    private boolean mayChange() {
        if (!busy()) return true;
        toast("当前操作完成后即可切换对话。");
        return false;
    }
    private void activate(JSONObject chat) {
        if (chat == state.current()) {
            if (chat == null) toast("已经在新对话中");
            else drawer.close();
            return;
        }
        if (!mayChange()) return;
        if (!prepareConversationSwitch(chat)) return;
        state.activate(chat);
        clearChatView();
        save();
        services.hideKeyboard();
        if (drawer.isRevealed()) drawer.close();
        else animateChatContents();
    }

    private boolean loadConversation(JSONObject chat) {
        if (chat.optJSONArray("messages") != null) return true;
        JSONArray messages = services.loadMessages(string(chat, "id"));
        if (messages == null) {
            toast("无法读取这条对话。");
            return false;
        }
        put(chat, "messages", messages);
        return true;
    }

    private boolean prepareConversationSwitch(JSONObject next) {
        if (messagesDirty && !persist()) return false;
        if (next != null && !loadConversation(next)) return false;
        JSONObject previous = state.current();
        if (previous != null) previous.remove("messages");
        return true;
    }

    private void clearChatView() {
        setAttachmentPanel(false, false);
        services.stopSpeaking();
        editing = null;
        draft = "";
        unfolded.clear();
        input.setText("");
        input.clearFocus();
        renderMessages(true);
        updateEditLayout(false);
        transcript.scrollTo(0, 0);
        controls();
    }

    private void animateChatContents() {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        transcript.animate().cancel();
        transcript.setAlpha(.4f);
        transcript.setTranslationY(dp(7));
        transcript.animate().alpha(1f).translationY(0).setDuration(190).setInterpolator(EASE).start();
    }
    private void submit() {
        if (state.job != null) {
            services.cancel(state.job.id);
            finish(state.job, "已停止");
            return;
        }
        if (busy() || !requireKey() || !allowProvider()) return;
        String message = draft.trim();
        if (message.isEmpty() && state.attachments.isEmpty()) return;
        setAttachmentPanel(false, true);
        JSONArray files = new JSONArray();
        for (JSONObject file : state.attachments) files.put(file);
        EditSession session = editing;
        boolean think = session == null ? state.thinkingEnabled() : session.think;
        boolean search = session == null ? state.data.optBoolean("search") : session.search;
        if (session == null) {
            state.attachments.clear();
            input.setText("");
        } else {
            restoreNormalDraft(session);
            updateEditLayout(true);
        }
        input.clearFocus();
        services.hideKeyboard();
        int index = session == null ? state.messages().length() : session.index;
        ChatState.Job job = generate(json("role", "user", "text", message, "files", files, "think", think, "search", search),
            index);
        if (!message.isEmpty() && services.nebulaEnabled()) {
            NebulaSummaries.Target target = nebulaTarget();
            nebulaJobId = job.id;
            nebulaAfterReply = () -> services.rememberInNebula(target, string(job.chat, "id"), index, message, snapshot -> {
                if (snapshot != null && !nebulaWorking) {
                    updateNebulaSnapshot(snapshot);
                    renderNebula();
                }
            });
        }
    }

    private void beginEdit(JSONObject message, int index) {
        if (!mayChange() || !requireKey() || !allowProvider()) return;
        setAttachmentPanel(false, true);
        services.hideKeyboard();
        input.clearFocus();
        editing = new EditSession(index, draft, state.attachments,
            state.thinkingEnabled(message.optBoolean("think", state.data.optBoolean("think"))),
            message.optBoolean("search", state.data.optBoolean("search")));
        state.attachments.clear();
        state.attachments.addAll(objects(array(message, "files")));
        input.setText(answer(message));
        input.setSelection(input.length());
        updateEditLayout(true);
        controls();
    }

    private void restoreNormalDraft(EditSession session) {
        editing = null;
        state.attachments.clear();
        state.attachments.addAll(session.normalFiles);
        input.setText(session.normalDraft);
        input.setSelection(input.length());
    }

    private boolean closeEdit() {
        if (editing == null) return true;
        if (upload) { toast("附件上传完成后即可退出修改。"); return false; }
        restoreNormalDraft(editing);
        services.hideKeyboard();
        input.clearFocus();
        updateEditLayout(true);
        controls();
        return true;
    }

    private void updateEditLayout(boolean animate) {
        boolean modifying = editing != null;
        host.updateSystemBack();
        boolean motion = animate && ValueAnimator.areAnimatorsEnabled();
        if (motion) {
            TransitionSet transition = new TransitionSet().addTransition(new Fade()).addTransition(new ChangeBounds());
            transition.setDuration(190).setInterpolator(EASE);
            TransitionManager.beginDelayedTransition(chatFooter, transition);
        }
        editHeader.setVisibility(modifying ? View.VISIBLE : View.GONE);
        input.setMinHeight(dp(modifying ? 80 : 44));
        input.setContentDescription(modifying ? "修改消息内容" : "消息");
        updateInputHint();
        editShade.setVisibility(modifying ? View.VISIBLE : View.GONE);
        updateReturnBottom();
        messages.setImportantForAccessibility(modifying ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS :
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        for (int i = 0; i < messages.getChildCount(); i++) {
            View row = messages.getChildAt(i);
            float alpha = modifying && i != editing.index ? (dark ? .25f : .34f) : 1f;
            row.animate().cancel();
            if (motion) row.animate().alpha(alpha).setDuration(175).setInterpolator(EASE).start();
            else row.setAlpha(alpha);
        }
    }
    private boolean allowProvider() {
        if (state.sameProvider()) return true;
        new AlertDialog.Builder(context).setTitle("当前接口无法继续此会话")
            .setMessage("可切换回原接口和协议继续会话，或用当前设置新建对话。")
            .setPositiveButton("新对话", (d, w) -> {
                if (!mayChange() || !prepareConversationSwitch(null)) return;
                String pending = draft;
                state.activate(null);
                clearChatView();
                input.setText(pending);
                input.setSelection(input.length());
                save();
                animateChatContents();
            }).setNegativeButton("取消", null).setNeutralButton("API 设置", (d,w) -> openApiSettings()).show();
        return false;
    }
    private ChatState.Job generate(JSONObject user, int index) {
        services.stopSpeaking();
        ChatState.Job job = state.start(user, index);
        saveMessages();
        put(job.reply, "status", "正在连接…");
        renderMessages(true);
        controls();
        if (drawer.isRevealed()) refreshHistory();
        services.stream(job.id, string(job.chat, "id"), state.apiProvider(), job.endpoint, job.protocol, job.request.toString(),
            state.data.optBoolean("compactContext", true), job.contextWindow, user.optBoolean("think"), job.attachmentKey);
        return job;
    }
    private void finish(ChatState.Job completed, String error) {
        state.finish(error);
        unfolded.remove(completed.reply);
        saveMessages();
        if (completed.id == nebulaJobId && nebulaAfterReply != null) {
            Runnable remember = nebulaAfterReply;
            nebulaAfterReply = null;
            if (error == null || error.isEmpty()) remember.run();
        }
        if (completed.endpoint.isEmpty()) refreshBalance(true);
        if (screen == CHAT) {
            boolean follow = nearBottom();
            if (ValueAnimator.areAnimatorsEnabled() && liveReply != null && !answer(liveReply).isEmpty()) {
                renderLive(true);
                if (follow) bottom();
                View previous = live;
                previous.postDelayed(() -> {
                    if (screen == CHAT && live == previous && state.job == null) renderMessages(nearBottom());
                }, StreamingText.FADE_MS);
            } else renderMessages(follow);
            controls();
            if (drawer.isRevealed()) refreshHistory();
        }
    }

    private boolean nearBottom() {
        return transcript == null || bottomOffset(transcript) - transcript.getScrollY() < dp(76);
    }
    private int bottomOffset(ScrollView view) {
        return view.getChildCount() == 0 ? 0 : Math.max(0, view.getChildAt(0).getHeight() - view.getHeight());
    }
    private void updateReturnBottom() {
        if (screen != CHAT || returnBottom == null) return;
        boolean visible = editing == null && !nearBottom();
        if (visible == (returnBottom.getVisibility() == View.VISIBLE)) return;
        returnBottom.animate().cancel();
        returnBottom.setAlpha(1f);
        returnBottom.setTranslationY(0f);
        returnBottom.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && ValueAnimator.areAnimatorsEnabled()) {
            returnBottom.setAlpha(0f);
            returnBottom.setTranslationY(dp(6));
            returnBottom.animate().alpha(1f).translationY(0f).setDuration(140).setInterpolator(EASE).start();
        }
    }
    private void smoothBottom() {
        int offset = bottomOffset(transcript);
        if (ValueAnimator.areAnimatorsEnabled()) transcript.smoothScrollTo(0, offset);
        else transcript.scrollTo(0, offset);
    }
    private void afterTranscriptLayout(java.util.function.Consumer<ScrollView> move) {
        ScrollView view = transcript;
        ViewTreeObserver observer = view.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                view.getViewTreeObserver().removeOnPreDrawListener(this);
                if (screen == CHAT && view == transcript) {
                    move.accept(view);
                    updateReturnBottom();
                }
                return true;
            }
        });
        view.invalidate();
    }
    private void bottom() {
        afterTranscriptLayout(view -> view.scrollTo(0, bottomOffset(view)));
    }
    private void renderMessages(boolean follow) {
        if (screen != CHAT) return;
        int offset = transcript.getScrollY();
        speechButtons.clear();
        live = null;
        liveReply = null;
        liveText = "";
        messages.removeAllViews();
        JSONArray entries = state.messages();
        ((View) welcome.getParent()).setVisibility(entries.length() == 0 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < entries.length(); i++) {
            JSONObject message = entries.optJSONObject(i);
            if (message == null) continue;
            if ("user".equals(message.optString("role"))) userView(message, i, messages);
            else assistantView(message, i, messages, i == entries.length() - 1);
        }
        if (follow && entries.length() > 0) bottom();
        else if (offset > 0) afterTranscriptLayout(view -> view.scrollTo(0, offset));
    }
    private void userView(JSONObject message, int index, LinearLayout parent) {
        LinearLayout entry = vertical();
        entry.setGravity(Gravity.END);
        parent.addView(entry, MATCH_PARENT, WRAP_CONTENT);
        LinearLayout bubble = vertical();
        pad(bubble, 12, 9);
        bubble.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x28a7c3ff : 0x203268df),
            userBubble(tint), userBubble(0xffffffff)));
        bubble.setOnClickListener(view -> beginEdit(message, index));
        bubble.setFocusable(true);
        entry.addView(bubble, WRAP_CONTENT, WRAP_CONTENT);
        String content = answer(message);
        if (!content.isEmpty()) {
            TextView value = text(content, 16, ink);
            ChatFont.apply(value, 16, ChatFont.scale(state.data));
            value.setMaxWidth(dp(290));
            bubble.addView(value, WRAP_CONTENT, WRAP_CONTENT);
            bubble.setOnLongClickListener(view -> {
                copy(content);
                return true;
            });
        }
        List<JSONObject> files = objects(array(message, "files"));
        files.addAll(objects(array(message, "archivedFiles")));
        for (JSONObject file : files) {
            TextView attachment = text("附件 · " + file.optString("name"), 13, blue);
            ChatFont.apply(attachment, 13, ChatFont.scale(state.data));
            attachment.setMaxWidth(dp(270));
            attachment.setSingleLine();
            attachment.setEllipsize(TextUtils.TruncateAt.END);
            bubble.addView(attachment);
        }
        gap(entry, 10);
    }
    private void assistantView(JSONObject message, int index, LinearLayout parent, boolean latest) {
        LinearLayout entry = vertical();
        parent.addView(entry, MATCH_PARENT, WRAP_CONTENT);
        boolean active = state.job != null && state.job.reply == message;
        ThinkingPanel thinking = new ThinkingPanel(message);
        MarkdownReply result = new MarkdownReply(context, ink, muted, blue, surface, edge, tint, dark, ChatFont.scale(state.data),
            services::open, this::copy, id -> showSourceSheet(WebSources.from(message), id));
        TextView status = text("", 13, muted);
        TextView sourceLink = action("", () -> {
            WebSources sources = WebSources.from(message);
            showSourceSheet(sources, 0);
        });
        sourceLink.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        sourceLink.setTextColor(muted);
        sourceLink.setMinHeight(dp(34));
        pad(sourceLink, 2, 4);
        if (active) {
            live = entry;
            liveReply = message;
            liveAnswer = result;
            liveThinking = thinking;
            liveStatus = status;
            liveSourceLink = sourceLink;
            entry.addView(thinking, MATCH_PARENT, WRAP_CONTENT);
            entry.addView(sourceLink, WRAP_CONTENT, WRAP_CONTENT);
            entry.addView(result, MATCH_PARENT, WRAP_CONTENT);
            entry.addView(status, MATCH_PARENT, WRAP_CONTENT);
            renderLive(false);
            return;
        }
        thinking.display(hiddenReply(message) ? "" : merged(message, "thinking", "thinking"), false);
        entry.addView(thinking, MATCH_PARENT, WRAP_CONTENT);
        WebSources sources = WebSources.from(message);
        setSourceLink(sourceLink, sources);
        entry.addView(sourceLink, WRAP_CONTENT, WRAP_CONTENT);
        String answer = answer(message);
        result.setMarkdown(sources.annotatedAnswer(message));
        entry.addView(result, MATCH_PARENT, WRAP_CONTENT);
        setStatus(status, replyStatus(message));
        entry.addView(status);
        if (latest && state.job == null) {
            LinearLayout bar = horizontal();
            entry.addView(bar, MATCH_PARENT, WRAP_CONTENT);
            if (!answer.isEmpty()) {
                messageAction(bar, R.drawable.ic_copy, "复制回复", 32, () -> copy(answer));
                String id = string(state.current(), "id") + ":" + index;
                speechButtons.put(id, messageAction(bar, R.drawable.ic_volume, "朗读回复", 32,
                    () -> services.speak(id, MarkdownText.forSpeech(answer))));
                updateSpeechButton(id, speechButtons.get(id));
                messageAction(bar, R.drawable.ic_share, "分享回复", 32, () -> services.share(answer));
            }
            weight(bar);
            if ("max_tokens".equals(string(message, "finish"))) {
                messageAction(bar, R.drawable.ic_continue, "继续生成", 44, () -> {
                    if (!busy() && requireKey() && allowProvider()) generate(json("role", "user", "text", "请从上一条回复中断的地方继续。"), state.messages().length());
                });
            }
            messageAction(bar, R.drawable.ic_retry, "重新生成", 44, () -> retry(index));
        }
        gap(entry, 14);
    }

    private void setSourceLink(TextView link, WebSources sources) {
        int count = sources.size();
        link.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        if (count == 0) return;
        String label = (sources.searched ? "搜索到 " : "查看 ") + count + " 个网页";
        if (!label.equals(link.getContentDescription())) {
            link.setText(label + "  ›");
            link.setContentDescription(label);
        }
    }

    private void showSourceSheet(WebSources sources, int focusNumber) {
        if (sources == null || sources.size() == 0) return;
        services.hideKeyboard();
        Dialog dialog = newSheet();
        LinearLayout panel = sheetPanel();
        LinearLayout header = horizontal();
        TextView title = text("网页来源", 16, ink);
        bold(title);
        pad(title, 5, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        TextView count = text(sources.size() + " 个", 12, muted);
        header.addView(count, WRAP_CONTENT, MATCH_PARENT);
        header.addView(icon(R.drawable.ic_close, "关闭来源", dialog::dismiss), dp(44), dp(44));
        panel.addView(header, MATCH_PARENT, dp(48));
        divider(panel);

        ScrollView scroll = sheetScroll(.61f);
        panel.addView(scroll, MATCH_PARENT, WRAP_CONTENT);
        LinearLayout rows = vertical();
        scroll.addView(rows, MATCH_PARENT, WRAP_CONTENT);
        View focused = null;
        for (WebSources.Source source : sources.items()) {
            LinearLayout row = vertical();
            pad(row, 10, 9);
            buttonBackground(row, source.number == focusNumber ? tint : 0, 0, 12);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                services.open(source.url);
            });
            row.setFocusable(true);
            String metaText = source.number + "  ·  " + source.displaySite()
                + (source.date.isEmpty() ? "" : "   " + source.date);
            TextView meta = text(metaText, 12, muted);
            meta.setSingleLine();
            meta.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(meta, MATCH_PARENT, WRAP_CONTENT);
            TextView name = text(source.displayTitle() + " ↗", 15, ink);
            bold(name);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(name, MATCH_PARENT, WRAP_CONTENT);
            if (!source.snippet.isEmpty()) {
                TextView snippet = text(source.snippet, 13, muted);
                snippet.setMaxLines(2);
                snippet.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(snippet, MATCH_PARENT, WRAP_CONTENT);
            }
            rows.addView(row, MATCH_PARENT, WRAP_CONTENT);
            if (source.number == focusNumber) focused = row;
        }
        showSheet(dialog, panel);
        View target = focused;
        if (target != null) scroll.post(() -> scroll.scrollTo(0, target.getTop()));
    }

    private Dialog newSheet() {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private LinearLayout sheetPanel() {
        LinearLayout panel = vertical();
        panel.setBackground(box(surface, edge, 20));
        pad(panel, 12, 5);
        return panel;
    }

    private ScrollView sheetScroll(float fraction) {
        int available = Math.max(dp(130), Math.round(root.getHeight() * fraction));
        ScrollView scroll = new ScrollView(context) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int limit = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? available
                    : Math.min(available, MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(limit, MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        return scroll;
    }

    private void showSheet(Dialog dialog, LinearLayout panel) {
        showPanel(dialog, panel, true);
    }

    private void showPanel(Dialog dialog, LinearLayout panel, boolean bottom) {
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.setGravity(bottom ? Gravity.BOTTOM : Gravity.CENTER);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(.42f);
            if (bottom) window.setWindowAnimations(R.style.Jingliu_PanelAnimation);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        dialog.show();
        if (window == null) return;
        window.setLayout(bottom ? MATCH_PARENT : Math.min(dp(360), root.getWidth() - dp(48)), WRAP_CONTENT);
        if (Build.VERSION.SDK_INT >= 31 && state.data.optBoolean(BLUR_PANELS)) {
            AndroidWindowEffects.blurPanelBackdrop(window, Math.min(dp(18), 64));
        }
        if (Build.VERSION.SDK_INT >= 34 && state.data.optBoolean(PREDICTIVE_BACK)) {
            AndroidWindowEffects.animatePanelBack(dialog, window, panel, bottom, dp(96));
        }
    }

    private LinearLayout settingsSheet(Dialog dialog, String heading) {
        LinearLayout panel = sheetPanel();
        panel.setFocusableInTouchMode(true);
        View handle = new View(context);
        handle.setBackground(box(edge, 0, 3));
        LinearLayout.LayoutParams grip = new LinearLayout.LayoutParams(dp(30), dp(4));
        grip.gravity = Gravity.CENTER_HORIZONTAL;
        grip.topMargin = dp(4);
        panel.addView(handle, grip);
        FrameLayout bar = new FrameLayout(context);
        TextView title = text(heading, 16, ink);
        bold(title);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleArea = new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
        titleArea.setMargins(dp(44), 0, dp(44), 0);
        bar.addView(title, titleArea);
        ImageButton close = icon(R.drawable.ic_close, "关闭", dialog::dismiss);
        bar.addView(close, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.END | Gravity.CENTER_VERTICAL));
        panel.addView(bar, MATCH_PARENT, dp(50));
        divider(panel);
        return panel;
    }

    private void showRequestLogs() {
        services.hideKeyboard();
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, "请求日志");
        List<RequestLog.Entry> entries = services.requestLogs(string(state.current(), "id"));
        if (entries.isEmpty()) {
            settingsNote(panel, "当前对话暂无请求日志。发送消息后可在这里查看。");
        } else {
            settingsNote(panel, "日志仅保存在本次运行中，可能包含对话内容。");
            ScrollView scroll = sheetScroll(.60f);
            LinearLayout rows = vertical();
            scroll.addView(rows, MATCH_PARENT, WRAP_CONTENT);
            panel.addView(scroll, MATCH_PARENT, WRAP_CONTENT);
            java.text.DateFormat clock = android.text.format.DateFormat.getTimeFormat(context);
            for (RequestLog.Entry entry : entries) {
                if (rows.getChildCount() > 0) divider(rows);
                settingsRow(rows, R.drawable.ic_request_log,
                    clock.format(new java.util.Date(entry.started)) + " · " + entry.provider, entry.state(), () -> {
                        dialog.dismiss();
                        showRequestLog(entry);
                    });
            }
        }
        showSheet(dialog, panel);
    }

    private void showRequestLog(RequestLog.Entry entry) {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, "请求详情");
        ScrollView scroll = sheetScroll(.59f);
        TextView detail = text(entry.details(), 12, ink);
        detail.setTypeface(Typeface.MONOSPACE);
        detail.setTextIsSelectable(true);
        pad(detail, 10, 10);
        scroll.addView(detail, MATCH_PARENT, WRAP_CONTENT);
        panel.addView(scroll, MATCH_PARENT, WRAP_CONTENT);
        LinearLayout actions = horizontal();
        actions.addView(action("返回列表", () -> {
            dialog.dismiss();
            showRequestLogs();
        }));
        weight(actions);
        actions.addView(action("刷新", () -> detail.setText(entry.details())));
        actions.addView(action("复制日志", () -> copy(entry.details())));
        panel.addView(actions, MATCH_PARENT, WRAP_CONTENT);
        showSheet(dialog, panel);
    }

    private void choiceSheet(String title, String[] options, int selected, java.util.function.IntConsumer select) {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, title);
        ScrollView scroll = sheetScroll(.63f);
        LinearLayout rows = vertical();
        pad(rows, 0, 6);
        scroll.addView(rows, MATCH_PARENT, WRAP_CONTENT);
        panel.addView(scroll, MATCH_PARENT, WRAP_CONTENT);
        View selectedRow = null;
        for (int i = 0; i < options.length; i++) {
            int position = i;
            boolean active = i == selected;
            LinearLayout row = horizontal();
            pad(row, 13, 0);
            buttonBackground(row, active ? tint : 0, 0, 12);
            TextView label = text(options[i], 15, active ? blue : ink);
            row.addView(label, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
            if (active) {
                bold(label);
                ImageView check = new ImageView(context);
                check.setImageResource(R.drawable.ic_check);
                check.setImageTintList(ColorStateList.valueOf(blue));
                row.addView(check, dp(20), dp(20));
                selectedRow = row;
            }
            row.setSelected(active);
            row.setContentDescription(options[i]);
            row.setFocusable(true);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                select.accept(position);
            });
            rows.addView(row, MATCH_PARENT, dp(49));
        }
        showSheet(dialog, panel);
        View current = selectedRow;
        if (current != null) scroll.post(() -> {
            if (current.getBottom() > scroll.getHeight()) scroll.scrollTo(0, current.getBottom() - scroll.getHeight());
        });
    }

    private int dangerColor() { return dark ? 0xffffa6a6 : 0xffb02938; }

    private ImageButton messageAction(LinearLayout bar, int image, String label, int width, Runnable run) {
        ImageButton button = icon(image, label, run);
        button.setTooltipText(label);
        button.setImageTintList(ColorStateList.valueOf(muted));
        bar.addView(button, dp(width), dp(44));
        return button;
    }

    private void updateSpeechButton(String id, ImageButton button) {
        boolean active = id.equals(speaking);
        button.setImageResource(active ? R.drawable.ic_stop : R.drawable.ic_volume);
        button.setImageTintList(ColorStateList.valueOf(active ? blue : muted));
        String label = active ? "停止朗读" : "朗读回复";
        button.setContentDescription(label);
        button.setTooltipText(label);
    }

    private void retry(int index) {
        if (index != state.messages().length() - 1) return;
        if (!mayChange() || !requireKey() || !allowProvider()) return;
        JSONObject prior = state.messages().optJSONObject(index - 1);
        if (prior == null || !"user".equals(prior.optString("role"))) return;
        if (hasThinkingToggle()) put(prior, "think", state.thinkingEnabled(prior.optBoolean("think", state.thinkingEnabled())));
        generate(prior, index - 1);
    }
    private void copy(String value) { services.copy(value); toast("已复制"); }

    private final class ThinkingPanel extends LinearLayout {
        private final JSONObject message;
        private final LinearLayout heading, body;
        private final TextView caption;
        private final StreamingText reasoning;
        private final ImageView chevron;
        private String displayed = "";

        ThinkingPanel(JSONObject reply) {
            super(context);
            message = reply;
            setOrientation(VERTICAL);
            heading = horizontal();
            heading.setMinimumHeight(dp(37));
            heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            heading.setFocusable(true);
            addView(heading, WRAP_CONTENT, WRAP_CONTENT);
            caption = text("", 14, muted);
            ChatFont.apply(caption, 14, ChatFont.scale(state.data));
            heading.addView(caption, WRAP_CONTENT, MATCH_PARENT);
            chevron = new ImageView(context);
            chevron.setImageResource(R.drawable.ic_chevron);
            chevron.setImageTintList(ColorStateList.valueOf(muted));
            LinearLayout.LayoutParams arrow = new LinearLayout.LayoutParams(dp(16), dp(16));
            arrow.setMarginStart(dp(7));
            heading.addView(chevron, arrow);

            body = horizontal();
            body.setGravity(Gravity.TOP);
            View rail = new View(context);
            rail.setBackground(box(dark ? 0xff465878 : 0xffb7c9ec, 0, 2));
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(dp(2), MATCH_PARENT);
            line.setMargins(dp(3), dp(4), dp(10), dp(4));
            body.addView(rail, line);
            reasoning = new StreamingText(context);
            reasoning.setTextColor(muted);
            ChatFont.apply(reasoning, 14, ChatFont.scale(state.data));
            reasoning.setLineSpacing(dp(1), 1.15f);
            reasoning.setTextIsSelectable(true);
            body.addView(reasoning, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
            addView(body, MATCH_PARENT, WRAP_CONTENT);
            gap(this, 11);
            heading.setOnClickListener(view -> {
                if (!unfolded.add(message)) unfolded.remove(message);
                boolean animated = ValueAnimator.areAnimatorsEnabled();
                if (animated) {
                    TransitionSet transition = new TransitionSet().addTransition(new Fade()).addTransition(new ChangeBounds());
                    transition.setDuration(170).setInterpolator(EASE);
                    TransitionManager.beginDelayedTransition((ViewGroup) getParent(), transition);
                }
                updateOpen(animated);
            });
        }

        void display(String content, boolean thinking) {
            display(content, thinking, false);
        }

        void display(String content, boolean thinking, boolean animate) {
            setVisibility(content.isEmpty() ? GONE : VISIBLE);
            if (content.isEmpty()) return;
            if (!content.equals(displayed)) {
                displayed = content;
                if (animate && ValueAnimator.areAnimatorsEnabled() && unfolded.contains(message)) {
                    reasoning.showNewText(reasoning.getText(), content);
                } else reasoning.setText(content);
            }
            long elapsed = message.optLong("thinkingMillis");
            String title = thinking ? "正在思考" : elapsed == 0 ? "思考过程" : "思考完成 · " + Math.max(1L, (elapsed + 500) / 1000) + " 秒";
            if (!title.contentEquals(caption.getText())) caption.setText(title);
            updateOpen(false);
        }

        private void updateOpen(boolean animated) {
            boolean open = unfolded.contains(message);
            body.setVisibility(open ? VISIBLE : GONE);
            heading.setContentDescription(caption.getText() + (open ? "，收起" : "，展开"));
            float rotation = open ? 180f : 0f;
            if (animated) chevron.animate().rotation(rotation).setDuration(170).setInterpolator(EASE).start();
            else if (chevron.getRotation() != rotation && !chevron.hasTransientState()) chevron.setRotation(rotation);
        }
    }
    private void setStatus(TextView target, String value) {
        target.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
        target.setText(value);
    }
    private void renderLive(boolean animate) {
        if (screen != CHAT || live == null || (state.job != null && liveReply != state.job.reply)) return;
        WebSources sources = WebSources.from(liveReply);
        setSourceLink(liveSourceLink, sources);
        String text = sources.annotatedAnswer(liveReply);
        String reasoning = hiddenReply(liveReply) ? "" : merged(liveReply, "thinking", "thinking");
        if (!text.equals(liveText) || liveAnswer.getChildCount() == 0) {
            liveAnswer.setMarkdown(text, animate);
            liveText = text;
        }
        liveThinking.display(reasoning, state.job != null && !state.job.thinkingFinished, animate);
        setStatus(liveStatus, replyStatus(liveReply));
    }

    private void buildHistory() {
        historyPanel = vertical();
        historyPanel.setBackgroundColor(historyBackdrop());
        pad(historyPanel, 12, 4);

        historyBrowse = vertical();
        gap(historyBrowse, 9);
        LinearLayout searchBar = horizontal();
        searchBar.setBackground(box(surface, edge, 25));
        searchBar.setPadding(dp(12), 0, dp(8), 0);
        ImageView searchIcon = new ImageView(context);
        searchIcon.setImageResource(R.drawable.ic_search);
        searchIcon.setImageTintList(ColorStateList.valueOf(muted));
        searchBar.addView(searchIcon, dp(20), dp(20));
        historySearch = field("搜索对话内容", false);
        historySearch.setContentDescription("搜索对话");
        historySearch.setText(historyFilter);
        historySearch.setBackground(null);
        pad(historySearch, 8, 3);
        searchBar.addView(historySearch, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        historyBrowse.addView(searchBar, MATCH_PARENT, dp(46));
        gap(historyBrowse, 8);
        historyPanel.addView(historyBrowse, MATCH_PARENT, WRAP_CONTENT);

        historyManage = horizontal();
        historyCount = text("选择对话", 17, ink);
        bold(historyCount);
        pad(historyCount, 9, 0);
        historyCount.setOnLongClickListener(view -> {
            selectVisibleHistory();
            return true;
        });
        if (android.os.Build.VERSION.SDK_INT >= 28) historyCount.setAccessibilityHeading(true);
        historyManage.addView(historyCount, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        ImageButton close = icon(R.drawable.ic_close, "退出对话管理", () -> setHistoryMode(false));
        close.setImageTintList(ColorStateList.valueOf(ink));
        buttonBackground(close, surface, edge, 22);
        historyManage.addView(close, dp(42), dp(42));
        historyPanel.addView(historyManage, MATCH_PARENT, dp(62));

        FrameLayout listArea = new FrameLayout(context);
        historyPanel.addView(listArea, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        historyList = new HistorySelectionList();
        historyList.setDivider(new ColorDrawable(0));
        historyList.setDividerHeight(dp(3));
        historyList.setItemsCanFocus(true);
        historyList.setVerticalScrollBarEnabled(false);
        historyList.setSelector(android.R.color.transparent);
        listArea.addView(historyList, MATCH_PARENT, MATCH_PARENT);
        historyEmpty = text("还没有对话", 14, muted);
        pad(historyEmpty, 9, 9);
        listArea.addView(historyEmpty, new FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP));
        historyList.setEmptyView(historyEmpty);
        historyAdapter = new HistoryAdapter();
        historyList.setAdapter(historyAdapter);

        divider(historyPanel);
        historyShortcuts = horizontal();
        pad(historyShortcuts, 8, 0);
        buttonBackground(historyShortcuts, 0, 0, 12);
        historyShortcuts.setContentDescription("设置");
        historyShortcuts.setFocusable(true);
        historyShortcuts.setOnClickListener(v -> openSettingsHome());
        ImageView brandIcon = new ImageView(context);
        brandIcon.setImageResource(R.drawable.ic_mark);
        brandIcon.setImageTintList(ColorStateList.valueOf(blue));
        brandIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        pad(brandIcon, 8, 8);
        brandIcon.setBackground(box(tint, 0, 12));
        historyShortcuts.addView(brandIcon, dp(32), dp(32));
        TextView brandName = text(context.getString(R.string.app_name), 15, ink);
        bold(brandName);
        pad(brandName, 9, 0);
        historyShortcuts.addView(brandName, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        ImageView settingIcon = new ImageView(context);
        settingIcon.setImageResource(R.drawable.ic_settings);
        settingIcon.setImageTintList(ColorStateList.valueOf(muted));
        historyShortcuts.addView(settingIcon, dp(22), dp(22));
        historyPanel.addView(historyShortcuts, MATCH_PARENT, dp(54));

        historySelection = horizontal();
        historyPin = action("置顶", this::pinSelectedHistory);
        historyDelete = action("删除", () -> confirmDelete(new HashSet<>(selectedChats)));
        historyPin.setTextColor(ink);
        historyDelete.setTextColor(dangerColor());
        chipIcon(historyPin, R.drawable.ic_pin);
        chipIcon(historyDelete, R.drawable.ic_trash);
        historyPin.setCompoundDrawableTintList(ColorStateList.valueOf(ink));
        historyDelete.setCompoundDrawableTintList(ColorStateList.valueOf(historyDelete.getCurrentTextColor()));
        addHistoryAction(historyPin);
        View rule = new View(context);
        rule.setBackgroundColor(edge);
        historySelection.addView(rule, dp(1), dp(23));
        addHistoryAction(historyDelete);
        historyPanel.addView(historySelection, MATCH_PARENT, dp(58));

        watch(historySearch, value -> {
            historyFilter = value;
            queueHistorySearch();
            refreshHistory();
            historyList.setSelection(0);
        });
        updateHistoryMode();
        refreshHistory();
    }

    private int historyBackdrop() { return dark ? 0xff151820 : 0xfff8faff; }

    private Drawable historyRowBackground(int fill) {
        return new RippleDrawable(ColorStateList.valueOf(dark ? 0x24ffffff : 0x12000000),
            box(fill, 0, 13), box(0xffffffff, 0, 13));
    }

    private void addHistoryAction(TextView action) {
        FrameLayout area = new FrameLayout(context);
        area.addView(action, new FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, Gravity.CENTER));
        area.setOnClickListener(view -> {
            if (action.isEnabled()) action.performClick();
        });
        area.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        historySelection.addView(area, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
    }

    private void onHistoryOpening() {
        setAttachmentPanel(false, false);
        services.hideKeyboard();
        refreshHistory();
        host.updateSystemBack();
    }

    private void onHistoryClosed() {
        if (historyPanel == null) return;
        historyList.endSelectionGesture();
        historySearch.clearFocus();
        services.hideKeyboard();
        managingHistory = false;
        selectedChats.clear();
        if (!historyFilter.isEmpty()) historySearch.setText("");
        updateHistoryMode();
        refreshHistory();
        host.updateSystemBack();
    }

    private void updateHistoryMode() {
        historyBrowse.setVisibility(managingHistory ? View.GONE : View.VISIBLE);
        historyShortcuts.setVisibility(managingHistory ? View.GONE : View.VISIBLE);
        historyManage.setVisibility(managingHistory ? View.VISIBLE : View.GONE);
        historySelection.setVisibility(managingHistory ? View.VISIBLE : View.GONE);
    }

    private void setHistoryMode(boolean manage) {
        if (managingHistory == manage) return;
        historyList.endSelectionGesture();
        services.hideKeyboard();
        historySearch.clearFocus();
        if (ValueAnimator.areAnimatorsEnabled()) {
            TransitionSet transition = new TransitionSet()
                .addTransition(new Fade()).addTransition(new ChangeBounds());
            transition.addTarget(historyBrowse).addTarget(historyManage)
                .addTarget(historyShortcuts).addTarget(historySelection).addTarget(historyList);
            transition.setDuration(175).setInterpolator(EASE);
            TransitionManager.beginDelayedTransition(historyPanel, transition);
        }
        managingHistory = manage;
        selectedChats.clear();
        updateHistoryMode();
        historyAdapter.notifyDataSetChanged();
        updateHistorySelection();
    }

    private void queueHistorySearch() {
        int request = ++historySearchRequest;
        if (historySearchTask != null) root.removeCallbacks(historySearchTask);
        historyBodyMatches = Collections.emptySet();
        String query = historyFilter.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return;
        historySearchTask = () -> services.searchHistory(query, hits -> {
            if (request != historySearchRequest || screen != CHAT) return;
            historyBodyMatches = hits;
            refreshHistory();
        });
        root.postDelayed(historySearchTask, 180);
    }

    private void refreshHistory() {
        historyList.endSelectionGesture();
        List<JSONObject> chats = state.history(historyFilter, historyBodyMatches);
        List<HistoryItem> rows = new ArrayList<>();
        String previous = "";
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        for (JSONObject chat : chats) {
            LocalDate updated = Instant.ofEpochMilli(chat.optLong("updated")).atZone(zone).toLocalDate();
            String group = pinned(chat) ? "置顶对话" : !updated.isBefore(today) ? "今天"
                : !updated.isBefore(today.minusDays(6)) ? "7 天内" : "更早";
            if (!group.equals(previous)) rows.add(new HistoryItem(group, null));
            rows.add(new HistoryItem(null, chat));
            previous = group;
        }
        historyEmpty.setText(historyFilter.trim().isEmpty() ? "还没有对话" : "没有匹配的对话");
        historyAdapter.chats = chats;
        historyAdapter.rows = rows;
        historyAdapter.notifyDataSetChanged();
        updateHistorySelection();
    }

    private void toggleHistorySelection(JSONObject chat) {
        String id = string(chat, "id");
        if (!selectedChats.add(id)) selectedChats.remove(id);
        historyAdapter.notifyDataSetChanged();
        updateHistorySelection();
    }

    private boolean allVisibleSelected() {
        for (JSONObject chat : historyAdapter.chats) if (!selectedChats.contains(string(chat, "id"))) return false;
        return !historyAdapter.chats.isEmpty();
    }

    private void selectVisibleHistory() {
        boolean clear = allVisibleSelected();
        for (JSONObject chat : historyAdapter.chats) {
            String id = string(chat, "id");
            if (clear) selectedChats.remove(id);
            else selectedChats.add(id);
        }
        historyAdapter.notifyDataSetChanged();
        updateHistorySelection();
    }

    private boolean allSelectedPinned() {
        if (selectedChats.isEmpty()) return false;
        for (JSONObject chat : objects(state.chats())) {
            if (selectedChats.contains(string(chat, "id")) && !pinned(chat)) return false;
        }
        return true;
    }

    private void updateHistorySelection() {
        int count = selectedChats.size();
        historyCount.setText(count == 0 ? "选择对话" : "已选择 " + count + " 个对话");
        historyCount.setLongClickable(!historyAdapter.chats.isEmpty());
        historyPin.setText(allSelectedPinned() ? "取消置顶" : "置顶");
        enableHistoryButton(historyPin, count > 0);
        enableHistoryButton(historyDelete, count > 0);
    }

    private void enableHistoryButton(TextView button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : .42f);
    }

    private void pinSelectedHistory() {
        if (selectedChats.isEmpty()) return;
        boolean pin = !allSelectedPinned();
        state.setPinned(selectedChats, pin);
        save();
        setHistoryMode(false);
        refreshHistory();
    }

    private void confirmDelete(Set<String> ids) {
        if (ids.isEmpty() || !mayChange()) return;
        confirmHistoryDeletion(ids, "删除 " + ids.size() + " 条对话？", "删除后无法恢复。", "删除");
    }

    private void confirmClearHistory() {
        if (!mayChange()) return;
        Set<String> ids = new HashSet<>();
        for (JSONObject chat : objects(state.chats())) ids.add(string(chat, "id"));
        if (ids.isEmpty()) {
            toast("对话列表已为空");
            return;
        }
        confirmHistoryDeletion(ids, "清空全部对话？", "将删除本机全部 " + ids.size() + " 条对话，包括置顶和导入的对话。删除后无法恢复。", "清空全部");
    }

    private void confirmHistoryDeletion(Set<String> ids, String title, String message, String label) {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, title);
        TextView warning = text(message, 14, muted);
        pad(warning, 6, 14);
        panel.addView(warning, MATCH_PARENT, WRAP_CONTENT);
        TextView erase = sheetActions(panel, "取消", dialog::dismiss, label, () -> {
            dialog.dismiss();
            if (!mayChange()) return;
            deleteHistory(ids);
        });
        erase.setTextColor(dangerColor());
        buttonBackground(erase, surface, dangerColor(), 14);
        showSheet(dialog, panel);
    }

    private void deleteHistory(Set<String> ids) {
        JSONArray previous = state.chats();
        JSONArray remaining = new JSONArray();
        for (JSONObject chat : objects(previous)) if (!ids.contains(string(chat, "id"))) remaining.put(chat);
        Object oldActive = state.data.opt("active");
        boolean clearActive = ids.contains(string(state.data, "active"));
        boolean wasDirty = messagesDirty;
        put(state.data, "chats", remaining);
        if (clearActive) {
            put(state.data, "active", JSONObject.NULL);
            messagesDirty = false;
        }
        if (!persist()) {
            put(state.data, "chats", previous);
            put(state.data, "active", oldActive);
            messagesDirty = wasDirty;
            return;
        }
        if (clearActive) {
            state.activate(null);
            if (screen == CHAT) clearChatView();
            else {
                draft = "";
                unfolded.clear();
                settingsReturnScroll = -1;
                services.stopSpeaking();
            }
        }
        if (screen == CHAT) {
            if (managingHistory) setHistoryMode(false);
            refreshHistory();
        } else {
            selectedChats.clear();
            historyFilter = "";
            historyBodyMatches = Collections.emptySet();
            toast("已清空全部对话");
        }
    }

    private void historyActions(View anchor, JSONObject chat) {
        String id = string(chat, "id");
        LinearLayout menu = vertical();
        menu.setBackground(box(surface, edge, 18));
        menu.setClipToOutline(true);
        PopupWindow popup = new PopupWindow(menu, Math.min(anchor.getWidth(), dp(216)), WRAP_CONTENT, true);
        popup.setBackgroundDrawable(box(surface, edge, 18));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setElevation(dp(9));
        popup.setEnterTransition(new Fade().setDuration(135));
        popup.setExitTransition(new Fade().setDuration(100));

        historyMenuItem(menu, popup, R.drawable.ic_edit, "重命名", ink, () ->
            editDialog("重命名对话", string(chat, "title"), "确认", title -> {
                put(chat, "title", title);
                put(chat, "customTitle", true);
                save();
                refreshHistory();
                updateChatHeader();
            }));
        historyMenuItem(menu, popup, R.drawable.ic_pin, pinned(chat) ? "取消置顶" : "置顶", ink, () -> {
            state.setPinned(Collections.singleton(id), !pinned(chat));
            save();
            refreshHistory();
        });
        historyMenuItem(menu, popup, R.drawable.ic_manage, "多选", ink, () -> {
            setHistoryMode(true);
            toggleHistorySelection(chat);
        });
        historyMenuItem(menu, popup, R.drawable.ic_trash, "删除", dangerColor(), () -> confirmDelete(Collections.singleton(id)));
        Rect visible = new Rect();
        root.getWindowVisibleDisplayFrame(visible);
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        int gap = dp(5);
        int top = Math.max(visible.top, rootLocation[1] + root.getPaddingTop()) + gap;
        int bottom = Math.min(visible.bottom, rootLocation[1] + root.getHeight() - root.getPaddingBottom()) - gap;
        if (bottom <= top) return;

        menu.measure(View.MeasureSpec.makeMeasureSpec(popup.getWidth(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = Math.min(menu.getMeasuredHeight(), bottom - top);
        if (height < menu.getMeasuredHeight()) {
            ScrollView scroll = new ScrollView(context);
            scroll.addView(menu, MATCH_PARENT, WRAP_CONTENT);
            popup.setContentView(scroll);
        }
        popup.setHeight(height);

        int[] row = new int[2];
        anchor.getLocationOnScreen(row);
        int rowBottom = row[1] + anchor.getHeight();
        int below = rowBottom + gap;
        int desiredTop = below + height <= bottom ? below : row[1] - gap - height;
        int popupTop = Math.max(top, Math.min(desiredTop, bottom - height));
        if (chat != state.current()) {
            Drawable previous = anchor.getBackground();
            anchor.setBackground(historyRowBackground(dark ? 0xff2b2d31 : 0xffededf0));
            popup.setOnDismissListener(() -> anchor.setBackground(previous));
        }
        popup.showAsDropDown(anchor, 0, popupTop - rowBottom, Gravity.RIGHT);
    }

    private void historyMenuItem(LinearLayout menu, PopupWindow popup, int graphic, String name, int color, Runnable run) {
        if (menu.getChildCount() > 0) divider(menu);
        LinearLayout row = horizontal();
        pad(row, 16, 0);
        ImageView icon = new ImageView(context);
        icon.setImageResource(graphic);
        icon.setImageTintList(ColorStateList.valueOf(color));
        row.addView(icon, dp(20), dp(20));
        TextView label = text(name, 15, color);
        pad(label, 10, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        buttonBackground(row, 0, 0, 0);
        row.setContentDescription(name);
        row.setFocusable(true);
        row.setOnClickListener(view -> {
            popup.dismiss();
            run.run();
            });
        menu.addView(row, MATCH_PARENT, dp(48));
    }

    private static final class HistoryItem {
        final String heading;
        final JSONObject chat;

        HistoryItem(String heading, JSONObject chat) {
            this.heading = heading;
            this.chat = chat;
        }
    }

    private final class HistorySelectionList extends ListView {
        private final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        private final Runnable startDrag = this::beginSelectionDrag;
        private final Runnable autoScroll = this::scrollSelection;
        private Set<String> originalSelection;
        private int anchor = INVALID_POSITION, endpoint, pointer = -1;
        private boolean selecting, moved;
        private long downTime;
        private float downX, downY, fingerY;

        HistorySelectionList() { super(context); }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (originalSelection != null) {
                int index = event.findPointerIndex(pointer);
                if (action == MotionEvent.ACTION_MOVE && index >= 0) {
                    fingerY = event.getY(index);
                    moved |= Math.abs(fingerY - downY) > touchSlop;
                    if (moved) {
                        extendSelection();
                        scheduleSelectionScroll();
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) endSelectionGesture();
                else if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer) {
                    pointer = -1;
                    removeCallbacks(autoScroll);
                }
                return true;
            }
            if (action == MotionEvent.ACTION_DOWN) {
                endSelectionGesture();
                if (managingHistory) {
                    downX = event.getX();
                    downY = event.getY();
                    anchor = pointToPosition((int) downX, (int) downY);
                    if (selectionKey(anchor) != null) {
                        downTime = event.getDownTime();
                        pointer = event.getPointerId(0);
                        postDelayed(startDrag, ViewConfiguration.getLongPressTimeout());
                    }
                }
            } else if (action != MotionEvent.ACTION_MOVE || Math.abs(event.getX() - downX) > touchSlop
                    || Math.abs(event.getY() - downY) > touchSlop) {
                removeCallbacks(startDrag);
            }
            return super.dispatchTouchEvent(event);
        }

        private void beginSelectionDrag() {
            String id = selectionKey(anchor);
            if (!managingHistory || !isShown() || id == null) return;
            MotionEvent cancel = MotionEvent.obtain(downTime, android.os.SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, downX, downY, 0);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            requestDisallowInterceptTouchEvent(true);
            originalSelection = new HashSet<>(selectedChats);
            endpoint = anchor;
            selecting = !originalSelection.contains(id);
            if (selecting) selectedChats.add(id);
            else selectedChats.remove(id);
            fingerY = downY;
            moved = false;
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            updateVisibleHistoryChecks();
        }

        private void extendSelection() {
            if (getChildCount() == 0) return;
            int child = 0;
            while (child < getChildCount() - 1 && fingerY >= getChildAt(child).getBottom()) child++;
            int next = getFirstVisiblePosition() + child;
            if (next == endpoint) return;
            boolean changed = false;
            int first = Math.min(anchor, next), last = Math.max(anchor, next);
            for (int row = Math.min(endpoint, next); row <= Math.max(endpoint, next); row++) {
                String id = selectionKey(row);
                if (id == null) continue;
                boolean check = row >= first && row <= last ? selecting : originalSelection.contains(id);
                changed |= check ? selectedChats.add(id) : selectedChats.remove(id);
            }
            endpoint = next;
            if (changed) updateVisibleHistoryChecks();
        }

        private int selectionScrollStep() {
            int edge = Math.min(dp(52), getHeight() / 4);
            if (edge == 0) return 0;
            float proximity = fingerY < edge ? fingerY - edge : Math.max(0f, fingerY - (getHeight() - edge));
            return Math.round(Math.max(-1f, Math.min(1f, proximity / edge)) * dp(14));
        }

        private void scheduleSelectionScroll() {
            removeCallbacks(autoScroll);
            int step = selectionScrollStep();
            if (step != 0 && canScrollList(Integer.signum(step))) postOnAnimation(autoScroll);
        }

        private void scrollSelection() {
            if (originalSelection == null) return;
            scrollListBy(selectionScrollStep());
            extendSelection();
            scheduleSelectionScroll();
        }

        void endSelectionGesture() {
            removeCallbacks(startDrag);
            removeCallbacks(autoScroll);
            if (originalSelection != null) requestDisallowInterceptTouchEvent(false);
            originalSelection = null;
            anchor = INVALID_POSITION;
            pointer = -1;
        }

        @Override protected void onDetachedFromWindow() {
            endSelectionGesture();
            super.onDetachedFromWindow();
        }
    }

    private String selectionKey(int position) {
        if (position < 0 || position >= historyAdapter.getCount()) return null;
        JSONObject chat = historyAdapter.getItem(position).chat;
        return chat == null ? null : string(chat, "id");
    }

    private void updateVisibleHistoryChecks() {
        int first = historyList.getFirstVisiblePosition();
        for (int i = 0; i < historyList.getChildCount(); i++) {
            ImageView check = historyList.getChildAt(i).findViewWithTag(HistoryAdapter.CHECK);
            if (check != null) showHistoryCheck(check, selectedChats.contains(selectionKey(first + i)));
        }
        updateHistorySelection();
    }

    private void showHistoryCheck(ImageView check, boolean selected) {
        if (check.getBackground() != null && check.isSelected() == selected) return;
        check.setSelected(selected);
        check.setBackground(box(selected ? blueSolid : 0, selected ? 0 : muted, 11));
        check.setImageResource(selected ? R.drawable.ic_check : 0);
    }

    private final class HistoryAdapter extends BaseAdapter {
        static final String CHECK = "history_checkbox";
        List<JSONObject> chats = Collections.emptyList();
        List<HistoryItem> rows = Collections.emptyList();

        @Override public int getCount() { return rows.size(); }
        @Override public HistoryItem getItem(int index) { return rows.get(index); }
        @Override public long getItemId(int index) { return index; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int index) { return getItem(index).chat == null ? 0 : 1; }
        @Override public boolean areAllItemsEnabled() { return false; }
        @Override public boolean isEnabled(int index) {
            return getItem(index).chat != null || (index == 0 && !managingHistory);
        }

        @Override public View getView(int index, View recycled, ViewGroup parent) {
            HistoryItem item = getItem(index);
            if (item.chat == null) {
                TextView heading = text(item.heading, managingHistory ? 14 : 12, muted);
                bold(heading);
                if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
                heading.setMinimumHeight(dp(managingHistory ? 35 : 30));
                pad(heading, 8, 4);
                if (index == 0 && !managingHistory) {
                    LinearLayout group = horizontal();
                    group.addView(heading, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
                    group.addView(icon(R.drawable.ic_manage, "管理对话", () -> setHistoryMode(true)), dp(40), dp(39));
                    return group;
                }
                return heading;
            }
            JSONObject chat = item.chat;
            String title = string(chat, "title");
            boolean active = !managingHistory && chat == state.current();
            LinearLayout row = horizontal();
            int height = dp(managingHistory ? 51 : 48);
            row.setMinimumHeight(height);
            row.setBackground(historyRowBackground(active ? tint : 0));
            row.setOnClickListener(view -> {
                if (managingHistory) toggleHistorySelection(chat);
                else activate(chat);
            });
            if (managingHistory) {
                String id = string(chat, "id");
                ImageView check = new ImageView(context);
                check.setTag(CHECK);
                check.setImageTintList(ColorStateList.valueOf(dark ? 0xff101623 : 0xffffffff));
                pad(check, 3, 3);
                showHistoryCheck(check, selectedChats.contains(id));
                LinearLayout.LayoutParams dot = new LinearLayout.LayoutParams(dp(21), dp(21));
                dot.setMarginStart(dp(8));
                dot.setMarginEnd(dp(11));
                row.addView(check, dot);
                row.setContentDescription("选择对话：" + title);
                row.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                    @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                        super.onInitializeAccessibilityNodeInfo(host, info);
                        info.setCheckable(true);
                        info.setChecked(selectedChats.contains(id));
                    }
                });
            } else row.setOnLongClickListener(view -> {
                historyActions(view, chat);
                return true;
            });
            TextView line = text(title, managingHistory ? 16 : 14, active ? blue : ink);
            if (active) bold(line);
            line.setSingleLine();
            line.setEllipsize(TextUtils.TruncateAt.END);
            pad(line, managingHistory ? 1 : 8, 0);
            row.addView(line, new LinearLayout.LayoutParams(0, height, 1));
            if (active) {
                ImageButton more = icon(R.drawable.ic_more, "对话操作：" + title, () -> historyActions(row, chat));
                more.setImageTintList(ColorStateList.valueOf(muted));
                more.setBackground(historyRowBackground(0));
                row.addView(more, dp(44), height);
            }
            return row;
        }
    }

    private boolean prepareSettingsFromChat() {
        if (!closeEdit()) return false;
        services.hideKeyboard();
        settingsFromDrawer = drawer != null && drawer.isRevealed();
        settingsReturnScroll = nearBottom() ? -1 : transcript.getScrollY();
        return true;
    }

    private void openSettingsHome() {
        if (screen != CHAT || !prepareSettingsFromChat()) return;
        screen = SETTINGS;
        show(Motion.PUSH, false);
    }

    private void openChatOptions() {
        if (screen != CHAT || !allowProvider() || !prepareSettingsFromChat()) return;
        editedOptionsChat = state.optionsTarget();
        screen = MODEL_SETTINGS;
        show(Motion.PUSH, false);
    }

    private void openApiSettings() {
        if (screen == API_SETTINGS) return;
        apiFromSettings = screen == SETTINGS;
        if (!apiFromSettings && !prepareSettingsFromChat()) return;
        screen = API_SETTINGS;
        providerDraft = state.apiProvider();
        protocolDraft = state.preferredProtocol(providerDraft);
        providerNameDraft = state.providerName(providerDraft);
        urlDraft = state.preferredAddress(providerDraft);
        modelDraft = state.preferredModel(providerDraft, state.endpoint());
        contextWindowDraft = savedContextWindow(modelDraft);
        keyDraft = "";
        stopModelLookup();
        if (!checking) keyStatus = "";
        show(Motion.PUSH, false);
        queueModelLookup();
    }
    private void label(LinearLayout into, String label) {
        TextView text = text(label, 13, muted);
        bold(text);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.topMargin = dp(15);
        params.bottomMargin = dp(6);
        into.addView(text, params);
    }

    private void buildSettingsHome() {
        header("设置", R.drawable.ic_back, "返回", this::back, false);
        ScrollView scroll = new ScrollView(context);
        layout.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout body = vertical();
        pad(body, 16, 10);
        scroll.addView(body, MATCH_PARENT, WRAP_CONTENT);

        LinearLayout connection = settingsGroup(body, "连接与数据");
        settingsRow(connection, R.drawable.ic_server, "API 设置",
            state.providerName(state.apiProvider()), this::openApiSettings);
        divider(connection);
        settingsRow(connection, R.drawable.ic_server, "SOCKS5 代理", "", this::showProxyProviders);
        divider(connection);
        LinearLayout logRow = settingsLine(connection, R.drawable.ic_request_log);
        Switch logging = new Switch(context);
        logging.setText("记录请求日志");
        logging.setTextColor(ink);
        logging.setTextSize(15);
        logging.setChecked(services.requestLoggingEnabled());
        logging.setOnCheckedChangeListener((view, enabled) -> services.setRequestLoggingEnabled(enabled));
        logging.setPaddingRelative(dp(11), 0, 0, 0);
        logRow.addView(logging, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        divider(connection);
        settingsRow(connection, R.drawable.ic_manage, "管理对话", "", () -> {
            historyFilter = "";
            selectedChats.clear();
            managingHistory = true;
            returnToChat(true);
        });
        divider(connection);
        settingsRow(connection, R.drawable.ic_import_history, "导入 DeepSeek 对话", "", this::showHistoryImport);
        divider(connection);
        settingsRow(connection, R.drawable.ic_mark, "记忆星云", "", this::openNebula);
        divider(connection);
        settingsRow(connection, R.drawable.ic_trash, "清空全部对话", "", this::confirmClearHistory);
        divider(connection);
        LinearLayout compressionRow = settingsLine(connection, R.drawable.ic_compress);
        Switch compression = new Switch(context);
        compression.setText("自动压缩上下文");
        compression.setTextColor(ink);
        compression.setTextSize(15);
        compression.setChecked(state.data.optBoolean("compactContext", true));
        compression.setOnCheckedChangeListener((view, enabled) -> {
            put(state.data, "compactContext", enabled);
            save();
        });
        compression.setPaddingRelative(dp(11), 0, 0, 0);
        compressionRow.addView(compression, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        TextView compressionHelp = text("长对话发送时提取较早内容的片段，保留最近消息和本地完整记录。", 13, muted);
        pad(compressionHelp, 14, 3);
        body.addView(compressionHelp, MATCH_PARENT, WRAP_CONTENT);

        LinearLayout appearance = settingsGroup(body, "应用");
        String[] names = {"跟随系统", "浅色", "深色"};
        String[] themes = {"system", "light", "dark"};
        String current = state.data.optString("theme", "system");
        int index = "light".equals(current) ? 1 : "dark".equals(current) ? 2 : 0;
        settingsRow(appearance, R.drawable.ic_theme, "外观", names[index], () ->
            choiceSheet("外观", names, index, choice -> {
                if (index == choice) return;
                put(state.data, "theme", themes[choice]);
                save();
                show(Motion.FADE, false);
            }));
        divider(appearance);
        String font = ChatFont.followsSystem(state.data) ? "跟随系统" : ChatFont.label(ChatFont.level(state.data));
        settingsRow(appearance, R.drawable.ic_font, "字体大小", font, () -> {
            screen = FONT_SETTINGS;
            show(Motion.PUSH, false);
        });

        divider(appearance);
        settingsRow(appearance, R.drawable.ic_insight, "实验性功能", "", () -> {
            screen = EXPERIMENTS;
            show(Motion.PUSH, false);
        });

        divider(appearance);
        LinearLayout teenRow = settingsLine(appearance, R.drawable.ic_shield);
        Switch teen = new Switch(context);
        teen.setText("未成年人模式");
        teen.setTextColor(ink);
        teen.setTextSize(15);
        teen.setChecked(state.data.optBoolean("teenMode"));
        teen.setPaddingRelative(dp(11), 0, 0, 0);
        teen.setOnCheckedChangeListener((view, enabled) -> {
            put(state.data, "teenMode", enabled);
            save();
        });
        teenRow.addView(teen, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        settingsNote(body, "开启后，后续请求会引导模型给出适龄回复。实际效果取决于所用模型。");

        LinearLayout about = settingsGroup(body, "关于");
        settingsRow(about, R.drawable.ic_continue, "检查更新", appVersion(), () -> {
            toast("正在检查更新");
            services.checkUpdate(true);
        });
        divider(about);
        settingsRow(about, R.drawable.ic_mark, "关于 Asteria", "", () -> {
            screen = ABOUT;
            show(Motion.PUSH, false);
        });
    }

    private void openNebula() {
        if (!nebulaWorking) {
            nebulaWorking = true;
            if (nebulaProgressState == null) nebulaStatus = "正在读取记忆…";
            services.readNebula(snapshot -> {
                if (nebulaProgressState == null || snapshot == null) {
                    nebulaCompleted(snapshot, "", "无法读取本机记忆");
                } else {
                    nebulaWorking = false;
                    updateNebulaSnapshot(snapshot);
                    renderNebula();
                }
            });
        }
        nebulaCategory = null;
        nebulaLimit = 12;
        screen = NEBULA;
        show(Motion.PUSH, false);
    }

    private void buildNebula() {
        header("记忆星云", R.drawable.ic_back, "返回", this::back, false);
        nebulaScroll = new ScrollView(context);
        nebulaScroll.setClipToPadding(false);
        nebulaScroll.setVerticalScrollBarEnabled(false);
        layout.addView(nebulaScroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout body = vertical();
        pad(body, 16, 13);
        nebulaScroll.addView(body, MATCH_PARENT, WRAP_CONTENT);
        boolean wide = context.getResources().getConfiguration().screenWidthDp >= 720;
        LinearLayout overview = body, library = body;
        if (wide) {
            LinearLayout columns = horizontal();
            columns.setGravity(Gravity.TOP);
            columns.setBaselineAligned(false);
            overview = vertical();
            library = vertical();
            LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 44);
            left.setMarginEnd(dp(18));
            columns.addView(overview, left);
            columns.addView(library, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 56));
            body.addView(columns, MATCH_PARENT, WRAP_CONTENT);
        }

        FrameLayout sky = new FrameLayout(context);
        sky.setBackground(box(0xff141c35, 0, 23));
        sky.setClipToOutline(true);
        nebulaSky = new NebulaSky(context, this::showNebulaMemory);
        sky.addView(nebulaSky, MATCH_PARENT, MATCH_PARENT);
        LinearLayout summary = vertical();
        pad(summary, 19, 15);
        TextView local = text("本机存储", 12, 0xffb9c7e8);
        summary.addView(local, WRAP_CONTENT, WRAP_CONTENT);
        nebulaCount = text("", 28, 0xfff2f5ff);
        bold(nebulaCount);
        summary.addView(nebulaCount, WRAP_CONTENT, WRAP_CONTENT);
        sky.addView(summary, new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP | Gravity.START));
        overview.addView(sky, MATCH_PARENT, dp(242));

        LinearLayout management = settingsGroup(overview, "整理与管理");
        LinearLayout recording = settingsLine(management, R.drawable.ic_insight);
        Switch automatic = new Switch(context);
        automatic.setText("自动总结");
        automatic.setTextColor(ink);
        automatic.setTextSize(15);
        automatic.setChecked(services.nebulaEnabled());
        automatic.setPaddingRelative(dp(11), 0, 0, 0);
        automatic.setOnCheckedChangeListener((view, enabled) -> services.setNebulaEnabled(enabled));
        recording.addView(automatic, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        divider(management);
        LinearLayout actions = horizontal();
        nebulaScan = action("整理已有对话", this::collectNebula);
        nebulaClear = action("清空星云", () -> confirmNebulaDelete(null));
        styleNebulaDelete(nebulaClear, false);
        actions.addView(nebulaScan, new LinearLayout.LayoutParams(0, dp(47), 1));
        actions.addView(nebulaClear, new LinearLayout.LayoutParams(0, dp(47), 1));
        management.addView(actions, MATCH_PARENT, WRAP_CONTENT);
        settingsNote(overview, "整理范围按对话最后更新时间计算。整理时会把你发出的部分文字交给当前配置的 " + state.providerName(state.apiProvider())
            + " API。摘要只保存在本机，与对话记录独立，不参与聊天回复。");
        nebulaProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        nebulaProgressBar.setProgressTintList(ColorStateList.valueOf(blue));
        nebulaProgressBar.setProgressBackgroundTintList(ColorStateList.valueOf(edge));
        nebulaProgressBar.setIndeterminateTintList(ColorStateList.valueOf(blue));
        nebulaProgressBar.setMax(1000);
        nebulaProgressBar.setContentDescription("对话整理进度");
        LinearLayout.LayoutParams progressSpace = new LinearLayout.LayoutParams(MATCH_PARENT, dp(8));
        progressSpace.setMargins(dp(3), dp(7), dp(3), dp(3));
        overview.addView(nebulaProgressBar, progressSpace);
        nebulaFeedback = text("", 13, muted);
        pad(nebulaFeedback, 3, 5);
        nebulaFeedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        overview.addView(nebulaFeedback, MATCH_PARENT, WRAP_CONTENT);

        label(library, "记忆记录");
        nebulaFilterStrip = new HorizontalScrollView(context);
        nebulaFilterStrip.setHorizontalScrollBarEnabled(false);
        nebulaFilters = horizontal();
        nebulaFilterStrip.addView(nebulaFilters, WRAP_CONTENT, WRAP_CONTENT);
        library.addView(nebulaFilterStrip, MATCH_PARENT, WRAP_CONTENT);
        nebulaCards = vertical();
        library.addView(nebulaCards, MATCH_PARENT, WRAP_CONTENT);
        gap(body, 10);
        renderNebula();
    }

    private void chooseNebulaCategory(MemoryNebula.Kind kind) {
        nebulaCategory = kind;
        nebulaLimit = 12;
        renderNebula();
    }

    private void showNebulaMemory(MemoryNebula.Entry entry) {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, entry.title);
        settingsNote(panel, entry.detail);
        showSheet(dialog, panel);
    }

    private void renderNebula() {
        if (screen != NEBULA || nebulaSky == null) return;
        nebulaSky.update(nebulaEntries);
        nebulaCount.setText(nebulaEntries.size() + " 条记忆");
        renderNebulaProgress();
        nebulaScan.setText(nebulaStop == null ? "整理已有对话" : nebulaStop.get() ? "正在停止…" : "停止整理");
        enableHistoryButton(nebulaScan, !nebulaWorking || (nebulaStop != null && !nebulaStop.get()));
        enableHistoryButton(nebulaClear, !nebulaWorking && (!nebulaEntries.isEmpty() || nebulaHasLegacy));

        nebulaFilters.removeAllViews();
        addNebulaFilter(null, "全部");
        for (MemoryNebula.Kind kind : MemoryNebula.Kind.values()) addNebulaFilter(kind, kind.label);
        nebulaCards.removeAllViews();
        int count = 0;
        for (MemoryNebula.Entry entry : nebulaEntries) {
            if (nebulaCategory != null && entry.kind != nebulaCategory) continue;
            if (++count <= nebulaLimit) addNebulaCard(entry);
        }
        if (count == 0 && !nebulaWorking) {
            TextView empty = text(nebulaEntries.isEmpty() ? "整理已有对话，或开启自动总结。" : "该类别还没有记忆。", 14, muted);
            pad(empty, 3, 15);
            nebulaCards.addView(empty, MATCH_PARENT, WRAP_CONTENT);
        } else if (count > nebulaLimit) {
            nebulaCards.addView(action("显示更多", () -> {
                nebulaLimit += 12;
                renderNebula();
            }), MATCH_PARENT, WRAP_CONTENT);
        }
    }

    private void renderNebulaProgress() {
        if (screen != NEBULA || nebulaProgressBar == null) return;
        MemoryNebula.Progress state = nebulaProgressState;
        String feedback = nebulaStatus.isEmpty() && nebulaHasLegacy ? "可选择「全部」重新整理旧星云记录" : nebulaStatus;
        nebulaProgressBar.setVisibility(state == null ? View.GONE : View.VISIBLE);
        if (state != null) {
            boolean running = nebulaStop != null;
            nebulaProgressBar.setIndeterminate(state.totalChats == 0 && running);
            int fraction = state.totalChats == 0 ? 1000 : (int) (1000L * state.scanned / state.totalChats);
            nebulaProgressBar.setProgress(running ? Math.min(fraction, 980) : fraction, ValueAnimator.areAnimatorsEnabled());
            String counts = (state.totalChats == 0 ? "" : "已扫描 " + state.scanned + " / " + state.totalChats + " 个对话 · ")
                + "本次已整理 " + state.saved + " 条消息 / " + state.batches + " 批";
            if (state.requesting && running && !nebulaStop.get()) counts += "\n正在调用 API 整理下一批…";
            if (!feedback.isEmpty()) counts += "\n" + feedback;
            feedback = counts;
        }
        nebulaFeedback.setText(feedback);
        nebulaFeedback.setVisibility(feedback.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void addNebulaFilter(MemoryNebula.Kind kind, String name) {
        boolean selected = nebulaCategory == kind;
        TextView filter = action(name, () -> chooseNebulaCategory(kind));
        filter.setTextSize(13);
        filter.setTextColor(selected ? blue : muted);
        filter.setSelected(selected);
        filter.setMinHeight(dp(40));
        buttonBackground(filter, selected ? tint : surface, selected ? blue : edge, 17);
        LinearLayout.LayoutParams style = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        style.setMarginEnd(dp(7));
        nebulaFilters.addView(filter, style);
    }

    private void addNebulaCard(MemoryNebula.Entry entry) {
        LinearLayout card = horizontal();
        card.setGravity(Gravity.TOP);
        pad(card, 13, 11);
        card.setBackground(box(surface, edge, 16));
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        space.topMargin = dp(8);
        nebulaCards.addView(card, space);

        View dot = new View(context);
        dot.setBackground(box(entry.kind.color, 0, 6));
        LinearLayout.LayoutParams marker = new LinearLayout.LayoutParams(dp(8), dp(8));
        marker.topMargin = dp(5);
        marker.setMarginEnd(dp(10));
        card.addView(dot, marker);
        LinearLayout copy = vertical();
        card.addView(copy, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        copy.addView(text(entry.kind.label, 12, muted), MATCH_PARENT, WRAP_CONTENT);
        TextView title = text(entry.title, 15, ink);
        bold(title);
        pad(title, 0, 3);
        copy.addView(title, MATCH_PARENT, WRAP_CONTENT);
        if (!entry.detail.isEmpty()) {
            TextView detail = text(entry.detail, 14, ink);
            pad(detail, 0, 3);
            copy.addView(detail, MATCH_PARENT, WRAP_CONTENT);
        }
        LocalDate updated = Instant.ofEpochMilli(entry.updated).atZone(ZoneId.systemDefault()).toLocalDate();
        String date = updated.getMonthValue() + "月" + updated.getDayOfMonth() + "日记录";
        if (entry.mentions > 1) date = "提及 " + entry.mentions + " 次 · " + date;
        copy.addView(text(date, 12, muted), MATCH_PARENT, WRAP_CONTENT);
        ImageButton remove = icon(R.drawable.ic_trash, "删除记忆 " + title.getText(), () -> confirmNebulaDelete(entry));
        remove.setImageTintList(ColorStateList.valueOf(muted));
        card.addView(remove, dp(44), dp(44));
    }

    private NebulaSummaries.Target nebulaTarget() {
        ApiProvider provider = state.apiProvider();
        String endpoint = state.endpoint();
        ApiProtocol protocol = state.apiProtocol();
        String model = state.preferredModel(provider, endpoint);
        return new NebulaSummaries.Target(provider, protocol, endpoint, model,
            state.preferredContextWindow(provider, endpoint, protocol, model));
    }

    private void collectNebula() {
        if (nebulaStop != null) {
            nebulaStop.set(true);
            nebulaStatus = "等待当前批次完成…";
            renderNebula();
            return;
        }
        if (nebulaWorking) return;
        if (state.chats().length() == 0 && !nebulaHasLegacy) {
            toast("还没有可整理的对话");
            return;
        }
        NebulaSummaries.Target target = nebulaTarget();
        if (!hasKey || target.model.isEmpty()) {
            toast("请先在设置里配置模型和 API");
            return;
        }
        NebulaRange[] ranges = NebulaRange.values();
        String[] labels = new String[ranges.length];
        for (int i = 0; i < ranges.length; i++) labels[i] = ranges[i].label;
        choiceSheet("整理范围", labels, -1, selected -> chooseNebulaRange(ranges[selected], target));
    }

    private void chooseNebulaRange(NebulaRange range, NebulaSummaries.Target target) {
        if (screen != NEBULA || nebulaWorking) return;
        boolean all = range == NebulaRange.ALL;
        long now = System.currentTimeMillis();
        long cutoff = now - java.util.concurrent.TimeUnit.DAYS.toMillis(range.days);
        List<String> ids = new ArrayList<>();
        for (JSONObject chat : state.history("", Collections.emptySet())) {
            long updated = chat.optLong("updated");
            if (all || (updated >= cutoff && updated <= now)) ids.add(string(chat, "id"));
        }
        boolean includeLegacy = all && nebulaHasLegacy;
        if (ids.isEmpty() && !includeLegacy) {
            toast("这个范围内没有可整理的对话");
            return;
        }
        if (range != NebulaRange.MONTH && !all) {
            startNebulaCollection(target, ids, false);
            return;
        }
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, "确认整理" + (all ? "全部历史" : range.label) + "？");
        String count = ids.isEmpty() ? "本次只整理旧星云记录。" : "范围内有 " + ids.size() + " 个对话。";
        if (!ids.isEmpty() && includeLegacy) count += "旧星云记录也会纳入。";
        settingsNote(panel, count);
        TextView warning = text("大范围整理可能消耗大量 Token，并产生 API 费用。已整理过的消息会跳过。", 14,
            dark ? 0xffffd89a : 0xff754607);
        pad(warning, 13, 11);
        warning.setBackground(box(dark ? 0xff352b20 : 0xfffff3df, 0, 12));
        panel.addView(warning, MATCH_PARENT, WRAP_CONTENT);
        sheetActions(panel, "取消", dialog::dismiss, "确认整理", () -> {
            dialog.dismiss();
            startNebulaCollection(target, ids, includeLegacy);
        });
        showSheet(dialog, panel);
    }

    private void startNebulaCollection(NebulaSummaries.Target target, List<String> ids, boolean includeLegacy) {
        if (screen != NEBULA || nebulaWorking) return;
        nebulaWorking = true;
        nebulaStop = new AtomicBoolean();
        nebulaProgressState = new MemoryNebula.Progress(0, ids.size(), 0, 0, 0, 0, false);
        nebulaStatus = "正在读取历史…";
        renderNebula();
        services.collectNebula(target, ids, includeLegacy, nebulaStop, progress -> {
            nebulaProgressState = progress;
            if (!nebulaStop.get()) nebulaStatus = "";
            renderNebulaProgress();
        }, (snapshot, status) -> {
            nebulaStop = null;
            nebulaWorking = false;
            if (snapshot != null) updateNebulaSnapshot(snapshot);
            nebulaStatus = snapshot == null ? "无法读取本机记忆" : status;
            renderNebula();
        });
    }

    private void confirmNebulaDelete(MemoryNebula.Entry entry) {
        if (nebulaWorking) return;
        Dialog dialog = newSheet();
        boolean all = entry == null;
        LinearLayout panel = settingsSheet(dialog, all ? "清空记忆星云？" : "删除这条记忆？");
        settingsNote(panel, all ? "将删除全部星云记录。" : entry.detail.isEmpty() ? entry.title : entry.detail);
        TextView confirm = sheetActions(panel, "取消", dialog::dismiss, all ? "清空星云" : "删除", () -> {
            dialog.dismiss();
            if (nebulaWorking) return;
            nebulaWorking = true;
            nebulaProgressState = null;
            nebulaStatus = "正在保存…";
            renderNebula();
            services.deleteNebula(all ? null : entry.id,
                entries -> nebulaCompleted(entries, all ? "已清空星云" : "已删除记忆", "无法删除记忆，请重试"));
        });
        styleNebulaDelete(confirm, true);
        showSheet(dialog, panel);
    }

    private void styleNebulaDelete(TextView button, boolean confirm) {
        int fill = confirm ? (dark ? 0xff322d2f : 0xfff4eeee) : 0;
        int press = dark ? 0x28e4b4b4 : 0x1aad5f64;
        button.setTextColor(dark ? 0xffe6a9ab : 0xff9e454c);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(press), box(fill, 0, 12),
            box(0xffffffff, 0, 12)));
    }

    private void updateNebulaSnapshot(MemoryNebula.Snapshot snapshot) {
        nebulaEntries = snapshot.entries;
        nebulaHasLegacy = snapshot.hasLegacy;
    }

    private void nebulaCompleted(MemoryNebula.Snapshot snapshot, String success, String failure) {
        nebulaWorking = false;
        if (snapshot != null) updateNebulaSnapshot(snapshot);
        nebulaStatus = snapshot == null ? failure : "";
        if (snapshot != null && !success.isEmpty() && screen == NEBULA) toast(success);
        renderNebula();
    }

    private void buildExperiments() {
        header("实验性功能", R.drawable.ic_back, "返回", this::back, false);
        ScrollView scroll = new ScrollView(context);
        layout.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout body = vertical();
        pad(body, 16, 10);
        scroll.addView(body, MATCH_PARENT, WRAP_CONTENT);

        LinearLayout gestures = settingsGroup(body, "手势");
        experimentSwitch(gestures, R.drawable.ic_back, "预测性返回", PREDICTIVE_BACK, Build.VERSION.SDK_INT >= 34);
        settingsNote(body, Build.VERSION.SDK_INT >= 34
            ? "开启后，从屏幕边缘滑动可预览上一页，或逐步收起侧栏和弹窗。松手取消时会恢复原位。"
            : "应用内返回预览需要 Android 14 或更新版本。");

        LinearLayout panels = settingsGroup(body, "视觉效果");
        experimentSwitch(panels, R.drawable.ic_theme, "弹窗毛玻璃", BLUR_PANELS, Build.VERSION.SDK_INT >= 31);
        if (Build.VERSION.SDK_INT >= 31) {
            divider(panels);
            settingsRow(panels, R.drawable.ic_insight, "预览弹窗", "", () -> composerNotice("效果预览",
                "开启毛玻璃后，弹窗背后的页面会变模糊。也可以在这里试试返回手势。", null, null));
        }
        settingsNote(body, Build.VERSION.SDK_INT >= 31
            ? "设备或系统暂时停用窗口模糊时，弹窗会使用普通遮罩。"
            : "弹窗毛玻璃需要 Android 12 或更新版本。");
        LinearLayout chat = settingsGroup(body, "聊天输入栏");
        boolean glass = GlassComposer.supported();
        experimentSwitch(chat, R.drawable.ic_theme, "输入栏玻璃效果", GLASS_COMPOSER, glass);
        settingsNote(body, glass ? "开启后，滚动经过输入栏下方的聊天内容会实时模糊，按压时会出现柔和的高光。"
            : "输入栏玻璃效果需要 Android 17 QPR2（API 37.2）或更新版本。");
    }

    private void experimentSwitch(LinearLayout group, int icon, String label, String key, boolean available) {
        LinearLayout row = settingsLine(group, icon);
        Switch toggle = new Switch(context);
        toggle.setText(label);
        toggle.setTextColor(ink);
        toggle.setTextSize(15);
        toggle.setPaddingRelative(dp(11), 0, 0, 0);
        toggle.setEnabled(available);
        toggle.setAlpha(available ? 1f : .55f);
        toggle.setChecked(available && state.data.optBoolean(key));
        toggle.setOnCheckedChangeListener((view, enabled) -> {
            put(state.data, key, enabled);
            save();
        });
        row.addView(toggle, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
    }

    private void showHistoryImport() {
        if (!mayChange()) return;
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, "导入 DeepSeek 对话");
        TextView details = text("粘贴官方导出链接，或选择已下载的 ZIP。已有对话会保留，重复记录会跳过。", 14, muted);
        pad(details, 6, 12);
        panel.addView(details, MATCH_PARENT, WRAP_CONTENT);
        EditText address = field("DeepSeek 导出下载链接", false);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        address.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        address.setSaveEnabled(false);
        panel.addView(address, MATCH_PARENT, WRAP_CONTENT);
        gap(panel, 8);
        sheetActions(panel, "选择 ZIP", () -> {
            services.hideKeyboard();
            dialog.dismiss();
            services.pickHistoryArchive();
        }, "下载并导入", () -> {
            URI link = DeepSeekHistoryImport.officialLink(address.getText().toString());
            if (link == null) {
                address.setError("请粘贴 DeepSeek 官方导出的完整 HTTPS 下载链接");
                return;
            }
            services.hideKeyboard();
            address.setText("");
            dialog.dismiss();
            startHistoryImport(link, null);
        });
        showSheet(dialog, panel);
    }

    void startHistoryImport(URI link, Uri document) {
        if (!mayChange()) return;
        if (checking) {
            toast("当前 API 设置保存完成后即可导入。");
            return;
        }
        Set<String> existing = new HashSet<>();
        for (JSONObject chat : objects(state.chats())) existing.add(string(chat, "id"));
        historyImportProgress = new AlertDialog.Builder(context)
            .setTitle("正在导入对话")
            .setMessage("正在读取 DeepSeek 导出包")
            .setCancelable(false)
            .show();
        services.importHistory(link, document, existing);
    }

    void closeHistoryProgress() {
        if (historyImportProgress == null) return;
        historyImportProgress.dismiss();
        historyImportProgress = null;
    }

    void historyImported(List<JSONObject> imported, String error) {
        closeHistoryProgress();
        if (!error.isEmpty()) {
            toast(error);
            return;
        }
        if (imported.isEmpty()) {
            toast("没有可新增的对话");
            return;
        }
        for (JSONObject chat : imported) state.chats().put(chat);
        if (screen == CHAT) refreshHistory();
        toast("已导入 " + imported.size() + " 条对话");
    }

    private String appVersion() {
        try {
            android.content.pm.PackageInfo info = host.getPackageManager().getPackageInfo(host.getPackageName(), 0);
            long build = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return info.versionName + " (" + build + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
            return "";
        }
    }

    private void buildAbout() {
        header("关于 Asteria", R.drawable.ic_back, "返回", this::back, false);
        ScrollView scroll = new ScrollView(context);
        layout.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout body = vertical();
        pad(body, 16, 10);
        scroll.addView(body, MATCH_PARENT, WRAP_CONTENT);

        LinearLayout brand = vertical();
        brand.setGravity(Gravity.CENTER_HORIZONTAL);
        gap(brand, 18);
        ImageView mark = new ImageView(context);
        mark.setImageResource(R.drawable.ic_mark);
        mark.setImageTintList(ColorStateList.valueOf(blue));
        mark.setScaleType(ImageView.ScaleType.CENTER);
        mark.setBackground(box(tint, 0, 22));
        brand.addView(mark, dp(66), dp(66));
        gap(brand, 12);
        TextView name = text("Asteria", 24, ink);
        bold(name);
        name.setGravity(Gravity.CENTER);
        brand.addView(name, MATCH_PARENT, WRAP_CONTENT);
        String version = appVersion();
        if (!version.isEmpty()) {
            TextView versionLabel = text(version, 13, muted);
            versionLabel.setGravity(Gravity.CENTER);
            brand.addView(versionLabel, MATCH_PARENT, WRAP_CONTENT);
        }
        gap(brand, 11);
        TextView description = text("原生 Android AI 聊天客户端。\n支持多渠道接入，可导入 DeepSeek 对话，按模型设置上下文窗口。", 14, muted);
        description.setGravity(Gravity.CENTER);
        pad(description, 16, 0);
        brand.addView(description, MATCH_PARENT, WRAP_CONTENT);
        body.addView(brand, MATCH_PARENT, WRAP_CONTENT);

        LinearLayout project = settingsGroup(body, "项目");
        settingsRow(project, R.drawable.ic_globe, "官网与源码", "GitHub", () -> services.open(AppUpdate.PROJECT_URL));
        divider(project);
        settingsRow(project, R.drawable.ic_continue, "发布记录", "", () -> services.open(AppUpdate.RELEASES_URL));
        divider(project);
        settingsRow(project, R.drawable.ic_edit, "问题反馈", "", () -> services.open(AppUpdate.PROJECT_URL + "/issues"));
        divider(project);
        settingsRow(project, R.drawable.ic_request_log, "项目许可证", "PolyForm NC 1.0.0",
            () -> services.open("https://polyformproject.org/licenses/noncommercial/1.0.0"));
    }

    void updateAvailable(java.util.Optional<AppUpdate.Release> result, boolean requested) {
        if (result == null || !result.isPresent()) {
            if (requested) toast(result == null ? "暂时无法检查更新" : "当前已是最新版本");
            return;
        }
        AppUpdate.Release release = result.get();
        if (!requested && release.tag.equals(offeredUpdate)) return;
        offeredUpdate = release.tag;
        Dialog dialog = newSheet();
        LinearLayout panel = sheetPanel();
        pad(panel, 20, 18);

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(context);
        mark.setImageResource(R.drawable.ic_mark);
        mark.setImageTintList(ColorStateList.valueOf(blueSolid));
        mark.setBackground(box(tint, 0, 14));
        pad(mark, 11, 11);
        titleRow.addView(mark, dp(46), dp(46));
        LinearLayout heading = vertical();
        pad(heading, 12, 0);
        TextView title = text("发现新版本", 20, ink);
        bold(title);
        heading.addView(title);
        heading.addView(text("Asteria " + release.tag.replaceFirst("^v", ""), 13, blueSolid));
        titleRow.addView(heading, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        panel.addView(titleRow, MATCH_PARENT, WRAP_CONTENT);
        gap(panel, 16);
        divider(panel);
        gap(panel, 12);
        TextView notesTitle = text("更新内容", 15, ink);
        bold(notesTitle);
        panel.addView(notesTitle, MATCH_PARENT, WRAP_CONTENT);
        gap(panel, 8);
        ScrollView scroll = sheetScroll(.33f);
        if (release.notes.isEmpty()) {
            scroll.addView(text("该版本未提供更新说明", 14, muted), MATCH_PARENT, WRAP_CONTENT);
        } else {
            MarkdownReply notes = new MarkdownReply(context, ink, muted, blue, surface, edge, tint,
                dark, .88f, services::open, this::copy, ignored -> {});
            notes.setMarkdown(release.notes);
            scroll.addView(notes, MATCH_PARENT, WRAP_CONTENT);
        }
        panel.addView(scroll, MATCH_PARENT, WRAP_CONTENT);
        gap(panel, 12);
        LinearLayout actions = horizontal();
        TextView later = action("稍后", dialog::dismiss);
        later.setTextColor(ink);
        buttonBackground(later, surface, edge, 12);
        actions.addView(later, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView download = action("下载更新", () -> {
            dialog.dismiss();
            services.open(AppUpdate.download(release.tag, AppUpdate.preferMirror(java.util.Locale.getDefault())));
        });
        download.setTextColor(dark ? 0xff0f1725 : 0xffffffff);
        buttonBackground(download, blueSolid, 0, 12);
        LinearLayout.LayoutParams downloadSize = new LinearLayout.LayoutParams(0, dp(46), 1);
        downloadSize.setMarginStart(dp(8));
        actions.addView(download, downloadSize);
        panel.addView(actions, MATCH_PARENT, WRAP_CONTENT);
        showPanel(dialog, panel, false);
    }

    private void buildModelSettings() {
        header("对话高级设置", R.drawable.ic_back, "返回", this::back, false);
        ScrollView scroll = new ScrollView(context);
        layout.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout body = vertical();
        pad(body, 16, 10);
        scroll.addView(body, MATCH_PARENT, WRAP_CONTENT);

        boolean custom = !state.deepSeekFeatures();
        ApiProtocol protocol = state.apiProtocol();
        boolean openAi = protocol == ApiProtocol.OPENAI;
        String model = state.effectiveModel();
        String provider = custom ? state.providerName(state.apiProvider()) + " · " + (model.isEmpty() ? "尚未选择模型" : model)
            : "DeepSeek 官方 · Flash / V4 Pro";
        boolean newChat = state.current() == null;
        String title = newChat ? "新对话" : string(editedOptionsChat, "title");
        settingsNote(body, title + "\n" + provider
            + (newChat ? "\n这些设置从第一条消息开始生效。" : "\n设置仅影响当前对话。"));

        LinearLayout style = settingsGroup(body, "回复风格");
        modelChoice(style, "回复语气", "tone",
            new String[]{"默认", "亲切", "共情", "专业", "直接", "轻松幽默"},
            new String[]{"", "friendly", "empathetic", "professional", "direct", "playful"});
        addModelOption(style, "自定义指令", () -> string(editedModelOptions(), "instructions").isEmpty() ? "默认" : "已设置",
            update -> {
                instructionDraft = string(editedModelOptions(), "instructions");
                screen = INSTRUCTION_SETTINGS;
                show(Motion.PUSH, false);
            });

        LinearLayout output = settingsGroup(body, "输出与推理");
        modelNumber(output, "最大输出 Token", "outputLimit", "限制单次生成的 Token 数。上游模型可能有更低的限制。",
            1, custom ? Integer.MAX_VALUE : 393216, true);
        if (openAi) {
            modelChoice(output, "长度字段", "tokenField",
                new String[]{"max_completion_tokens", "max_tokens（兼容旧接口）"}, new String[]{"", "legacy"});
            modelChoice(output, "推理强度", "reasoning_effort",
                new String[]{"默认", "关闭", "最少", "Low", "中", "High", "很高", "Max"},
                new String[]{"", "none", "minimal", "low", "medium", "high", "xhigh", "max"});
            modelChoice(output, "回复详略", "verbosity", new String[]{"默认", "简短", "适中", "详细"},
                new String[]{"", "low", "medium", "high"});
        } else if (!custom) {
            modelChoice(output, "推理强度", "reasoning_effort",
                new String[]{"默认", "Low", "High", "Max"}, new String[]{"", "low", "high", "max"});
            settingsNote(body, "选择 Low、High 或 Max 会为当前对话打开深度思考。关闭深度思考后，推理强度不生效。");
        }
        if (custom) {
            if (protocol != ApiProtocol.ANTHROPIC) modelChoice(output, "输出格式", "format",
                new String[]{"默认", "JSON 对象"}, new String[]{"", "json"});
            modelInput(output, "停止序列", "stop", "每行一条，最多 4 条。命中后模型停止生成，部分模型不支持。",
                "每行输入一条停止序列", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, raw -> {
                    JSONArray stops = new JSONArray();
                    for (String line : raw.split("\\r?\\n")) if (!line.trim().isEmpty()) stops.put(line);
                    if (stops.length() > 4) throw new IllegalArgumentException("最多 4 条停止序列");
                    return stops.length() == 0 ? null : stops;
                });
        }

        LinearLayout sampling = settingsGroup(body, "采样");
        modelNumber(sampling, "温度", "temperature", custom
            ? "温度与 Top-P 通常只需调整其中一项。DeepSeek Chat 默认 1.0，DeepSeek-R1 默认 0.6。"
            : "默认 1.0。温度设置在关闭「深度思考」时生效。", 0, protocol == ApiProtocol.ANTHROPIC && custom ? 1 : 2, false);
        if (custom) {
            modelNumber(sampling, "Top-P", "top_p", "控制采样时考虑的候选词范围。",
                0, 1, false);
        }
        if (openAi) {
            modelNumber(sampling, "频率惩罚", "frequency_penalty", "正值减少重复用词，负值更容许重复。",
                -2, 2, false);
            modelNumber(sampling, "存在惩罚", "presence_penalty", "正值鼓励模型探索尚未提及的话题。",
                -2, 2, false);
        }
        if (custom && protocol != ApiProtocol.ANTHROPIC) {
            modelInput(sampling, "随机种子", "seed", "部分接口用固定种子提高复现性。相同种子也可能生成不同结果。",
                "上游默认", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED, Long::valueOf);
        }
        if (openAi) {
            LinearLayout extension = settingsGroup(body, "接口扩展");
            modelInput(extension, "额外请求参数", "extras", "填写上游文档支持的 JSON 对象。已在本页提供或由客户端管理的字段请使用对应设置。",
                "{\"top_k\": 40}", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, raw -> {
                    if (raw.length() > 8000) throw new IllegalArgumentException("最多 8000 个字符");
                    try {
                        org.json.JSONTokener parser = new org.json.JSONTokener(raw);
                        Object parsed = parser.nextValue();
                        if (!(parsed instanceof JSONObject) || parser.nextClean() != 0) {
                            throw new IllegalArgumentException("请输入一个 JSON 对象");
                        }
                        JSONObject extra = (JSONObject) parsed;
                        String key = ModelOptions.unsupportedKey(extra);
                        if (key != null) throw new IllegalArgumentException("此处不能设置字段 " + key);
                        return extra.length() == 0 ? null : extra;
                    } catch (org.json.JSONException error) {
                        throw new IllegalArgumentException("请输入一个 JSON 对象");
                    }
                });
        }

        gap(body, 12);
        String resetTitle = "恢复默认";
        TextView reset = action(resetTitle, () -> {
            Dialog dialog = newSheet();
            LinearLayout panel = settingsSheet(dialog, resetTitle);
            TextView note = text("将这条对话的高级设置恢复为默认值？", 14, muted);
            pad(note, 6, 14);
            panel.addView(note, MATCH_PARENT, WRAP_CONTENT);
            sheetActions(panel, "取消", dialog::dismiss, "恢复", () -> {
                ModelOptions.resetLocal(editedOptionsChat);
                if (state.current() != null) save();
                dialog.dismiss();
                show(Motion.FADE, false);
            });
            showSheet(dialog, panel);
        });
        body.addView(reset, MATCH_PARENT, WRAP_CONTENT);
    }

    private void settingsNote(LinearLayout group, String message) {
        TextView note = text(message, 13, muted);
        pad(note, 4, 6);
        group.addView(note, MATCH_PARENT, WRAP_CONTENT);
    }

    private JSONObject editedModelOptions() {
        return ModelOptions.local(editedOptionsChat);
    }

    private void storeModelOption(String key, Object value) {
        ModelOptions.setLocal(editedOptionsChat, key, value);
        if (state.deepSeekFeatures() && "reasoning_effort".equals(key) && value != null) {
            ModelOptions.setLocal(editedOptionsChat, "think", true);
        }
        if (state.current() != null) save();
    }

    private void modelChoice(LinearLayout group, String title, String key, String[] labels, String[] values) {
        addModelOption(group, title, () -> {
            JSONObject options = editedModelOptions();
            String stored = string(options, key);
            int index = Math.max(0, java.util.Arrays.asList(values).indexOf(stored));
            return labels[index];
        }, (update) -> {
            JSONObject options = editedModelOptions();
            String current = string(options, key);
            int valueIndex = Math.max(0, java.util.Arrays.asList(values).indexOf(current));
            choiceSheet(title, labels, valueIndex, choice -> {
                String value = values[choice];
                storeModelOption(key, value.isEmpty() ? null : value);
                update.run();
            });
        });
    }

    private void modelNumber(LinearLayout group, String title, String key, String help, double min, double max, boolean whole) {
        String low = min == (int) min ? String.valueOf((int) min) : String.valueOf(min);
        String high = max == (int) max ? String.valueOf((int) max) : String.valueOf(max);
        int inputType = InputType.TYPE_CLASS_NUMBER | (whole ? 0 : InputType.TYPE_NUMBER_FLAG_DECIMAL)
            | (min < 0 ? InputType.TYPE_NUMBER_FLAG_SIGNED : 0);
        modelInput(group, title, key, help, low + "–" + high, inputType, raw -> {
            double number = whole ? Integer.parseInt(raw) : Double.parseDouble(raw.replace(',', '.'));
            if (!Double.isFinite(number) || number < min || number > max) {
                throw new IllegalArgumentException("请输入 " + low + "–" + high + " 范围内的数值");
            }
            if (whole) return (int) number;
            return number;
        });
    }

    private void closeInstructionSettings() {
        instructionEditor = null;
        instructionDraft = "";
        screen = MODEL_SETTINGS;
        show(Motion.POP, false);
    }

    private void buildInstructionSettings() {
        header("自定义指令", R.drawable.ic_back, "返回", this::back, false);
        LinearLayout body = vertical();
        pad(body, 16, 5);
        layout.addView(body, MATCH_PARENT, WRAP_CONTENT);
        settingsNote(body, "填写希望模型遵循的指令。填写后会替换 DeepSeek 的默认提示词。");

        instructionEditor = field("例如：用简体中文回答，先给结论", false);
        instructionEditor.setContentDescription("自定义指令");
        instructionEditor.setSingleLine(false);
        instructionEditor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        instructionEditor.setGravity(Gravity.TOP | Gravity.START);
        instructionEditor.setVerticalScrollBarEnabled(true);
        instructionEditor.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        instructionEditor.setText(instructionDraft);
        instructionEditor.setSelection(instructionEditor.length());
        int viewport = root.getHeight() - root.getPaddingTop() - root.getPaddingBottom();
        int height = Math.round(viewport * .75f);
        body.addView(instructionEditor, new LinearLayout.LayoutParams(MATCH_PARENT, height, 1));
        sheetActions(body, "恢复默认", () -> {
            storeModelOption("instructions", null);
            services.hideKeyboard();
            closeInstructionSettings();
        }, "保存", () -> {
            String entered = instructionEditor.getText().toString().trim();
            storeModelOption("instructions", entered.isEmpty() ? null : entered);
            services.hideKeyboard();
            closeInstructionSettings();
        });
        layout.requestFocus();
    }

    private void modelInput(LinearLayout group, String title, String key, String help, String hint, int type,
            java.util.function.Function<String, Object> parse) {
        addModelOption(group, title, () -> {
            Object value = editedModelOptions().opt(key);
            if (value == null || value == JSONObject.NULL) return "默认";
            return value instanceof Number ? value.toString() : "已设置";
        }, update -> {
            JSONObject options = editedModelOptions();
            Object value = options.opt(key);
            String initial = options.isNull(key) ? "" : value.toString();
            if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                List<String> lines = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) lines.add(array.optString(i));
                initial = String.join("\n", lines);
            }
            Dialog dialog = newSheet();
            LinearLayout panel = settingsSheet(dialog, title);
            ScrollView scroll = sheetScroll(.46f);
            panel.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
            LinearLayout content = vertical();
            pad(content, 6, 12);
            scroll.addView(content, MATCH_PARENT, WRAP_CONTENT);
            TextView explanation = text(help, 14, muted);
            content.addView(explanation, MATCH_PARENT, WRAP_CONTENT);
            gap(content, 8);
            EditText editor = field(hint, false);
            editor.setContentDescription(title);
            editor.setInputType(type);
            editor.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            if ((type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0) {
                editor.setSingleLine(false);
                editor.setMinLines(2);
                editor.setMaxLines(5);
                editor.setGravity(Gravity.TOP | Gravity.START);
            }
            editor.setText(initial);
            editor.setSelection(editor.length());
            content.addView(editor, MATCH_PARENT, WRAP_CONTENT);
            sheetActions(panel, "恢复默认", () -> {
                storeModelOption(key, null);
                dialog.dismiss();
                update.run();
            }, "保存", () -> {
                String entered = editor.getText().toString();
                try {
                    String raw = "stop".equals(key) ? entered : entered.trim();
                    Object result = raw.trim().isEmpty() ? null : parse.apply(raw);
                    storeModelOption(key, result);
                    dialog.dismiss();
                    update.run();
                } catch (NumberFormatException error) {
                    editor.setError("请输入有效的数值");
                } catch (IllegalArgumentException error) {
                    editor.setError(error.getMessage());
                }
            });
            panel.requestFocus();
            showSheet(dialog, panel);
        });
    }

    private TextView sheetActions(LinearLayout panel, String secondaryLabel, Runnable secondary, String primaryLabel, Runnable primary) {
        LinearLayout buttons = horizontal();
        pad(buttons, 6, 5);
        TextView reset = action(secondaryLabel, secondary);
        reset.setTextColor(muted);
        pad(reset, 14, 0);
        buttons.addView(reset, WRAP_CONTENT, dp(44));
        weight(buttons);
        TextView save = action(primaryLabel, primary);
        save.setTextColor(dark ? 0xff101623 : 0xffffffff);
        bold(save);
        pad(save, 18, 0);
        buttonBackground(save, blueSolid, 0, 14);
        buttons.addView(save, WRAP_CONTENT, dp(44));
        panel.addView(buttons, MATCH_PARENT, WRAP_CONTENT);
        return save;
    }

    private void addModelOption(LinearLayout group, String title, java.util.function.Supplier<String> read,
            java.util.function.Consumer<Runnable> open) {
        if (group.getChildCount() > 0) divider(group);
        LinearLayout row = settingsLine(group, 0);
        row.addView(text(title, 15, ink), new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        TextView value = text("", 13, muted);
        row.addView(value, WRAP_CONTENT, MATCH_PARENT);
        Runnable update = () -> {
            String label = read.get();
            value.setText(label);
            row.setContentDescription(title + "，" + label);
        };
        addSettingsChevron(row);
        update.run();
        row.setOnClickListener(view -> open.accept(update));
    }

    private void buildFontSettings() {
        header("字体大小", R.drawable.ic_back, "返回", this::back, false);
        ScrollView previewScroll = new ScrollView(context);
        layout.addView(previewScroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout preview = vertical();
        pad(preview, 18, 22);
        previewScroll.addView(preview, MATCH_PARENT, WRAP_CONTENT);

        TextView sampleMessage = text("预览文字大小", 16, ink);
        sampleMessage.setBackground(userBubble(tint));
        pad(sampleMessage, 12, 9);
        LinearLayout.LayoutParams sampleBubble = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        sampleBubble.gravity = Gravity.END;
        preview.addView(sampleMessage, sampleBubble);
        gap(preview, 16);
        TextView sampleReply = text("拖动滑块可调节聊天文字。\n\n设置会应用到消息、代码块和输入框。", 16, ink);
        sampleReply.setLineSpacing(dp(2), 1.1f);
        preview.addView(sampleReply, MATCH_PARENT, WRAP_CONTENT);

        LinearLayout controls = vertical();
        controls.setBackground(box(surface, edge, 18));
        pad(controls, 16, 10);
        LinearLayout.LayoutParams panel = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        panel.setMargins(dp(12), dp(8), dp(12), dp(12));
        layout.addView(controls, panel);
        Switch follow = new Switch(context);
        follow.setText("跟随系统");
        follow.setTextColor(ink);
        follow.setTextSize(15);
        follow.setChecked(ChatFont.followsSystem(state.data));
        controls.addView(follow, MATCH_PARENT, dp(46));

        LinearLayout labels = horizontal();
        labels.addView(text("A", 13, muted), WRAP_CONTENT, WRAP_CONTENT);
        TextView size = text("", 14, ink);
        size.setGravity(Gravity.CENTER);
        labels.addView(size, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        labels.addView(text("A", 21, muted), WRAP_CONTENT, WRAP_CONTENT);
        controls.addView(labels, MATCH_PARENT, WRAP_CONTENT);
        SeekBar bar = new SeekBar(context);
        bar.setMax(ChatFont.lastLevel());
        bar.setProgress(ChatFont.level(state.data));
        controls.addView(bar, MATCH_PARENT, dp(42));

        Runnable update = () -> {
            float scale = ChatFont.scale(state.data);
            ChatFont.apply(sampleMessage, 16, scale);
            ChatFont.apply(sampleReply, 16, scale);
            boolean system = ChatFont.followsSystem(state.data);
            String value = ChatFont.label(ChatFont.level(state.data));
            size.setText(system ? "系统字号" : value);
            bar.setContentDescription("字体大小，" + value);
            bar.setEnabled(!system);
        };
        follow.setOnCheckedChangeListener((view, enabled) -> {
            put(state.data, "chatFontSystem", enabled);
            save();
            update.run();
        });
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean dragging;
            @Override public void onProgressChanged(SeekBar view, int progress, boolean fromUser) {
                if (!fromUser) return;
                put(state.data, "chatFontLevel", progress);
                update.run();
                if (!dragging) save();
            }
            @Override public void onStartTrackingTouch(SeekBar view) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar view) {
                dragging = false;
                save();
            }
        });
        update.run();
    }

    private LinearLayout settingsGroup(LinearLayout body, String name) {
        label(body, name);
        LinearLayout group = vertical();
        group.setBackground(box(surface, edge, 16));
        group.setClipToOutline(true);
        body.addView(group, MATCH_PARENT, WRAP_CONTENT);
        return group;
    }

    private LinearLayout settingsLine(LinearLayout group, int graphic) {
        LinearLayout row = horizontal();
        pad(row, 14, 0);
        group.addView(row, MATCH_PARENT, dp(54));
        if (graphic != 0) {
            ImageView glyph = new ImageView(context);
            glyph.setImageResource(graphic);
            glyph.setImageTintList(ColorStateList.valueOf(blue));
            row.addView(glyph, dp(22), dp(22));
        }
        return row;
    }

    private void addSettingsChevron(LinearLayout row) {
        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron);
        chevron.setImageTintList(ColorStateList.valueOf(muted));
        chevron.setRotation(-90f);
        LinearLayout.LayoutParams tail = new LinearLayout.LayoutParams(dp(16), dp(16));
        tail.setMarginStart(dp(6));
        row.addView(chevron, tail);
        buttonBackground(row, 0, 0, 0);
        row.setFocusable(true);
    }

    private void settingsRow(LinearLayout group, int graphic, String name, String value, Runnable click) {
        LinearLayout row = settingsLine(group, graphic);
        TextView title = text(name, 15, ink);
        pad(title, 11, 0);
        row.addView(title, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
        if (value != null && !value.isEmpty()) row.addView(text(value, 13, muted), WRAP_CONTENT, MATCH_PARENT);
        if (click != null) {
            addSettingsChevron(row);
            row.setContentDescription(value.isEmpty() ? name : name + "，" + value);
            row.setOnClickListener(view -> click.run());
        }
    }

    private void showProxyProviders() {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, "SOCKS5 代理");
        LinearLayout providers = settingsGroup(panel, "模型提供商");
        for (ApiProvider provider : ApiProvider.values()) {
            if (provider != ApiProvider.DEEPSEEK) divider(providers);
            settingsRow(providers, R.drawable.ic_server, state.providerName(provider),
                services.proxyEnabled(provider) ? "已启用" : "已关闭", () -> {
                    dialog.dismiss();
                    showProviderProxy(provider);
                });
        }
        showSheet(dialog, panel);
    }

    private void showProviderProxy(ApiProvider provider) {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, state.providerName(provider) + " · SOCKS5");
        Switch enabled = new Switch(context);
        enabled.setText("为此提供商启用代理");
        enabled.setTextColor(ink);
        enabled.setChecked(services.proxyEnabled(provider));
        panel.addView(enabled, MATCH_PARENT, dp(48));

        ScrollView scroll = sheetScroll(.55f);
        LinearLayout form = vertical();
        scroll.addView(form, MATCH_PARENT, WRAP_CONTENT);
        panel.addView(scroll, MATCH_PARENT, WRAP_CONTENT);
        label(form, "代理地址");
        EditText proxyAddress = field("域名或 IP 地址", false);
        proxyAddress.setSingleLine();
        proxyAddress.setText(services.proxyHost(provider));
        form.addView(proxyAddress, MATCH_PARENT, WRAP_CONTENT);
        label(form, "端口");
        EditText proxyPort = field("1080", false);
        proxyPort.setInputType(InputType.TYPE_CLASS_NUMBER);
        proxyPort.setSingleLine();
        proxyPort.setText(Integer.toString(services.proxyPort(provider)));
        form.addView(proxyPort, MATCH_PARENT, WRAP_CONTENT);
        label(form, "用户名");
        EditText username = field("无认证时留空", false);
        username.setSingleLine();
        username.setSaveEnabled(false);
        form.addView(username, MATCH_PARENT, WRAP_CONTENT);
        label(form, "密码");
        EditText password = field("无认证时留空", true);
        password.setSingleLine();
        password.setSaveEnabled(false);
        form.addView(password, MATCH_PARENT, WRAP_CONTENT);
        try {
            java.net.PasswordAuthentication saved = services.proxyCredentials(provider);
            if (saved != null) {
                username.setText(saved.getUserName());
                password.setText(new String(saved.getPassword()));
            }
        } catch (Exception ignored) {
            settingsNote(form, "无法读取已保存的 SOCKS5 凭据，请重新输入");
        }
        EditText[] fields = {proxyAddress, proxyPort, username, password};
        for (EditText input : fields) input.setEnabled(enabled.isChecked());
        enabled.setOnCheckedChangeListener((view, checked) -> {
            for (EditText input : fields) input.setEnabled(checked);
        });
        sheetActions(panel, "取消", dialog::dismiss, "保存", () -> {
            if (!enabled.isChecked()) {
                services.disableProxy(provider);
                if (provider == ApiProvider.DEEPSEEK) invalidateBalance();
                dialog.dismiss();
                return;
            }
            String host = proxyAddress.getText().toString().trim();
            if (host.indexOf(':') >= 0 && !host.startsWith("[")) host = "[" + host + "]";
            try {
                java.net.URI address = new java.net.URI("socks://" + host);
                if (address.getHost() == null || !host.equals(address.getHost())) throw new IllegalArgumentException();
            } catch (java.net.URISyntaxException | IllegalArgumentException error) {
                proxyAddress.setError("请输入域名或 IP 地址");
                return;
            }
            int port;
            try {
                port = Integer.parseInt(proxyPort.getText().toString().trim());
                if (port < 1 || port > 65535) throw new NumberFormatException();
            } catch (NumberFormatException error) {
                proxyPort.setError("请输入 1 到 65535 的端口");
                return;
            }
            String user = username.getText().toString();
            String secret = password.getText().toString();
            if (user.isEmpty() != secret.isEmpty()) {
                (user.isEmpty() ? username : password).setError("认证需要同时填写用户名和密码");
                return;
            }
            java.nio.charset.CharsetEncoder encoder = java.nio.charset.StandardCharsets.ISO_8859_1.newEncoder();
            if (user.length() > 255 || !encoder.canEncode(user)) {
                username.setError("请输入最多 255 个拉丁字符");
                return;
            }
            if (secret.length() > 255 || !encoder.canEncode(secret)) {
                password.setError("请输入最多 255 个拉丁字符");
                return;
            }
            if (!services.saveProxy(provider, host, port, user, secret)) {
                toast("无法保存 SOCKS5 设置");
                return;
            }
            if (provider == ApiProvider.DEEPSEEK) invalidateBalance();
            dialog.dismiss();
        });
        showSheet(dialog, panel);
    }

    private void buildApiSettings() {
        header("API 设置", R.drawable.ic_back, "返回", this::back, false);
        ScrollView scroll = new ScrollView(context);
        layout.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout form = vertical();
        pad(form, 18, 9);
        scroll.addView(form, MATCH_PARENT, WRAP_CONTENT);
        label(form, "模型提供商");
        String[] providers = java.util.Arrays.stream(ApiProvider.values()).map(item -> item.label).toArray(String[]::new);
        providerRow = apiPicker(() -> choiceSheet("模型提供商", providers,
            providerDraft.ordinal(), selected -> selectApiProvider(ApiProvider.values()[selected])));
        form.addView(providerRow, MATCH_PARENT, dp(52));
        label(form, "API 调用协议");
        protocolRow = apiPicker(this::chooseApiProtocol);
        form.addView(protocolRow, MATCH_PARENT, dp(52));
        providerName = null;
        if (providerDraft == ApiProvider.CUSTOM) {
            label(form, "提供商名称");
            providerName = field("例如：我的模型服务", false);
            providerName.setContentDescription("提供商名称");
            if (!providerDraft.label.equals(providerNameDraft)) providerName.setText(providerNameDraft);
            form.addView(providerName, MATCH_PARENT, WRAP_CONTENT);
        }

        label(form, "API 接口");
        apiUrl = field(providerDraft.address.isEmpty() ? "https://example.com/v1" : providerDraft.address, false);
        apiUrl.setContentDescription("API 接口");
        apiUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        apiUrl.setText(urlDraft);
        form.addView(apiUrl, MATCH_PARENT, WRAP_CONTENT);
        resetAddress = null;
        if (!providerDraft.address.isEmpty()) {
            resetAddress = action("恢复默认地址", () -> apiUrl.setText(providerDraft.address));
            resetAddress.setTextSize(12);
            form.addView(resetAddress, WRAP_CONTENT, WRAP_CONTENT);
        }
        if (providerDraft == ApiProvider.OLLAMA) settingsNote(form, "Ollama 运行在电脑上时，请填写电脑的局域网地址和端口。");

        label(form, "密钥");
        apiKey = field("输入 API Key", true);
        apiKey.setContentDescription("密钥");
        apiKey.setText(keyDraft);
        keyDraft = "";
        form.addView(apiKey, MATCH_PARENT, WRAP_CONTENT);
        keyFeedback = text("", 13, muted);
        pad(keyFeedback, 2, 6);
        LinearLayout keyInfo = horizontal();
        keyInfo.addView(keyFeedback, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        manageKeys = action("密钥管理", this::openKeySettings);
        manageKeys.setTextSize(13);
        keyInfo.addView(manageKeys, WRAP_CONTENT, WRAP_CONTENT);
        form.addView(keyInfo, MATCH_PARENT, WRAP_CONTENT);

        label(form, "模型");
        apiModel = field("选择上游模型或输入模型 ID", false);
        apiModel.setContentDescription("模型 ID");
        apiModel.setText(modelDraft);
        modelRow = horizontal();
        modelRow.setBackground(box(surface, edge, 14));
        apiModel.setBackground(null);
        modelRow.addView(apiModel, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        modelArrow = icon(R.drawable.ic_chevron, "选择上游模型", this::chooseUpstreamModel);
        modelArrow.setImageTintList(ColorStateList.valueOf(muted));
        modelRow.addView(modelArrow, dp(48), dp(48));
        form.addView(modelRow, MATCH_PARENT, WRAP_CONTENT);
        LinearLayout modelInfo = horizontal();
        modelsFeedback = text("", 12, muted);
        modelInfo.addView(modelsFeedback, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        refreshModels = action("刷新模型", this::lookupModelsNow);
        refreshModels.setTextSize(12);
        modelInfo.addView(refreshModels);
        form.addView(modelInfo, MATCH_PARENT, WRAP_CONTENT);

        label(form, "模型上下文窗口（Token）");
        apiWindow = field("留空使用默认预算", false);
        apiWindow.setContentDescription("模型上下文窗口");
        apiWindow.setInputType(InputType.TYPE_CLASS_NUMBER);
        apiWindow.setText(contextWindowDraft);
        form.addView(apiWindow, MATCH_PARENT, WRAP_CONTENT);
        settingsNote(form, "按模型保存。用于自动压缩，Ollama 也会收到该设置。");

        gap(form, 10);
        LinearLayout actions = horizontal();
        form.addView(actions, MATCH_PARENT, WRAP_CONTENT);
        verify = action("测试并保存", () -> saveKey(true));
        verify.setTextColor(dark ? 0xff0f1725 : 0xffffffff);
        buttonBackground(verify, blueSolid, 0, 13);
        actions.addView(verify);
        saveOnly = action("直接保存", () -> saveKey(false));
        actions.addView(saveOnly);

        gap(form, 12);
        if (!providerDraft.keyPage.isEmpty()) {
            TextView link = action("打开 " + providerDraft.label + " 密钥管理  ↗", () -> services.open(providerDraft.keyPage));
            link.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            form.addView(link, MATCH_PARENT, WRAP_CONTENT);
        }
        gap(form, 14);
        updateKeyFeedback();
        showModelLookupState();
        watch(apiUrl, value -> {
            String previous = editingEndpoint();
            urlDraft = value;
            String next = editingEndpoint();
            if (!java.util.Objects.equals(previous, next)) {
                apiKey.setText("");
                apiModel.setText(state.preferredModel(providerDraft, next));
                apiWindow.setText(savedContextWindow(apiModel.getText().toString()));
            }
            keyStatus = "";
            updateKeyFeedback();
            queueModelLookup();
        });
        watch(apiKey, value -> queueModelLookup());
        watch(apiModel, value -> apiWindow.setText(savedContextWindow(value)));
    }

    private TextView apiPicker(Runnable open) {
        TextView row = action("", open);
        row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        row.setTextSize(15);
        row.setTextColor(ink);
        row.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, context.getDrawable(R.drawable.ic_chevron), null);
        row.setCompoundDrawableTintList(ColorStateList.valueOf(muted));
        pad(row, 14, 0);
        buttonBackground(row, surface, edge, 14);
        return row;
    }

    private void chooseApiProtocol() {
        if (checking) return;
        String[] labels = java.util.Arrays.stream(ApiProtocol.values()).map(item -> item.label).toArray(String[]::new);
        choiceSheet("API 调用协议", labels, protocolDraft.ordinal(), index -> {
            ApiProtocol next = ApiProtocol.values()[index];
            if (protocolDraft == next) return;
            protocolDraft = next;
            apiWindow.setText(savedContextWindow(apiModel.getText().toString()));
            keyStatus = "";
            updateKeyFeedback();
            queueModelLookup();
        });
    }

    private void openKeySettings() {
        String endpoint = editingEndpoint();
        if (endpoint == null || checking) return;
        services.hideKeyboard();
        captureEndpointFields();
        keyDraft = apiKey.getText().toString();
        keyManagementEndpoint = endpoint;
        screen = KEY_SETTINGS;
        stopModelLookup();
        show(Motion.PUSH, false);
    }

    private void buildKeySettings() {
        header("密钥管理", R.drawable.ic_back, "返回", this::back, false);
        ScrollView scroll = new ScrollView(context);
        layout.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        LinearLayout body = vertical();
        pad(body, 16, 10);
        scroll.addView(body, MATCH_PARENT, WRAP_CONTENT);
        String endpoint = keyManagementEndpoint;
        settingsNote(body, endpoint.isEmpty() ? ApiProvider.DEEPSEEK.address : endpoint);

        LinearLayout strategy = settingsGroup(body, "使用策略");
        KeyVault.Mode mode = services.keyMode(endpoint);
        settingsRow(strategy, R.drawable.ic_sliders, "密钥选择", mode.label, () ->
            choiceSheet("密钥选择", new String[]{"轮询", "随机"}, mode.ordinal(), index -> {
                if (services.setKeyMode(endpoint, KeyVault.Mode.values()[index])) show(Motion.NONE, false);
                else toast("无法保存密钥选择方式");
            }));
        divider(strategy);
        int visible = services.keyVisibleLength(endpoint);
        settingsRow(strategy, R.drawable.ic_key, "首尾显示位数", "各 " + visible + " 位", () -> {
            String[] values = new String[8];
            for (int i = 0; i < values.length; i++) values[i] = "各 " + (i + 1) + " 位";
            choiceSheet("首尾显示位数", values, visible - 1, index -> {
                if (services.setKeyVisibleLength(endpoint, index + 1)) show(Motion.NONE, false);
                else toast("无法保存脱敏设置");
            });
        });
        settingsNote(body, "每条普通对话请求按策略选择一把密钥。含附件的对话沿用附件密钥。");
        if (endpoint.isEmpty()) settingsNote(body, "页脚的密钥合计按币种相加。同一账号的多把密钥可能重复计入同一笔余额。");

        LinearLayout keys = settingsGroup(body, "已保存的密钥");
        try {
            for (KeyVault.Entry entry : services.savedKeys(endpoint)) {
                if (keys.getChildCount() > 0) divider(keys);
                LinearLayout row = settingsLine(keys, R.drawable.ic_key);
                TextView display = text(entry.display, 15, ink);
                display.setTypeface(Typeface.MONOSPACE);
                display.setSingleLine();
                display.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                pad(display, 11, 0);
                row.addView(display, new LinearLayout.LayoutParams(0, MATCH_PARENT, 1));
                ImageButton remove = icon(R.drawable.ic_trash, "移除密钥 " + entry.display,
                    () -> confirmRemoveKey(endpoint, entry));
                remove.setImageTintList(ColorStateList.valueOf(dangerColor()));
                row.addView(remove, dp(44), dp(44));
            }
            if (keys.getChildCount() == 0) settingsNote(keys, "此接口还没有保存密钥");
        } catch (Exception error) {
            settingsNote(keys, "无法读取已保存的密钥");
        }
        gap(body, 12);
        TextView addKey = action("添加密钥", () -> showAddKey(endpoint));
        addKey.setTextColor(dark ? 0xff0f1725 : 0xffffffff);
        buttonBackground(addKey, blueSolid, 0, 13);
        body.addView(addKey, MATCH_PARENT, WRAP_CONTENT);
    }

    private void showAddKey(String endpoint) {
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, "添加密钥");
        EditText value = field("输入 API Key", true);
        value.setContentDescription("新密钥");
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        spacing.topMargin = dp(10);
        panel.addView(value, spacing);
        sheetActions(panel, "取消", dialog::dismiss, "保存", () -> {
            String key = value.getText().toString().trim();
            if (key.isEmpty() || key.matches(".*\\s.*")) {
                value.setError("请填写有效的 API Key");
                return;
            }
            String error = services.addKey(endpoint, key);
            if (!error.isEmpty()) {
                value.setError(error);
                return;
            }
            value.setText("");
            dialog.dismiss();
            updateKey();
            show(Motion.NONE, false);
        });
        showSheet(dialog, panel);
    }

    private void confirmRemoveKey(String endpoint, KeyVault.Entry entry) {
        if (!mayChange()) return;
        Dialog dialog = newSheet();
        LinearLayout panel = settingsSheet(dialog, "移除密钥？");
        settingsNote(panel, entry.display);
        sheetActions(panel, "取消", dialog::dismiss, "移除", () -> {
            dialog.dismiss();
            if (!mayChange()) return;
            String error = services.removeKey(endpoint, entry.id);
            if (!error.isEmpty()) toast(error);
            updateKey();
            show(Motion.NONE, false);
        });
        showSheet(dialog, panel);
    }

    private void selectApiProvider(ApiProvider next) {
        if (checking || next == providerDraft) return;
        services.hideKeyboard();
        providerDraft = next;
        protocolDraft = state.preferredProtocol(next);
        providerNameDraft = state.providerName(next);
        urlDraft = state.preferredAddress(next);
        modelDraft = state.preferredModel(next, editingEndpoint());
        contextWindowDraft = savedContextWindow(modelDraft);
        keyDraft = "";
        keyStatus = "";
        stopModelLookup();
        show(Motion.FADE, false);
        queueModelLookup();
    }

    private void captureEndpointFields() {
        if (apiUrl != null) urlDraft = apiUrl.getText().toString();
        if (apiModel != null) modelDraft = apiModel.getText().toString();
        if (apiWindow != null) contextWindowDraft = apiWindow.getText().toString();
        if (providerName != null) providerNameDraft = providerName.getText().toString().trim();
    }

    private String editingEndpoint() {
        if (urlDraft == null) return null;
        ApiProtocol selection = protocolDraft == ApiProtocol.AUTO ? providerDraft.protocol : protocolDraft;
        return providerDraft.endpoint(urlDraft, selection);
    }

    private ApiProtocol editingProtocol() { return protocolDraft.resolve(providerDraft, editingEndpoint()); }

    private String savedContextWindow(String model) {
        String id = model.trim();
        ApiProtocol protocol = editingProtocol();
        if (protocol == ApiProtocol.GEMINI) {
            String normalized = ApiProtocol.geminiModel(id);
            if (normalized != null) id = normalized;
        }
        int limit = state.preferredContextWindow(providerDraft, editingEndpoint(), protocol, id);
        return limit > 0 ? Integer.toString(limit) : "";
    }

    private void stopModelLookup() {
        if (modelLookup != null) root.removeCallbacks(modelLookup);
        modelLookup = null;
        modelLookupId++;
        upstreamModels = new String[0];
        modelsStatus = "";
    }

    private void queueModelLookup() {
        if (screen != API_SETTINGS) return;
        stopModelLookup();
        String endpoint = editingEndpoint();
        if (endpoint != null && (!ApiProvider.requiresKey(endpoint) || apiKey.length() > 0 || services.hasKey(endpoint))) {
            modelsStatus = "正在读取上游模型…";
            modelLookup = this::lookupModelsNow;
            root.postDelayed(modelLookup, 1200);
        } else if (endpoint != null) {
            modelsStatus = "填写密钥后自动读取模型";
        }
        showModelLookupState();
    }

    private void lookupModelsNow() {
        if (screen != API_SETTINGS) return;
        stopModelLookup();
        String endpoint = editingEndpoint();
        if (endpoint == null) {
            modelsStatus = "请先填写有效的 API 接口地址";
        } else {
            String candidate = apiKey.getText().toString().trim();
            if (candidate.matches(".*\\s.*")) modelsStatus = "请检查 API Key。";
            else if (ApiProvider.requiresKey(endpoint) && candidate.isEmpty() && !services.hasKey(endpoint)) {
                modelsStatus = "填写密钥后自动读取模型";
            }
            else {
                modelsStatus = "正在读取上游模型…";
                services.discoverModels(modelLookupId, providerDraft, endpoint, editingProtocol(), candidate);
            }
        }
        showModelLookupState();
    }

    private void showModelLookupState() {
        if (screen != API_SETTINGS || modelArrow == null) return;
        boolean choices = !checking && ("".equals(editingEndpoint()) || upstreamModels.length > 0);
        modelArrow.setEnabled(choices);
        modelArrow.setAlpha(choices ? 1 : .35f);
        modelsFeedback.setText(modelsStatus);
    }

    private static String officialModelName(String model) {
        return FLASH.equals(model) ? "DeepSeek Flash" : PRO.equals(model) ? "DeepSeek V4 Pro" : model;
    }

    private void chooseUpstreamModel() {
        if (checking) return;
        boolean nativeDeepSeek = "".equals(editingEndpoint());
        String[] ids = nativeDeepSeek && upstreamModels.length == 0 ? new String[]{FLASH, PRO} : upstreamModels;
        if (ids.length == 0) return;
        String[] labels = ids.clone();
        if (nativeDeepSeek) for (int i = 0; i < labels.length; i++) labels[i] = officialModelName(labels[i]);
        String current = apiModel.getText().toString().trim();
        int selected = java.util.Arrays.asList(ids).indexOf(current);
        choicePopup(modelRow, modelArrow, labels, selected, index -> apiModel.setText(ids[index]));
    }
    private void updateKeyFeedback() {
        if (screen != API_SETTINGS || keyFeedback == null) return;
        String endpoint = editingEndpoint();
        boolean saved = endpoint != null && services.hasKey(endpoint);
        keyFeedback.setText(!keyStatus.isEmpty() ? keyStatus : saved ? "此地址已保存密钥" : "此地址尚未保存密钥");
        apiKey.setHint(saved ? "留空使用已保存的密钥" : endpoint != null && !ApiProvider.requiresKey(endpoint)
            ? "无需鉴权时可留空" : "输入 API Key");
        providerRow.setText(providerDraft.label);
        providerRow.setContentDescription("模型提供商，当前：" + providerDraft.label);
        providerRow.setEnabled(!checking);
        String protocolLabel = protocolDraft == ApiProtocol.AUTO ? "自动 · " + editingProtocol().label : protocolDraft.label;
        protocolRow.setText(protocolLabel);
        protocolRow.setContentDescription("API 调用协议，当前：" + protocolLabel);
        protocolRow.setEnabled(!checking);
        apiUrl.setEnabled(!checking);
        apiKey.setEnabled(!checking);
        apiModel.setEnabled(!checking);
        apiWindow.setEnabled(!checking);
        refreshModels.setEnabled(!checking);
        if (providerName != null) providerName.setEnabled(!checking);
        if (resetAddress != null) {
            resetAddress.setEnabled(!checking && !providerDraft.address.equals(urlDraft.trim()));
            resetAddress.setAlpha(resetAddress.isEnabled() ? 1 : .45f);
        }
        verify.setEnabled(!checking);
        verify.setAlpha(checking ? .5f : 1);
        if (saveOnly != null) { saveOnly.setEnabled(!checking); saveOnly.setAlpha(checking ? .5f : 1); }
        manageKeys.setEnabled(endpoint != null && !checking);
        manageKeys.setAlpha(manageKeys.isEnabled() ? 1f : .45f);
        showModelLookupState();
    }

    private void saveKey(boolean test) {
        if (checking) return;
        captureEndpointFields();
        String endpoint = editingEndpoint();
        ApiProtocol protocol = editingProtocol();
        String model = modelDraft.trim();
        String candidate = apiKey.getText().toString().trim();
        if (endpoint == null) { keyStatus = "请输入有效的 HTTP 或 HTTPS 接口地址。"; updateKeyFeedback(); return; }
        if (model.isEmpty() || model.matches(".*\\s.*")) { keyStatus = "请填写有效的模型 ID。"; updateKeyFeedback(); return; }
        if (protocol == ApiProtocol.GEMINI) {
            model = ApiProtocol.geminiModel(model);
            if (model == null) { keyStatus = "请填写有效的 Gemini 模型 ID。"; updateKeyFeedback(); return; }
        }
        int context = 0;
        if (!contextWindowDraft.trim().isEmpty()) {
            try {
                context = Integer.parseInt(contextWindowDraft.trim());
                if (context < 2) throw new NumberFormatException();
            } catch (NumberFormatException error) {
                apiWindow.setError("请输入大于 1 的整数，或留空使用默认预算");
                return;
            }
        }
        if (providerDraft == ApiProvider.CUSTOM && providerNameDraft.length() > 40) {
            keyStatus = "提供商名称最多 40 个字符";
            updateKeyFeedback();
            return;
        }
        if ((ApiProvider.requiresKey(endpoint) && candidate.isEmpty() && !services.hasKey(endpoint)) || candidate.matches(".*\\s.*")) {
            keyStatus = "请填写有效的 API Key。"; updateKeyFeedback(); return;
        }
        if (busy() && (providerDraft != state.apiProvider() || !endpoint.equals(state.endpoint()) || protocol != state.apiProtocol())) {
            toast("当前操作完成后即可切换接口和协议。");
            return;
        }
        services.hideKeyboard();
        checking = true;
        pendingUrl = endpoint;
        pendingModel = model;
        pendingProvider = providerDraft;
        pendingProtocol = protocolDraft;
        pendingContextWindow = context;
        pendingProviderName = providerDraft == ApiProvider.CUSTOM ? providerNameDraft : "";
        keyStatus = test ? "正在测试连接…" : "正在保存…";
        updateKeyFeedback();
        services.verify(providerDraft, endpoint, protocol, model, candidate, test);
    }

    private void choicePopup(View anchor, ImageView arrow, String[] choices, int current, java.util.function.IntConsumer selected) {
        services.hideKeyboard();
        ListPopupWindow popup = new ListPopupWindow(context);
        popup.setAnchorView(anchor);
        popup.setModal(true);
        popup.setAnimationStyle(R.style.Jingliu_PanelAnimation);
        popup.setInputMethodMode(ListPopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setWidth(anchor.getWidth());
        if (choices.length > 5) popup.setHeight(dp(280));
        popup.setVerticalOffset(dp(5));
        popup.setBackgroundDrawable(box(bg, edge, 14));
        popup.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return choices.length; }
            @Override public Object getItem(int i) { return choices[i]; }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View recycled, ViewGroup parent) {
                LinearLayout row = horizontal();
                row.setMinimumHeight(dp(50));
                pad(row, 14, 4);
                if (current == i) row.setBackground(box(tint, 0, 10));
                TextView item = text(choices[i], 15, current == i ? blue : ink);
                item.setMaxLines(2);
                item.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(item, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
                if (current == i) {
                    ImageView check = new ImageView(context);
                    check.setImageResource(R.drawable.ic_check);
                    check.setImageTintList(ColorStateList.valueOf(blue));
                    row.addView(check, dp(22), dp(22));
                }
                return row;
            }
        });
        popup.setOnDismissListener(() -> arrow.animate().rotation(0).setDuration(120).start());
        popup.setOnItemClickListener((parent, item, index, id) -> {
            popup.dismiss();
            selected.accept(index);
        });
        popup.show();
        if (current >= 0) popup.setSelection(current);
        arrow.animate().rotation(180).setDuration(120).start();
    }

    private void watch(EditText field, java.util.function.Consumer<String> read) {
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { read.accept(value.toString()); }
            @Override public void afterTextChanged(android.text.Editable value) {}
        });
    }

    private void editDialog(String title, String initial, String save, java.util.function.Consumer<String> use) {
        Dialog dialog = newSheet();
        LinearLayout panel = sheetPanel();
        panel.setFocusableInTouchMode(true);
        TextView heading = text(title, 17, ink);
        heading.setGravity(Gravity.CENTER);
        bold(heading);
        panel.addView(heading, MATCH_PARENT, dp(47));
        LinearLayout editor = vertical();
        pad(editor, 6, 12);
        EditText value = field("对话名称", false);
        value.setContentDescription("对话名称");
        value.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        value.setText(initial);
        value.setSelection(value.length());
        editor.addView(value, MATCH_PARENT, WRAP_CONTENT);
        panel.addView(editor, MATCH_PARENT, WRAP_CONTENT);
        sheetActions(panel, "取消", dialog::dismiss, save, () -> {
            String name = value.getText().toString().trim();
            if (name.isEmpty()) {
                value.setError("请输入对话名称");
                return;
            }
            dialog.dismiss();
            use.accept(name);
        });
        panel.requestFocus();
        showPanel(dialog, panel, false);
    }

    void event(String type, JSONObject event) {
        switch (type) {
            case "stream":
                if (state.job == null || state.job.id != event.optInt("id")) break;
                ChatState.Job active = state.job;
                boolean hadReasoning = active.hasReasoning;
                JSONObject chunk = event.optJSONObject("event");
                state.receive(chunk);
                if (!hadReasoning && active.hasReasoning) unfolded.add(active.reply);
                if (screen == CHAT && chunk != null && "message_delta".equals(chunk.optString("type"))) updateChatHeader();
                if (state.job == null) finish(active, "");
                else if (screen == CHAT && !renderPending) {
                    renderPending = true;
                    messages.postDelayed(() -> {
                        renderPending = false;
                        boolean follow = nearBottom();
                        renderLive(state.job != null);
                        if (screen == CHAT && state.job != null && follow) bottom();
                    }, 75);
                }
                break;
            case "streamEnd":
                if (state.job != null && state.job.id == event.optInt("id")) {
                    finish(state.job, state.job.receivedFinish ? "" : event.optString("error", "连接中断，可以重新生成。"));
                }
                break;
            case "key": {
                String error = string(event, "error");
                if (checking && pendingUrl.equals(string(event, "endpoint"))) {
                    checking = false;
                    boolean saved = error.isEmpty() && event.optBoolean("saved");
                    keyStatus = error.isEmpty() ? event.optString("message", "API Key 已保存") : error;
                    if (saved) {
                        state.saveConnection(pendingProvider, pendingUrl, pendingModel, pendingProviderName, pendingProtocol, pendingContextWindow);
                        save();
                        if (screen == API_SETTINGS) {
                            providerDraft = pendingProvider;
                            protocolDraft = pendingProtocol;
                            providerNameDraft = state.providerName(providerDraft);
                            urlDraft = state.preferredAddress(providerDraft);
                            modelDraft = pendingModel;
                            contextWindowDraft = savedContextWindow(modelDraft);
                            keyDraft = "";
                            show(Motion.NONE, false);
                            queueModelLookup();
                        }
                    }
                    pendingUrl = pendingModel = null;
                    pendingProvider = null;
                    pendingProtocol = null;
                    pendingProviderName = null;
                }
                updateKey();
                updateKeyFeedback();
                controls();
                if (screen != API_SETTINGS) toast(keyStatus);
                break;
            }
            case "models": {
                if (screen != API_SETTINGS || event.optInt("id") != modelLookupId) break;
                JSONArray items = array(event, "items");
                upstreamModels = new String[items.length()];
                for (int i = 0; i < items.length(); i++) upstreamModels[i] = items.optString(i);
                modelsStatus = event.optString("error", items.length() > 0 ? "已读取 " + items.length() + " 个上游模型" :
                    "上游未返回模型，可手动填写模型 ID。");
                showModelLookupState();
                break;
            }
            case "upload":
                upload = false;
                if (event.has("error")) toast(string(event, "error"));
                else if (event.has("id")) {
                    if (pendingThumbnail != null) attachmentThumbnails.put(event.optString("id"), pendingThumbnail);
                    state.attachments.add(event);
                }
                pendingAttachment = null;
                pendingThumbnail = null;
                controls();
                break;
            case "voice":
                recording = event.optBoolean("recording");
                transcribing = event.optBoolean("transcribing");
                if (event.has("error")) toast(string(event, "error").trim().replaceFirst("[。.]$", ""));
                if (event.has("text")) {
                    if (holdingVoice) heldVoiceText = string(event, "text");
                    else applyVoiceText(string(event, "text"));
                }
                controls();
                break;
            case "toast": toast(string(event, "message")); break;
            case "speech":
                speaking = string(event, "id");
                for (Map.Entry<String, ImageButton> button : speechButtons.entrySet()) {
                    updateSpeechButton(button.getKey(), button.getValue());
                }
                break;
            default: break;
        }
    }

    private void toast(String value) {
        if (!value.isEmpty()) Toast.makeText(host, value, Toast.LENGTH_SHORT).show();
    }
}
