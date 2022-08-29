package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.ui.custom.CustomListener;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.Hot;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Suggest;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivitySearchBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.net.Callback;
import com.fongmi.android.tv.net.OKHttp;
import com.fongmi.android.tv.ui.custom.CustomKeyboard;
import com.fongmi.android.tv.ui.custom.CustomRowPresenter;
import com.fongmi.android.tv.ui.custom.CustomSelector;
import com.fongmi.android.tv.ui.presenter.VodPresenter;
import com.fongmi.android.tv.ui.presenter.WordPresenter;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Utils;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Response;

public class SearchActivity extends BaseActivity implements VodPresenter.OnClickListener, WordPresenter.OnClickListener {

    private final ActivityResultLauncher<String> launcherString = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> onVoice());

    private ActivitySearchBinding mBinding;
    private SpeechRecognizer mRecognizer;
    private SiteViewModel mSiteViewModel;
    private ArrayObjectAdapter mWordAdapter;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private ExecutorService mService;
    private List<Site> mSites;
    private Handler mHandler;
    private Animation mFlicker;

    private String getKeyword() {
        return getIntent().getStringExtra("keyword");
    }

    private boolean hasVoice() {
        return SpeechRecognizer.isRecognitionAvailable(this);
    }

    public static void start(Activity activity) {
        start(activity, "");
    }

    public static void start(Activity activity, String keyword) {
        Intent intent = new Intent(activity, SearchActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra("keyword", keyword);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        check();
    }

    @Override
    protected void initView() {
        mFlicker = ResUtil.getAnim(R.anim.flicker);
        mHandler = new Handler(Looper.getMainLooper());
        mRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        mBinding.voice.setVisibility(hasVoice() ? View.VISIBLE : View.GONE);
        mBinding.keyword.requestFocus();
        CustomKeyboard.init(mBinding);
        setRecyclerView();
        setViewModel();
        setSite();
        getHot();
        check();
    }

    @Override
    protected void initEvent() {
        mBinding.voice.setOnClickListener(view -> onVoice());
        mBinding.search.setOnClickListener(view -> startSearch());
        mBinding.clear.setOnClickListener(view -> mBinding.keyword.setText(""));
        mBinding.remote.setOnClickListener(view -> PushActivity.start(this));
        mBinding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) mBinding.search.performClick();
            return true;
        });
        mBinding.keyword.addTextChangedListener(new CustomListener() {
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().isEmpty()) getHot();
                else getSuggest(s.toString());
            }
        });
        mRecognizer.setRecognitionListener(new CustomListener() {
            @Override
            public void onResults(String result) {
                mBinding.search.requestFocus();
                mBinding.voice.clearAnimation();
                mBinding.keyword.setText(result);
                mBinding.keyword.setSelection(mBinding.keyword.length());
            }
        });
    }

    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(ListRow.class, new CustomRowPresenter(16), VodPresenter.class);
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.word.setVerticalSpacing(ResUtil.dp2px(16));
        mBinding.word.setAdapter(new ItemBridgeAdapter(mWordAdapter = new ArrayObjectAdapter(new WordPresenter(this))));
    }

    private void setViewModel() {
        mSiteViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mSiteViewModel.result.observe(this, result -> {
            if (mService != null) addVideo(result.getList());
        });
    }

    private void setSite() {
        mSites = new ArrayList<>();
        for (Site site : ApiConfig.get().getSites()) if (site.isSearchable()) mSites.add(site);
        Site home = ApiConfig.get().getHome();
        if (!mSites.contains(home)) return;
        mSites.remove(home);
        mSites.add(0, home);
    }

    private void check() {
        if (getKeyword().isEmpty()) return;
        stopSearch();
        mAdapter.clear();
        mBinding.keyword.setText(getKeyword());
        startSearch();
    }

    private void onVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            launcherString.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            mBinding.voice.startAnimation(mFlicker);
            mRecognizer.startListening(intent);
        }
    }

    private boolean checkLastSize(List<Vod> items) {
        if (mLast == null || items.size() == 0) return false;
        int size = 5 - mLast.size();
        if (size == 0) return false;
        size = Math.min(size, items.size());
        mLast.addAll(mLast.size(), new ArrayList<>(items.subList(0, size)));
        addVideo(new ArrayList<>(items.subList(size, items.size())));
        return true;
    }

    private void addVideo(List<Vod> items) {
        if (checkLastSize(items)) return;
        List<ListRow> rows = new ArrayList<>();
        for (List<Vod> part : Lists.partition(items, 5)) {
            mLast = new ArrayObjectAdapter(new VodPresenter(this));
            mLast.setItems(part, null);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
        mBinding.progressLayout.showContent();
    }

    private void startSearch() {
        String keyword = mBinding.keyword.getText().toString().trim();
        mBinding.keyword.setSelection(mBinding.keyword.length());
        if (TextUtils.isEmpty(keyword)) return;
        mService = Executors.newFixedThreadPool(5);
        for (Site site : mSites) mService.execute(() -> mSiteViewModel.searchContent(site.getKey(), keyword));
        Utils.hideKeyboard(mBinding.keyword);
        showResult();
    }

    private void stopSearch() {
        if (mService == null) return;
        mService.shutdownNow();
        mService = null;
        mLast = null;
    }

    private void showResult() {
        mBinding.layout.setVisibility(View.GONE);
        mBinding.progressLayout.setVisibility(View.VISIBLE);
        mBinding.progressLayout.showProgress();
    }

    private void hideResult() {
        mBinding.clear.requestFocus();
        mBinding.layout.setVisibility(View.VISIBLE);
        mBinding.progressLayout.setVisibility(View.INVISIBLE);
    }

    private boolean isResultVisible() {
        return mBinding.progressLayout.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onItemClick(Vod item) {
        DetailActivity.start(this, item.getSite().getKey(), item.getVodId());
    }

    @Override
    public boolean onLongClick(Vod item) {
        return false;
    }

    @Override
    public void onItemClick(String text) {
        mBinding.keyword.setText(text);
        startSearch();
    }

    private void getHot() {
        OKHttp.newCall("https://node.video.qq.com/x/api/hot_mobilesearch?channdlId=0").enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> items = Hot.get(response.body().string());
                mHandler.post(() -> mWordAdapter.setItems(items, null));
            }
        });
    }

    private void getSuggest(String text) {
        OKHttp.newCall("https://suggest.video.iqiyi.com/?if=mobile&key=" + text).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                List<String> items = Suggest.get(response.body().string());
                mHandler.post(() -> mWordAdapter.setItems(items, null));
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (isResultVisible() && getKeyword().isEmpty()) {
            mAdapter.clear();
            hideResult();
            stopSearch();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecognizer.destroy();
        stopSearch();
    }
}
