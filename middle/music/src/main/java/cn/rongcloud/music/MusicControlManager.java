package cn.rongcloud.music;

import android.app.Activity;
import android.os.Environment;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.basis.ui.UIStack;
import com.basis.utils.KToast;
import com.basis.utils.Logger;
import com.basis.utils.SharedPreferUtil;
import com.basis.utils.UIKit;
import com.basis.wapper.IResultBack;
import com.basis.widget.loading.LoadTag;
import com.hfopen.sdk.entity.ChannelItem;
import com.hfopen.sdk.entity.ChannelSheet;
import com.hfopen.sdk.entity.HQListen;
import com.hfopen.sdk.entity.MusicList;
import com.hfopen.sdk.entity.MusicRecord;
import com.hfopen.sdk.entity.Record;
import com.hfopen.sdk.hInterface.DataResponse;
import com.hfopen.sdk.manager.HFOpenApi;
import com.hfopen.sdk.rx.BaseException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cn.rongcloud.corekit.api.DataCallback;
import cn.rongcloud.corekit.net.oklib.OkApi;
import cn.rongcloud.corekit.net.oklib.api.callback.FileIOCallBack;
import cn.rongcloud.corekit.utils.GsonUtil;
import cn.rongcloud.corekit.utils.ListUtil;
import cn.rongcloud.corekit.utils.VMLog;
import cn.rongcloud.musiccontrolkit.RCMusicControlEngine;
import cn.rongcloud.musiccontrolkit.bean.Effect;
import cn.rongcloud.musiccontrolkit.bean.Music;
import cn.rongcloud.musiccontrolkit.bean.MusicCategory;
import cn.rongcloud.musiccontrolkit.bean.MusicControl;
import cn.rongcloud.musiccontrolkit.iinterface.RCMusicKitListener;
import cn.rongcloud.rtc.api.IAudioEffectManager;
import cn.rongcloud.rtc.api.RCRTCAudioMixer;
import cn.rongcloud.rtc.api.RCRTCAudioMixer.MixingState;
import cn.rongcloud.rtc.api.RCRTCAudioMixer.MixingStateReason;
import cn.rongcloud.rtc.api.RCRTCAudioRouteManager;
import cn.rongcloud.rtc.api.RCRTCEngine;
import cn.rongcloud.rtc.api.callback.IRCRTCAudioRouteListener;
import cn.rongcloud.rtc.api.callback.RCRTCAudioMixingStateChangeListener;
import cn.rongcloud.rtc.audioroute.RCAudioRouteType;
import io.rong.imlib.IRongCoreCallback;
import io.rong.imlib.IRongCoreEnum;
import io.rong.imlib.RongCoreClient;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Message;
import io.rong.message.CommandMessage;

/**
 * Created by gyn on 2021/11/25
 * ?????????????????????????????????????????????????????????????????????
 * ??????????????????
 */
public class MusicControlManager extends RCRTCAudioMixingStateChangeListener implements RCMusicKitListener, IRCRTCAudioRouteListener {
    private static final String TAG = MusicControlManager.class.getSimpleName();
    private static final String KEY_MUSIC_CONTROL = "key_music_control";
    private static MusicControlManager instance;
    private static MusicControl musicControl;
    // ???????????????????????????
    private String musicPath;
    private String mRoomId;
    private LoadTag loadTag = null;
    private RCAudioRouteType routeType;

    public MusicControlManager() {
        // ???????????????cache?????????????????????????????????????????????????????????
        musicPath = UIKit.getContext().getExternalFilesDir(Environment.DIRECTORY_MUSIC).getAbsolutePath();
        musicControl = getMusicControl();
    }

    public static MusicControlManager getInstance() {
        if (instance == null) {
            instance = new MusicControlManager();
        }
        return instance;
    }

    public void showDialog(FragmentManager fragmentManager, String roomId) {
        init(roomId);
        RCMusicControlEngine.getInstance().showDialog(fragmentManager, this);
    }

    private void init(String roomId) {
        mRoomId = roomId;
        musicControl = getMusicControl();
        musicControl.setMicVolume(RCRTCEngine.getInstance().getDefaultAudioStream().getRecordingVolume());
        musicControl.setRemoteVolume(RCRTCAudioMixer.getInstance().getMixingVolume());
        musicControl.setLocalVolume(RCRTCAudioMixer.getInstance().getPlaybackVolume());
        RCRTCAudioMixer.getInstance().setAudioMixingStateChangeListener(this);
        initRouteType();
        RCRTCAudioRouteManager.getInstance().setOnAudioRouteChangedListener(this);
    }

