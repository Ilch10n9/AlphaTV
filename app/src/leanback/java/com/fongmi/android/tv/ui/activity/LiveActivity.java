package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Setting;
import com.fongmi.android.tv.api.LiveConfig;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.event.ErrorEvent;
import com.fongmi.android.tv.event.PlayerEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.LiveCallback;
import com.fongmi.android.tv.impl.PassCallback;
import com.fongmi.android.tv.impl.SubtitleCallback;
import com.fongmi.android.tv.model.LiveViewModel;
import com.fongmi.android.tv.player.ExoUtil;
import com.fongmi.android.tv.player.Players;
import com.fongmi.android.tv.player.Source;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.CustomKeyDownLive;
import com.fongmi.android.tv.ui.custom.CustomLiveListView;
import com.fongmi.android.tv.ui.custom.dialog.LiveDialog;
import com.fongmi.android.tv.ui.custom.dialog.PassDialog;
import com.fongmi.android.tv.ui.custom.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.custom.dialog.TrackDialog;
import com.fongmi.android.tv.ui.presenter.ChannelPresenter;
import com.fongmi.android.tv.ui.presenter.GroupPresenter;
import com.fongmi.android.tv.utils.Biometric;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Response;
import tv.danmaku.ijk.media.player.ui.IjkVideoView;

public class LiveActivity extends BaseActivity implements GroupPresenter.OnClickListener, ChannelPresenter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, TrackDialog.Listener, Biometric.Callback, PassCallback, LiveCallback, SubtitleCallback {

    private ActivityLiveBinding mBinding;
    private ArrayObjectAdapter mChannelAdapter;
    private ArrayObjectAdapter mGroupAdapter;
    private SimpleDateFormat mFormatDate;
    private SimpleDateFormat mFormatTime;
    private CustomKeyDownLive mKeyDown;
    private LiveViewModel mViewModel;
    private List<Group> mHides;
    private Players mPlayers;
    private Channel mChannel;
    private View mOldView;
    private Group mGroup;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private boolean confirm;
    private int toggleCount;
    private int count;

    public static void start(Activity activity) {
        if (!LiveConfig.isEmpty()) activity.startActivity(new Intent(activity, LiveActivity.class));
    }

    private PlayerView getExo() {
        return Setting.getRender() == 0 ? mBinding.surface : mBinding.texture;
    }

    private IjkVideoView getIjk() {
        return mBinding.ijk;
    }

