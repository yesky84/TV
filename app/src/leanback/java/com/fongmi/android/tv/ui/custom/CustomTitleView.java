package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.ApiConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.SiteCallback;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Utils;

import java.util.List;

public class CustomTitleView extends AppCompatTextView {

    private Listener mListener;
    private Animation mFlicker;

    public CustomTitleView(@NonNull Context context) {
        super(context);
    }

    public CustomTitleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        mFlicker = ResUtil.getAnim(R.anim.flicker);
    }

    public void setListener(Listener listener) {
        this.mListener = listener;
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focused) startAnimation(mFlicker);
        else clearAnimation();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (ApiConfig.get().getSites().isEmpty()) return false;
        if (hasEvent(event)) return onKeyDown(event);
        else return super.dispatchKeyEvent(event);
    }

    private boolean onKeyDown(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP && Utils.isEnterKey(event)) {
            mListener.showDialog();
        } else if (event.getAction() == KeyEvent.ACTION_DOWN && Utils.isLeftKey(event)) {
            mListener.setSite(getSite(true));
        } else if (event.getAction() == KeyEvent.ACTION_DOWN && Utils.isRightKey(event)) {
            mListener.setSite(getSite(false));
        }
        return true;
    }

    private Site getSite(boolean next) {
        List<Site> items = ApiConfig.get().getSites();
        int position = ApiConfig.getHomeIndex();
        if (next) position = position > 0 ? --position : items.size() - 1;
        else position = position < items.size() - 1 ? ++position : 0;
        return items.get(position);
    }

    private boolean hasEvent(KeyEvent event) {
        return Utils.isEnterKey(event) || Utils.isLeftKey(event) || Utils.isRightKey(event);
    }

    public interface Listener extends SiteCallback {

        void showDialog();
    }
}