    private void initRouteType() {
        if (RCRTCAudioRouteManager.getInstance().hasHeadSet()) {
            routeType = RCAudioRouteType.HEADSET;
        } else if (RCRTCAudioRouteManager.getInstance().hasBluetoothA2dpConnected()) {
            routeType = RCAudioRouteType.HEADSET_BLUETOOTH;
        } else {
            routeType = RCAudioRouteType.SPEAKER_PHONE;
        }
    }

    private boolean earsBackEnable() {
        return routeType == RCAudioRouteType.HEADSET || routeType == RCAudioRouteType.HEADSET_BLUETOOTH;
    }

    @Override
    public void onStateChanged(MixingState state, MixingStateReason reason) {
        VMLog.d(TAG, "onStateChanged: " + state.name() + " reason: " + reason.name());
        if (state == MixingState.STOPPED) {
            //????????????????????????????????????
            if (reason == MixingStateReason.ALL_LOOPS_COMPLETED) {
                //??????startMix?????????????????????loopCount > 0?????????loopCount???????????????????????????
                UIKit.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        // ???????????????????????????
                        RCMusicControlEngine.getInstance().playNextMusic();
                    }
                }, 0);
            } else if (reason == MixingStateReason.ONE_LOOP_COMPLETED) {
                //??????startMix?????????????????????loopCount < 0????????????????????? > 1????????????????????????????????????????????????????????????????????????
            } else if (reason == MixingStateReason.STOPPED_BY_USER) {
                //??????stopMix??????????????????
            }
        } else if (state == MixingState.PLAY) {
            //????????????????????????????????????
            if (reason == MixingStateReason.STARTED_BY_USER) {
                //??????startMix??????????????????
            } else if (reason == MixingStateReason.START_NEW_LOOP) {
                //??????startMix?????????loopCount < 0????????????????????? > 1????????????????????????????????????
            } else if (reason == MixingStateReason.RESUMED_BY_USER) {
                //??????resume????????????????????????
            }
        } else if (state == MixingState.PAUSED) {
            //???????????????reason ??? MixingStateReason.PAUSED_BY_USER
        }
    }

    @Override
    public void onReportPlayingProgress(float progress) {

    }

    /**
     * ?????????????????????
     *
     * @param dataCallback ????????????
     */
    @Override
    public void onLoadMusicList(DataCallback<List<Music>> dataCallback) {
        VMLog.d(TAG, "onLoadMusicList");
        List<Music> musicList = new ArrayList<>();

        MusicApi.loadMusics(mRoomId, MusicApi.MUSIC_TYPE_USER, new IResultBack<List<MusicBean>>() {
            @Override
            public void onResult(List<MusicBean> myMusics) {
                if (myMusics != null) {
                    Music<MusicBean> music;
                    for (MusicBean myMusic : myMusics) {
                        music = convertToMusic(myMusic);
                        musicList.add(music);
                    }
                }
                if (dataCallback != null) {
                    dataCallback.onResult(musicList);
                } else {
                    RCMusicControlEngine.getInstance().setMusicList(musicList);
                }
            }
        });
    }

    @Override
    public void onLoadMoreMusicList(DataCallback<List<Music>> dataCallback) {
        VMLog.d(TAG, "onLoadMoreMusicList");
    }

    /**
     * ??????????????????
     *
     * @param dataCallback ????????????
     */
    @Override
    public void onLoadMusicCategory(DataCallback<List<MusicCategory>> dataCallback) {
        HFOpenApi.getInstance().channel(new DataResponse<ArrayList<ChannelItem>>() {
            @Override
            public void onError(@NonNull BaseException e) {
                VMLog.e(TAG, e.getCode() + ":" + e.getMsg());
            }

            @Override
            public void onSuccess(ArrayList<ChannelItem> channelItems, @NonNull String s) {
                VMLog.e(TAG, GsonUtil.obj2Json(channelItems));
                if (channelItems != null && channelItems.size() > 0) {
                    ChannelItem channelItem = channelItems.get(0);
                    HFOpenApi.getInstance().channelSheet(channelItem.getGroupId(), 0, 0, 1, 100, new DataResponse<ChannelSheet>() {
                        @Override
                        public void onError(@NonNull BaseException e) {

                        }

                        @Override
                        public void onSuccess(ChannelSheet channelSheet, @NonNull String s) {
                            if (channelSheet != null && channelSheet.getRecord() != null) {
                                List<MusicCategory> categoryList = new ArrayList<>();
                                MusicCategory category;
                                for (Record record : channelSheet.getRecord()) {
                                    category = new MusicCategory();
                                    category.setCategoryName(record.getSheetName());
                                    category.setCategoryId(record.getSheetId() + "");
                                    categoryList.add(category);
                                }
                                dataCallback.onResult(categoryList);
                            }
                        }
                    });
                }
            }
        });
        VMLog.d(TAG, "onLoadMusicCategory");
    }

    /**
     * ????????????????????????
     *
     * @param category
     * @param dataCallback ????????????
     */
    @Override
    public void onLoadMusicListByCategory(String category, DataCallback<List<Music>> dataCallback) {
        VMLog.d(TAG, "onLoadMusicListByCategory");
        HFOpenApi.getInstance().sheetMusic(Long.parseLong(category), 0, 1, 100, new DataResponse<MusicList>() {
            @Override
            public void onError(@NonNull BaseException e) {

            }

            @Override
            public void onSuccess(MusicList musicList, @NonNull String s) {
                if (musicList != null && ListUtil.isNotEmpty(musicList.getRecord())) {
                    VMLog.e(TAG, GsonUtil.obj2Json(musicList));
                    List<Music> myMusicList = new ArrayList<>();
                    for (MusicRecord record : musicList.getRecord()) {
                        myMusicList.add(convertToMusic(record));
                    }
                    dataCallback.onResult(myMusicList);
                }
            }
        });
    }

    @Override
    public void onLoadMoreMusicListByCategory(String category, DataCallback<List<Music>> dataCallback) {
        VMLog.d(TAG, "onLoadMoreMusicListByCategory");
    }

    /**
     * ????????????
     *
     * @param dataCallback ????????????
     */
    @Override
    public void onSearchMusic(String keywords, DataCallback<List<Music>> dataCallback) {
        VMLog.d(TAG, "onSearchMusic");
        HFOpenApi.getInstance().searchMusic(null, null, null, null, null, null, null, keywords, null, null, 0, 1, 100, new DataResponse<MusicList>() {
            @Override
            public void onError(@NonNull BaseException e) {

            }

            @Override
            public void onSuccess(MusicList musicList, @NonNull String s) {
                List<Music> myMusicList = new ArrayList<>();
                if (musicList != null && ListUtil.isNotEmpty(musicList.getRecord())) {
                    VMLog.e(TAG, GsonUtil.obj2Json(musicList));
                    for (MusicRecord record : musicList.getRecord()) {
                        myMusicList.add(convertToMusic(record));
                    }
                }
                dataCallback.onResult(myMusicList);
            }
        });
    }

    /**
     * ?????????????????????
     *
     * @param dataCallback ????????????
     */
    @Override
    public void onLoadMusicControl(DataCallback<MusicControl> dataCallback) {
        dataCallback.onResult(musicControl);
        VMLog.d(TAG, "onLoadMusicControl");
    }

    @Override
    public void onLoadMusicDetail(Music music, DataCallback<Music> dataCallback) {
        VMLog.d(TAG, "onLoadMusicDetail");
        // ??????????????????????????????????????????url
        HFOpenApi.getInstance().trafficHQListen(music.getMusicId(), "mp3", "320", new DataResponse<HQListen>() {
            @Override
            public void onError(@NonNull BaseException e) {
                dataCallback.onResult(music);
            }

            @Override
            public void onSuccess(HQListen hqListen, @NonNull String s) {
                music.setSize(hqListen.getFileSize());
                music.setFileUrl(hqListen.getFileUrl());
                dataCallback.onResult(music);
            }
        });
    }

    /**
     * ?????????????????????
     *
     * @param music
     * @param dataCallback
     */
    @Override
    public void onDownloadMusic(Music music, DataCallback<Music> dataCallback) {
        VMLog.d(TAG, "onDownloadMusic:" + GsonUtil.obj2Json(music));
        // ???????????????id??????????????????????????????????????????????????????????????????????????????????????????
        String name = music.getMusicName();

        File file = new File(musicPath, name);
        // ???????????????????????????????????????
        if (file.exists()) {
            music.setPath(file.getAbsolutePath());
            musicLoadFinished(music, MusicApi.MUSIC_TYPE_HI_FIVE, dataCallback);
            return;
        }

        OkApi.download(music.getFileUrl(), null, new FileIOCallBack(musicPath, name) {
            @Override
            public void onResult(File result) {
                super.onResult(result);
                music.setPath(result.getAbsolutePath());
                musicLoadFinished(music, MusicApi.MUSIC_TYPE_HI_FIVE, dataCallback);
            }
        });
    }

    /**
     * ???????????????????????????,??????uri????????????Music
     *
     * @param music ??????music
     */
    @Override
    public void onSelectMusicFromLocal(Music music) {
        if (music == null || TextUtils.isEmpty(music.getPath())) {
            KToast.show("????????? MP3???AAC???M4A???WAV???OGG???AMR ????????????");
            return;
        }
        String realPath = music.getPath().toLowerCase(Locale.ROOT);
        if (!realPath.endsWith("mp3")
                && !realPath.endsWith("aac")
                && !realPath.endsWith("m4a")
                && !realPath.endsWith("wav")
                && !realPath.endsWith("ogg")
                && !realPath.endsWith("amr")
        ) {
            KToast.show("????????? MP3???AAC???M4A???WAV???OGG???AMR ????????????");
            return;
        }
        // ??????????????????
        showLoading("????????????");
        MusicApi.uploadMusicFile(music.getPath(), new IResultBack<String>() {
            @Override
            public void onResult(String url) {
                if (TextUtils.isEmpty(url)) {
                    hideLoading();
                    KToast.show("??????????????????");
                    return;
                }
                music.setFileUrl(url);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        UIKit.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                hideLoading();
                                musicLoadFinished(music, MusicApi.MUSIC_TYPE_LOCAL, null);
                            }
                        });
                    }
                }).start();
            }
        });

    }

    private void showLoading(String msg) {
        Activity activity = UIStack.getInstance().getTopActivity();
        if (activity != null) {
            loadTag = new LoadTag(activity, msg);
            loadTag.show();
        }
    }

    private void hideLoading() {
        if (loadTag != null) {
            loadTag.dismiss();
        }
    }

    /**
     * ??????????????????????????????????????????????????????????????????????????????
     *
     * @param music
     * @param type         1 ???????????????2 ?????????????????? 3 hifive?????????????????????1???3???
     * @param dataCallback
     */
    private void musicLoadFinished(Music music, int type, DataCallback<Music> dataCallback) {
        if (TextUtils.isEmpty(mRoomId)) {
            return;
        }
        MusicBean musicBean = new MusicBean();
        musicBean.setThirdMusicId(music.getMusicId());
        musicBean.setBackgroundUrl(music.getCoverUrl());
        musicBean.setAuthor(music.getAuthor());
        musicBean.setName(music.getMusicName());
        musicBean.setRoomId(mRoomId);
        musicBean.setType(type);
        musicBean.setUrl(music.getFileUrl());
        // ????????????kb??????????????????????????????M?????????
        musicBean.setSize(new File(music.getPath()).length() / 1024);

        MusicApi.addMusic(mRoomId, musicBean, new IResultBack<Boolean>() {
            @Override
            public void onResult(Boolean aBoolean) {
                if (aBoolean) {
                    KToast.show("????????????" + (aBoolean ? "??????" : "??????"));
                    //??????UI
                    // ?????????????????????????????????
                    music.setLoadState(Music.LoadState.LOADED);
                    if (dataCallback != null) {
                        dataCallback.onResult(music);
                    }

                    onLoadMusicList(new DataCallback<List<Music>>() {
                        @Override
                        public void onResult(List<Music> musicList) {
                            RCMusicControlEngine.getInstance().setMusicList(musicList);

                            // ???????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
                            boolean isEmpty = ListUtil.isEmpty(RCMusicControlEngine.getInstance().getMusicList());
                            Music currentMusic = music;
                            if (musicList != null) {
                                for (Music music1 : musicList) {
                                    if (TextUtils.equals(music.getMusicId(), music1.getMusicId())) {
                                        currentMusic = music1;
                                        break;
                                    }
                                }
                            }
                            // ???????????????????????????????????????
                            if (isEmpty) {
                                RCMusicControlEngine.getInstance().playMusic(currentMusic);
                            } else {
                                // ?????????????????????
                                RCMusicControlEngine.getInstance().addMusic(currentMusic);
                            }
                        }
                    });
                }
            }
        });
    }

    @Override
    public void onLoadEffectList(DataCallback<List<Effect>> dataCallback) {
        VMLog.d(TAG, "onLoadEffectList");
        List<Effect> effectList = MusicEffectManager.getInstance().getEffectList();
        dataCallback.onResult(effectList);
        // ???????????????????????? RCRTCEngine
        if (RCRTCEngine.getInstance().getAudioEffectManager() == null) {
            return;
        }
        for (Effect effect : effectList) {
            RCRTCEngine.getInstance().getAudioEffectManager().preloadEffect(effect.getFilePath(), Integer.parseInt(effect.getSoundId()), new IAudioEffectManager.ILoadingStateCallback() {
                @Override
                public void complete(int error) {
                    if (error == -1) {
                        VMLog.e(TAG, "????????????????????????");
                    }
                }
            });
        }
    }

    /**
     * ????????????
     *
     * @param fromMusic
     * @param downToMusic
     */
    @Override
    public void onTopMusic(Music fromMusic, Music downToMusic) {
        VMLog.d(TAG, "onTopMusic");
        if (TextUtils.isEmpty(mRoomId)) {
            return;
        }
        if (fromMusic.getExtra() == null || downToMusic.getExtra() == null) {
            return;
        }
        MusicApi.moveMusic(mRoomId, ((MusicBean) (fromMusic.getExtra())).getId(), ((MusicBean) (downToMusic.getExtra())).getId(), new IResultBack<Boolean>() {
            @Override
            public void onResult(Boolean aBoolean) {
                KToast.show("??????" + (aBoolean ? "??????" : "??????"));
                if (!aBoolean) {
                    // ?????????????????????????????????
                    onLoadMusicList(null);
                }
            }
        });
    }

    /**
     * ????????????
     *
     * @param music
     */
    @Override
    public void onDeleteMusic(Music music) {
        VMLog.d(TAG, "onDeleteMusic");
        if (TextUtils.isEmpty(mRoomId)) {
            return;
        }
        if (music.getExtra() == null) {
            return;
        }
        MusicApi.deleteMusic(mRoomId, ((MusicBean) (music.getExtra())).getId(), new IResultBack<Boolean>() {
            @Override
            public void onResult(Boolean aBoolean) {
                KToast.show("????????????" + (aBoolean ? "??????" : "??????"));
                if (!aBoolean) {
                    // ?????????????????????????????????
                    onLoadMusicList(null);
                }
            }
        });
    }

    private void saveMusicControlToCache() {
        VMLog.e(TAG, "saveMusicControlToCache");
        saveMusicControl(musicControl);
    }

    @Override
    public void onLocalVolumeChanged(int localVolume) {
        VMLog.d(TAG, "onLocalVolumeChanged");
        RCRTCAudioMixer.getInstance().setPlaybackVolume(localVolume);
        musicControl.setLocalVolume(localVolume);
        saveMusicControlToCache();
    }

    @Override
    public void onRemoteVolumeChanged(int remoteVolume) {
        VMLog.d(TAG, "onRemoteVolumeChanged");
        RCRTCAudioMixer.getInstance().setMixingVolume(remoteVolume);
        musicControl.setRemoteVolume(remoteVolume);
        saveMusicControlToCache();
    }

    @Override
    public void onMicVolumeChanged(int micVolume) {
        VMLog.d(TAG, "onMicVolumeChanged");
        RCRTCEngine.getInstance().getDefaultAudioStream().adjustRecordingVolume(micVolume);
        musicControl.setMicVolume(micVolume);
        saveMusicControlToCache();
    }

    @Override
    public void onEarsBackEnableChanged(boolean earsBackEnable) {
        VMLog.d(TAG, "onEarsBackEnableChanged");
        RCRTCEngine.getInstance().getDefaultAudioStream().enableEarMonitoring(earsBackEnable);
        musicControl.setEarsBackEnable(earsBackEnable);
        saveMusicControlToCache();
    }

    @Override
    public void onStartMixingWithMusic(Music music) {
        VMLog.d(TAG, "onStartMixingWithMusic:" + GsonUtil.obj2Json(music));
        RCRTCAudioMixer.getInstance().stop();
        // ???????????????????????????????????????
        if (music == null || TextUtils.isEmpty(music.getPath()) || !new File(music.getPath()).exists()) {
            if (music.getExtra() instanceof MusicBean) {
                showLoading("??????????????????");
                MusicBean musicBean = (MusicBean) music.getExtra();
                int type = musicBean.getType();
                if (type == MusicApi.MUSIC_TYPE_HI_FIVE) {
                    // hifive??????????????????????????????????????????????????????
                    onLoadMusicDetail(music, new DataCallback<Music>() {
                        @Override
                        public void onResult(Music m) {
                            if (!TextUtils.isEmpty(m.getFileUrl())) {
                                music.setFileUrl(m.getFileUrl());
                                downloadMusicAndPlay(music);
                            } else {
                                hideLoading();
                            }
                        }
                    });
                } else {
                    downloadMusicAndPlay(music);
                }
            }
        } else {
            mixingWithMusic(music);
        }
    }

    /**
     * ??????????????????????????????????????????
     *
     * @param music
     */
    private void downloadMusicAndPlay(Music music) {
        String name = music.getMusicName();
        OkApi.download(music.getFileUrl(), null, new FileIOCallBack(musicPath, name) {
            @Override
            public void onResult(File result) {
                super.onResult(result);
                mixingWithMusic(music);
                hideLoading();
            }

            @Override
            public void onError(int code, String msg) {
                super.onError(code, msg);
                hideLoading();
            }
        });
    }

    /**
     * ????????????
     *
     * @param music
     */
    private void mixingWithMusic(Music music) {
        RCRTCAudioMixer.getInstance()
                .startMix(music.getPath(), RCRTCAudioMixer.Mode.MIX, true, 1);
        //???????????????????????????
//        RCRTCAudioMixer.getInstance().seekTo(0.9f);
        if (!TextUtils.isEmpty(mRoomId) && music.getExtra() instanceof MusicBean) {
            int id = ((MusicBean) music.getExtra()).getId();
            sendPlayOrStopMusicMessage(id);
        }
    }

    @Override
    public void onResumeMixingWithMusic(Music music) {
        VMLog.d(TAG, "onResumeMixingWithMusic");
        RCRTCAudioMixer.getInstance().resume();
        if (!TextUtils.isEmpty(mRoomId) && music.getExtra() instanceof MusicBean) {
            int id = ((MusicBean) music.getExtra()).getId();
            sendPlayOrStopMusicMessage(id);
        }
    }

    @Override
    public void onPauseMixingWithMusic(Music music) {
        VMLog.d(TAG, "onPauseMixingWithMusic");
        RCRTCAudioMixer.getInstance().pause();
        if (!TextUtils.isEmpty(mRoomId) && music.getExtra() instanceof MusicBean) {
            sendPlayOrStopMusicMessage(null);
        }
    }

    @Override
    public void onStopMixingWithMusic() {
        RCRTCAudioMixer.getInstance().stop();
        if (!TextUtils.isEmpty(mRoomId)) {
            sendPlayOrStopMusicMessage(null);
        }
    }

    private void sendPlayOrStopMusicMessage(Integer musicId) {
        if (TextUtils.isEmpty(mRoomId)) {
            return;
        }

        MusicApi.playOrPauseMusic(mRoomId, musicId, aBoolean -> {
            if (aBoolean) {
                CommandMessage message = CommandMessage.obtain("RCVoiceSyncMusicInfoKey", musicId == null ? "" : (musicId + ""));
                RongCoreClient.getInstance().sendMessage(Conversation.ConversationType.CHATROOM, mRoomId, message, "", "", new IRongCoreCallback.ISendMessageCallback() {
                    @Override
                    public void onAttached(Message message) {
                        VMLog.d(TAG, "onAttached:");
                    }

                    @Override
                    public void onSuccess(Message message) {

                    }

                    @Override
                    public void onError(Message message, IRongCoreEnum.CoreErrorCode coreErrorCode) {
                        Logger.e("=============" + coreErrorCode.code + ":" + coreErrorCode.msg);
                    }
                });
            }
        });
    }

    @Override
    public void onPlayEffect(Effect effect) {
        VMLog.d(TAG, "onPlayEffect");
        if (RCRTCEngine.getInstance().getAudioEffectManager() == null) {
            return;
        }
        RCRTCEngine.getInstance().getAudioEffectManager().stopAllEffects();
        RCRTCEngine.getInstance().getAudioEffectManager().playEffect(Integer.parseInt(effect.getSoundId()), 1, 50);
    }

    @Override
    public boolean isEarsBackEnable() {
        boolean enable = earsBackEnable();
        if (!enable) {
            KToast.show("???????????????");
        }
        return enable;
    }

    /**
     * ????????????
     *
     * @param record
     * @return
     */
    private Music convertToMusic(MusicRecord record) {
        Music music = new Music();
        music.setMusicName(record.getMusicName());
        music.setMusicId(record.getMusicId());
        music.setAlbumName(record.getAlbumName());
        if (ListUtil.isNotEmpty(record.getCover())) {
            music.setCoverUrl(record.getCover().get(0).getUrl());
        }
        if (ListUtil.isNotEmpty(record.getArtist())) {
            music.setAuthor(record.getArtist().get(0).getName());
        } else if (ListUtil.isNotEmpty(record.getAuthor())) {
            music.setAuthor(record.getAuthor().get(0).getName());
        } else if (ListUtil.isNotEmpty(record.getComposer())) {
            music.setAuthor(record.getComposer().get(0).getName());
        } else if (ListUtil.isNotEmpty(record.getArranger())) {
            music.setAuthor(record.getArranger().get(0).getName());
        }
        return music;
    }

    /**
     * @param musicBean
     * @return
     */
    private Music<MusicBean> convertToMusic(MusicBean musicBean) {
        Music<MusicBean> music = new Music<>();
        music.setMusicName(musicBean.getName());
        music.setMusicId(musicBean.getThirdMusicId());
        music.setSize((long) (musicBean.getSize() * 1024 * 1024));
        music.setAuthor(musicBean.getAuthor());
        music.setCoverUrl(musicBean.getBackgroundUrl());
        music.setExtra(musicBean);
        music.setPath(musicPath + File.separator + musicBean.getName());
        music.setFileUrl(musicBean.getUrl());
        return music;
    }

    /**
     * ??????????????????
     *
     * @param musicControl
     */
    private void saveMusicControl(MusicControl musicControl) {
        SharedPreferUtil.set(KEY_MUSIC_CONTROL, GsonUtil.obj2Json(musicControl));
    }

    /**
     * ????????????
     *
     * @return
     */
    private MusicControl getMusicControl() {
        String json = SharedPreferUtil.get(KEY_MUSIC_CONTROL);
        if (!TextUtils.isEmpty(json)) {
            return GsonUtil.json2Obj(json, MusicControl.class);
        }
        return new MusicControl();
    }

    /**
     * ??????????????????
     */
    public void release() {
        stopPlayMusic();
        RCRTCAudioMixer.getInstance().setAudioMixingStateChangeListener(null);
        RCMusicControlEngine.getInstance().release();
        saveMusicControl(null);
        if (RCRTCEngine.getInstance().getAudioEffectManager() != null) {
            RCRTCEngine.getInstance().getAudioEffectManager().stopAllEffects();
            RCRTCEngine.getInstance().getAudioEffectManager().unloadAllEffects();
        }
        RCRTCAudioRouteManager.getInstance().setOnAudioRouteChangedListener(null);
    }

    /**
     * ????????????
     */
    public void stopPlayMusic() {
        RCMusicControlEngine.getInstance().stopMusic();
        RCRTCAudioMixer.getInstance().stop();
    }

    public boolean isPlaying() {
        return RCMusicControlEngine.getInstance().isPlaying();
    }

    @Override
    public void onRouteChanged(RCAudioRouteType type) {
        routeType = type;
        boolean enable = earsBackEnable();
        RCMusicControlEngine.getInstance().setEarsBackEnable(enable);
        if (!enable) {
            onEarsBackEnableChanged(false);
        }
    }

    @Override
    public void onRouteSwitchFailed(RCAudioRouteType fromType, RCAudioRouteType toType) {

    }
}