    private Group getKeep() {
        return (Group) mGroupAdapter.get(0);
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    private int getPlayerType(int playerType) {
        return playerType != -1 ? playerType : getHome().getPlayerType() != -1 ? getHome().getPlayerType() : Setting.getLivePlayer();
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView() {
        mR0 = this::hideUI;
        mR1 = this::hideInfo;
        mR2 = this::hideControl;
        mR3 = this::setChannelActivated;
        mR4 = this::setTraffic;
        mHides = new ArrayList<>();
        mPlayers = new Players().init(this);
        mKeyDown = CustomKeyDownLive.create(this);
        mClock = Clock.create(mBinding.widget.time);
        mFormatDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        mFormatTime = new SimpleDateFormat("yyyy-MM-ddHH:mm", Locale.getDefault());
        setRecyclerView();
        setVideoView();
        setViewModel();
        getLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        mBinding.channel.setListener(this);
        mBinding.control.seek.setListener(mPlayers);
        mBinding.control.text.setOnClickListener(this::onTrack);
        mBinding.control.audio.setOnClickListener(this::onTrack);
        mBinding.control.video.setOnClickListener(this::onTrack);
        mBinding.control.home.setOnClickListener(view -> onHome());
        mBinding.control.line.setOnClickListener(view -> onLine());
        mBinding.control.scale.setOnClickListener(view -> onScale());
        mBinding.control.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.invert.setOnClickListener(view -> onInvert());
        mBinding.control.across.setOnClickListener(view -> onAcross());
        mBinding.control.change.setOnClickListener(view -> onChange());
        mBinding.control.player.setOnClickListener(view -> onPlayer());
        mBinding.control.decode.setOnClickListener(view -> onDecode());
        mBinding.control.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mGroupAdapter.size() > 0) onChildSelected(child, mGroup = (Group) mGroupAdapter.get(position));
            }
        });
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.group.setAdapter(new ItemBridgeAdapter(mGroupAdapter = new ArrayObjectAdapter(new GroupPresenter(this))));
        mBinding.channel.setAdapter(new ItemBridgeAdapter(mChannelAdapter = new ArrayObjectAdapter(new ChannelPresenter(this))));
    }

    private void setPlayerView() {
        getIjk().setPlayer(mPlayers.getPlayer());
        mBinding.control.player.setText(mPlayers.getPlayerText());
        getExo().setVisibility(mPlayers.isExo() ? View.VISIBLE : View.GONE);
        getIjk().setVisibility(mPlayers.isIjk() ? View.VISIBLE : View.GONE);
    }

    private void setDecodeView() {
        mBinding.control.decode.setText(mPlayers.getDecodeText());
    }

    private void setVideoView() {
        mPlayers.set(getExo(), getIjk());
        setScale(Setting.getLiveScale());
        setSubtitle(Setting.getSubtitle());
        mBinding.control.speed.setText(mPlayers.getSpeedText());
        mBinding.control.invert.setActivated(Setting.isInvert());
        mBinding.control.across.setActivated(Setting.isAcross());
        mBinding.control.change.setActivated(Setting.isChange());
        mBinding.control.home.setVisibility(LiveConfig.isOnly() ? View.GONE : View.VISIBLE);
        findViewById(R.id.timeBar).setNextFocusUpId(R.id.player);
        getExo().getSubtitleView().setStyle(ExoUtil.getCaptionStyle());
        getIjk().getSubtitleView().setStyle(ExoUtil.getCaptionStyle());
    }

    private void setScale(int scale) {
        getExo().setResizeMode(scale);
        getIjk().setResizeMode(scale);
        mBinding.control.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mViewModel.channel.observe(this, result -> mPlayers.start(result, getHome().getTimeout()));
        mViewModel.live.observe(this, live -> {
            hideProgress();
            setGroup(live);
        });
    }

    private void getLive() {
        mPlayers.setPlayer(getPlayerType(-1));
        mViewModel.getLive(getHome());
        setPlayerView();
        setDecodeView();
        showProgress();
    }

    private void setGroup(Live home) {
        List<Group> items = new ArrayList<>();
        items.add(Group.create(R.string.keep));
        for (Group group : home.getGroups()) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.setItems(items, null);
        setPosition(LiveConfig.get().find(items));
        mBinding.control.home.setText(home.getName());
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        if (mGroupAdapter.size() == 1) return;
        if (position[0] >= mGroupAdapter.size()) return;
        mGroup = (Group) mGroupAdapter.get(position[0]);
        mBinding.group.setSelectedPosition(position[0]);
        mGroup.setPosition(position[1]);
        onItemClick(mGroup);
        onItemClick(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mBinding.group.getSelectedPosition() != position;
        if (change) mBinding.group.setSelectedPosition(position);
        if (change) mChannelAdapter.setItems(mGroup.getChannel(), null);
        mBinding.channel.setSelectedPosition(mGroup.getPosition());
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, Group group) {
        if (mOldView != null) mOldView.setSelected(false);
        if (child == null) return;
        mOldView = child.itemView;
        mOldView.setSelected(true);
        onItemClick(group);
        resetPass();
    }

    private void setChannelActivated() {
        for (int i = 0; i < mChannelAdapter.size(); i++) ((Channel) mChannelAdapter.get(i)).setSelected(mChannel);
        notifyItemChanged(mBinding.channel, mChannelAdapter);
        fetch();
    }

    private void onTrack(View view) {
        TrackDialog.create().player(mPlayers).type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private boolean onTextLong() {
        SubtitleDialog.create(this).show();
        hideControl();
        return true;
    }

    private void onHome() {
        LiveDialog.create(this).show();
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        int index = Setting.getLiveScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        Setting.putLiveScale(index = index == array.length - 1 ? 0 : ++index);
        setScale(index);
    }

    private void onSpeed() {
        mBinding.control.speed.setText(mPlayers.addSpeed());
    }

    private boolean onSpeedLong() {
        mBinding.control.speed.setText(mPlayers.toggleSpeed());
        return true;
    }

    private void onInvert() {
        Setting.putInvert(!Setting.isInvert());
        mBinding.control.invert.setActivated(Setting.isInvert());
    }

    private void onAcross() {
        Setting.putAcross(!Setting.isAcross());
        mBinding.control.across.setActivated(Setting.isAcross());
    }

    private void onChange() {
        Setting.putChange(!Setting.isChange());
        mBinding.control.change.setActivated(Setting.isChange());
    }

    private void onPlayer() {
        mPlayers.togglePlayer();
        Setting.putLivePlayer(mPlayers.getPlayer());
        setPlayerView();
        fetch();
    }

    private void onDecode() {
        mPlayers.toggleDecode();
        mPlayers.set(getExo(), getIjk());
        setDecodeView();
        fetch();
    }

    private void hideUI() {
        App.removeCallbacks(mR0);
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
        setPosition();
    }

    private void showProgress() {
        mBinding.widget.progress.setVisibility(View.VISIBLE);
        App.post(mR4, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.widget.progress.setVisibility(View.GONE);
        App.removeCallbacks(mR4);
        Traffic.reset();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl(View view) {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        view.requestFocus();
        setR2Callback();
        hideInfo();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showInfo() {
        mBinding.widget.info.setVisibility(View.VISIBLE);
        setR1Callback();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.info.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void showEpg() {
        mBinding.widget.play.setText(mChannel.getData().getEpg());
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.widget.traffic);
        App.post(mR4, Constant.INTERVAL_TRAFFIC);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback() {
        App.post(mR2, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork(String url) {
        ImgUtil.load(url, R.drawable.radio, new CustomTarget<Drawable>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                getExo().setDefaultArtwork(resource);
                getIjk().setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable error) {
                getExo().setDefaultArtwork(error);
                getIjk().setDefaultArtwork(error);
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }
        });
    }

    @Override
    public void onItemClick(Group item) {
        mChannelAdapter.setItems(item.getChannel(), null);
        mBinding.channel.setSelectedPosition(Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        if (Biometric.enable()) Biometric.show(this);
        else PassDialog.create().show(this);
        App.removeCallbacks(mR0);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        mGroup.setPosition(mBinding.channel.getSelectedPosition());
        setChannel(item.group(mGroup));
        hideUI();
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = Keep.exist(item.getName());
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        return true;
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        keep.save();
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        if (mChannelAdapter.size() == 0) mBinding.group.requestFocus();
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setChannel(Channel item) {
        mPlayers.setPlayer(getPlayerType(item.getPlayerType()));
        setArtwork(item.getLogo());
        App.post(mR3, 100);
        mChannel = item;
        setPlayerView();
        showInfo();
    }

    private void setInfo() {
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.name.setText(mChannel.getName());
        mBinding.control.name.setText(mChannel.getName());
        mBinding.widget.line.setText(mChannel.getLineText());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.control.number.setText(mChannel.getNumber());
        mBinding.control.line.setText(mChannel.getLineText());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.line.setVisibility(mChannel.getLineVisible());
        showEpg();
        checkEpg();
    }

    private void checkEpg() {
        if (mChannel.getEpg().isEmpty()) return;
        String date = mFormatDate.format(new Date());
        String epg = mChannel.getEpg().replace("{date}", date);
        if (!mChannel.getData().equal(date)) getEpg(epg, mChannel);
    }

    private void getEpg(String epg, Channel channel) {
        OkHttp.newCall(epg).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                channel.setData(Epg.objectFrom(response.body().string(), mFormatTime));
                if (mChannel.equals(channel)) App.post(() -> showEpg());
            }
        });
    }

    private void fetch() {
        if (mChannel == null) return;
        LiveConfig.get().setKeep(mChannel);
        mViewModel.fetch(mChannel);
        showProgress();
    }

    private void release() {
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
    }

    @Override
    public void onTrackClick(Track item) {
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
        mPlayers.stop();
        hideControl();
        release();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    @Override
    public void onBiometricSuccess() {
        unlock(null);
    }

    private void unlock(String pass) {
        boolean first = true;
        int position = mGroupAdapter.size();
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(mGroupAdapter.size(), item);
            if (first) mBinding.group.setSelectedPosition(position);
            if (first) onItemClick(mGroup = item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPlayerEvent(PlayerEvent event) {
        switch (event.getState()) {
            case 0:
                setTrackVisible(false);
                break;
            case Player.STATE_IDLE:
                break;
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                resetToggle();
                hideProgress();
                mPlayers.reset();
                setSpeedVisible();
                setTrackVisible(true);
                break;
            case Player.STATE_ENDED:
                nextChannel();
                break;
        }
    }

    private void setSpeedVisible() {
        mBinding.control.speed.setVisibility(mPlayers.isVod() ? View.VISIBLE : View.GONE);
    }

    private void setTrackVisible(boolean visible) {
        mBinding.control.text.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_TEXT) ? View.VISIBLE : View.GONE);
        mBinding.control.audio.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.video.setVisibility(visible && mPlayers.haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onErrorEvent(ErrorEvent event) {
        if (mPlayers.addRetry() > event.getRetry()) onError(event);
        else fetch();
    }

    private void checkError(ErrorEvent event) {
        if (getHome().getPlayerType() == -1 && event.isFormat() && event.getRetry() > 0 && getToggleCount() < 2 && mPlayers.getPlayer() != Players.SYS) {
            toggleCount++;
            nextPlayer();
        } else {
            resetToggle();
            onError(event);
        }
    }

    private void nextPlayer() {
        mPlayers.nextPlayer();
        setPlayerView();
        fetch();
    }

    private void onError(ErrorEvent event) {
        showError(event.getMsg());
        mPlayers.stop();
        startFlow();
    }

    private void startFlow() {
        if (!Setting.isChange()) return;
        if (!mChannel.isLast()) {
            nextLine(true);
        } else if (isGone(mBinding.recycler)) {
            mChannel.setLine(0);
            nextChannel();
        }
    }

    private void prevChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() - 1;
        boolean limit = position < 0;
        if (Setting.isAcross() & limit) prevGroup(true);
        else mGroup.setPosition(limit ? mChannelAdapter.size() - 1 : position);
        if (!mGroup.isEmpty()) setChannel(mGroup.current());
    }

    private void nextChannel() {
        if (mGroup == null) return;
        int position = mGroup.getPosition() + 1;
        boolean limit = position > mChannelAdapter.size() - 1;
        if (Setting.isAcross() && limit) nextGroup(true);
        else mGroup.setPosition(limit ? 0 : position);
        if (!mGroup.isEmpty()) setChannel(mGroup.current());
    }

    private void prevLine() {
        mChannel.prevLine();
        showInfo();
        fetch();
    }

    private void nextLine(boolean show) {
        mChannel.nextLine();
        if (show) showInfo();
        else setInfo();
        fetch();
    }

    private void seekTo(int time) {
        mPlayers.seekTo(time);
        showProgress();
        hideCenter();
    }

    private void setConfirm() {
        confirm = true;
        Notify.show(R.string.app_exit);
        App.post(() -> confirm = false, 5000);
    }

    public int getToggleCount() {
        return toggleCount;
    }

    public void resetToggle() {
        this.toggleCount = 0;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVisible(mBinding.control.getRoot())) setR2Callback();
        if (mKeyDown.hasEvent(event)) mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setUITimer() {
        App.post(mR0, Constant.INTERVAL_HIDE);
    }

    @Override
    public boolean nextGroup(boolean skip) {
        int position = mBinding.group.getSelectedPosition() + 1;
        if (position > mGroupAdapter.size() - 1) position = 0;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = (Group) mGroupAdapter.get(position);
        mBinding.group.setSelectedPosition(position);
        if (skip && mGroup.skip()) return nextGroup(true);
        mChannelAdapter.setItems(mGroup.getChannel(), null);
        mGroup.setPosition(0);
        return true;
    }

    @Override
    public boolean prevGroup(boolean skip) {
        int position = mBinding.group.getSelectedPosition() - 1;
        if (position < 0) position = mGroupAdapter.size() - 1;
        if (mGroup.equals(mGroupAdapter.get(position))) return false;
        mGroup = (Group) mGroupAdapter.get(position);
        mBinding.group.setSelectedPosition(position);
        if (skip && mGroup.skip()) return prevGroup(true);
        mChannelAdapter.setItems(mGroup.getChannel(), null);
        mGroup.setPosition(mGroup.getChannel().size() - 1);
        return true;
    }

    @Override
    public boolean dispatch(boolean check) {
        return !check || isGone(mBinding.recycler) && isGone(mBinding.control.getRoot());
    }

    @Override
    public void onShow(String number) {
        mBinding.widget.digital.setText(number);
        mBinding.widget.digital.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFind(String number) {
        mBinding.widget.digital.setVisibility(View.GONE);
        setPosition(LiveConfig.get().find(number, mGroupAdapter.unmodifiableList()));
    }

    @Override
    public void onSeeking(int time) {
        if (!mPlayers.isVod() || !mChannel.isOnly()) return;
        mBinding.widget.exoDuration.setText(mPlayers.getDurationTime());
        mBinding.widget.exoPosition.setText(mPlayers.getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onKeyUp() {
        prevChannel();
    }

    @Override
    public void onKeyDown() {
        nextChannel();
    }

    @Override
    public void onKeyLeft(int time) {
        if (mChannel == null) return;
        if (mChannel.isOnly() && mPlayers.isVod()) App.post(() -> seekTo(time), 250);
        else if (!mChannel.isOnly()) prevLine();
        mKeyDown.resetTime();
    }

    @Override
    public void onKeyRight(int time) {
        if (mChannel == null) return;
        if (mChannel.isOnly() && mPlayers.isVod()) App.post(() -> seekTo(time), 250);
        else if (!mChannel.isOnly()) nextLine(true);
        mKeyDown.resetTime();
    }

    @Override
    public void onKeyCenter() {
        hideInfo();
        showUI();
    }

    @Override
    public void onMenu() {
        showControl(mBinding.control.player);
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        else if (isVisible(mBinding.control.getRoot())) hideControl();
        else onMenu();
    }

    @Override
    public void setSubtitle(int size) {
        getExo().getSubtitleView().setFixedTextSize(Dimension.SP, size);
        getIjk().getSubtitleView().setFixedTextSize(Dimension.SP, size);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPlayers.play();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPlayers.pause();
        mClock.stop();
    }

    @Override
    public void onBackPressed() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.info)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else if (!confirm) {
            setConfirm();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPlayers.release();
        Source.get().stop();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
    }
}
