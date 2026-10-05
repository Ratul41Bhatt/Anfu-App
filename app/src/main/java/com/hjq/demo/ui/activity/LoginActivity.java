package com.hjq.demo.ui.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.aop.Log;
import com.hjq.demo.aop.SingleClick;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.manager.InputTextManager;
import com.hjq.demo.other.KeyboardWatcher;
import com.hjq.demo.utils.LogUtils;
import com.hjq.language.MultiLanguages;
import com.hjq.widget.view.SubmitButton;
import com.szanfu.sdk.api.ISdkServiceManager;

import java.util.Locale;

/**
 *    author : Android 轮子哥
 *    github : https://github.com/getActivity/AndroidProject
 *    time   : 2018/10/18
 *    desc   : 登录界面
 */
public final class LoginActivity extends AppActivity
        implements
        KeyboardWatcher.SoftKeyboardStateListener,
        TextView.OnEditorActionListener, RadioGroup.OnCheckedChangeListener{
    private static final String TAG = "LoginActivity";
    private static final String INTENT_KEY_IN_PHONE = "phone";
    private static final String INTENT_KEY_IN_PASSWORD = "password";

    @Log
    public static void start(Context context, String phone, String password) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.putExtra(INTENT_KEY_IN_PHONE,"01");
        intent.putExtra(INTENT_KEY_IN_PASSWORD, "0000");
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private ImageView mLogoView;

    private ViewGroup mBodyLayout;
    private EditText mPhoneView;
    private EditText mPasswordView;

    private View mForgetView;
    private SubmitButton mCommitView;

    private View mOtherView;
    //private View mQQView;
    //private View mWeChatView;

    /** logo 缩放比例 */
    private final float mLogoScale = 0.8f;
    /** 动画时间 */
    private final int mAnimTime = 100;

    @Override
    protected int getLayoutId() {
        return R.layout.login_activity;
    }

    @Override
    protected void initView() {
        mLogoView = findViewById(R.id.iv_login_logo);
        mBodyLayout = findViewById(R.id.ll_login_body);
        mPhoneView = findViewById(R.id.et_login_phone);
        mPasswordView = findViewById(R.id.et_login_password);
        mForgetView = findViewById(R.id.tv_login_forget);
        mCommitView = findViewById(R.id.btn_login_commit);
        mOtherView = findViewById(R.id.ll_login_other);
        //mQQView = findViewById(R.id.iv_login_qq);
        //mWeChatView = findViewById(R.id.iv_login_wechat);
        setOnClickListener(mCommitView);
        //setOnClickListener(mForgetView, mCommitView, mQQView, mWeChatView);
        RadioGroup radioGroup = findViewById(R.id.rg_main_languages);
        Locale locale = MultiLanguages.getAppLanguage();
        LogUtils.d("locale:"+locale);
        if (Locale.CHINA.equals(locale)) {
            radioGroup.check(R.id.rb_main_language_cn);
        }else if (Locale.ENGLISH.equals(locale)) {
            radioGroup.check(R.id.rb_main_language_en);
        } else {
            LogUtils.d("默认设置中文");
            //radioGroup.check(R.id.rb_main_language_cn);
        }
        radioGroup.setOnCheckedChangeListener(this);
        mPasswordView.setOnEditorActionListener(this);
        InputTextManager.with(this)
                .addView(mPhoneView)
                .addView(mPasswordView)
                .setMain(mCommitView)
                .build();
    }

    @Override
    protected void initData() {
        postDelayed(() -> {
            KeyboardWatcher.with(LoginActivity.this)
                    .setListener(LoginActivity.this);
        }, 200);

        // 判断用户当前有没有安装 QQ
       /* if (!UmengClient.isAppInstalled(this, Platform.QQ)) {
            mQQView.setVisibility(View.GONE);
        }*/

        // 判断用户当前有没有安装微信
       /* if (!UmengClient.isAppInstalled(this, Platform.WECHAT)) {
            mWeChatView.setVisibility(View.GONE);
        }*/

        // 如果这两个都没有安装就隐藏提示
        /*if (mQQView.getVisibility() == View.GONE && mWeChatView.getVisibility() == View.GONE) {
            mOtherView.setVisibility(View.GONE);
        }*/

        // 自动填充手机号和密码
        mPhoneView.setText("01");
        mPasswordView.setText("0000");
    }

    @Override
    public void onRightClick(View view) {
        // 跳转到注册界面
      /*  RegisterActivity.start(this, mPhoneView.getText().toString(),
                mPasswordView.getText().toString(), (phone, password) -> {
            // 如果已经注册成功，就执行登录操作
            mPhoneView.setText(phone);
            mPasswordView.setText(password);
            mPasswordView.requestFocus();
            mPasswordView.setSelection(mPasswordView.getText().length());
            onClick(mCommitView);
        });*/
    }

    @SingleClick
    @Override
    public void onClick(View view) {
        if (view == mForgetView) {
            startActivity(PasswordForgetActivity.class);
            return;
        }

        if (view == mCommitView) {
            if (mPhoneView.getText().toString().length() != 2) {
                mPhoneView.startAnimation(AnimationUtils.loadAnimation(getContext(), R.anim.shake_anim));
                mCommitView.showError(3000);
                toast(R.string.common_phone_input_error);
                return;
            }

            // 隐藏软键盘
            hideKeyboard(getCurrentFocus());

            if (true) {
                mCommitView.showProgress();
                postDelayed(() -> {
                    mCommitView.showSucceed();
                    postDelayed(() -> {
                        //HomeActivity.start(getContext(), MineFragment.class);
                        startActivity(PayActivity.class);
                        finish();
                    }, 500);
                }, 500);
                return;
            }

           /* EasyHttp.post(this)
                    .api(new LoginApi()
                            .setPhone(mPhoneView.getText().toString())
                            .setPassword(mPasswordView.getText().toString()))
                    .request(new HttpCallback<HttpData<LoginApi.Bean>>(this) {

                        @Override
                        public void onStart(Call call) {
                            mCommitView.showProgress();
                        }

                        @Override
                        public void onEnd(Call call) {}

                        @Override
                        public void onSucceed(HttpData<LoginApi.Bean> data) {
                            // 更新 Token
                            EasyConfig.getInstance()
                                    .addParam("token", data.getData().getToken());
                            postDelayed(() -> {
                                mCommitView.showSucceed();
                                postDelayed(() -> {
                                    // 跳转到首页
                                    //HomeActivity.start(getContext(), MineFragment.class);
                                    startActivity(PayActivity.class);
                                    finish();
                                }, 1000);
                            }, 1000);
                        }

                        @Override
                        public void onFail(Exception e) {
                            super.onFail(e);
                            postDelayed(() -> {
                                mCommitView.showError(3000);
                            }, 1000);
                        }
                    });
            return;*/
        }

       /* if (view == mQQView || view == mWeChatView) {
            toast("记得改好第三方 AppID 和 Secret，否则会调不起来哦");
            Platform platform;
            if (view == mQQView) {
                platform = Platform.QQ;
            } else if (view == mWeChatView) {
                platform = Platform.WECHAT;
                toast("也别忘了改微信 " + WXEntryActivity.class.getSimpleName() + " 类所在的包名哦");
            } else {
                throw new IllegalStateException("are you ok?");
            }
            UmengClient.login(this, platform, this);
        }*/
    }

    /**
     * {@link RadioGroup.OnCheckedChangeListener}
     */
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        // 是否需要重启
        boolean restart = false;

        if (checkedId == R.id.rb_main_language_cn) {
            // 简体中文
            restart = MultiLanguages.setAppLanguage(this, Locale.CHINA);
            LogUtils.i("设置中文");
        } else if (checkedId == R.id.rb_main_language_en) {
            // 英语
            restart = MultiLanguages.setAppLanguage(this, Locale.ENGLISH);
            LogUtils.i("设置英文");
        }

        LogUtils.i("是否需要重启：\t "+restart);

        if (restart) {
            // 1.使用recreate来重启Activity的效果差，有闪屏的缺陷
            // recreate();

            // 2.使用常规startActivity来重启Activity，有从左向右的切换动画，稍微比recreate的效果好一点，但是这种并不是最佳的效果
            // startActivity(new Intent(this, LanguageActivity.class));
            // finish();

            // 3.我们可以充分运用 Activity 跳转动画，在跳转的时候设置一个渐变的效果，相比前面的两种带来的体验是最佳的
            startActivity(new Intent(this, SplashActivity.class));
            overridePendingTransition(R.anim.activity_alpha_in, R.anim.activity_alpha_out);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 友盟回调
       // UmengClient.onActivityResult(this, requestCode, resultCode, data);
    }


    /**
     * {@link KeyboardWatcher.SoftKeyboardStateListener}
     */

    @Override
    public void onSoftKeyboardOpened(int keyboardHeight) {
        // 执行位移动画
      /*  ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mBodyLayout, "translationY", 0, -mCommitView.getHeight());
        objectAnimator.setDuration(mAnimTime);
        objectAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        objectAnimator.start();*/

        // 执行缩小动画
       /* mLogoView.setPivotX(mLogoView.getWidth() / 2f);
        mLogoView.setPivotY(mLogoView.getHeight());
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mLogoView, "scaleX", 1f, mLogoScale);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mLogoView, "scaleY", 1f, mLogoScale);
        ObjectAnimator translationY = ObjectAnimator.ofFloat(mLogoView, "translationY", 0f, -mCommitView.getHeight());
        animatorSet.play(translationY).with(scaleX).with(scaleY);
        animatorSet.setDuration(mAnimTime);
        animatorSet.start();*/
    }

    @Override
    public void onSoftKeyboardClosed() {
        // 执行位移动画
       /* ObjectAnimator objectAnimator = ObjectAnimator.ofFloat(mBodyLayout, "translationY", mBodyLayout.getTranslationY(), 0f);
        objectAnimator.setDuration(mAnimTime);
        objectAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        objectAnimator.start();

        if (mLogoView.getTranslationY() == 0) {
            return;
        }

        // 执行放大动画
        mLogoView.setPivotX(mLogoView.getWidth() / 2f);
        mLogoView.setPivotY(mLogoView.getHeight());
        AnimatorSet animatorSet = new AnimatorSet();
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(mLogoView, "scaleX", mLogoScale, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(mLogoView, "scaleY", mLogoScale, 1f);
        ObjectAnimator translationY = ObjectAnimator.ofFloat(mLogoView, "translationY", mLogoView.getTranslationY(), 0f);
        animatorSet.play(translationY).with(scaleX).with(scaleY);
        animatorSet.setDuration(mAnimTime);
        animatorSet.start();*/
    }

    /**
     * {@link TextView.OnEditorActionListener}
     */
    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE && mCommitView.isEnabled()) {
            // 模拟点击登录按钮
            onClick(mCommitView);
            return true;
        }
        return false;
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                .hideBar(com.gyf.immersionbar.BarHide.FLAG_HIDE_NAVIGATION_BAR);
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {

    }
}