package com.github.lzyzsd.rxexamples;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import com.f2prateek.rx.preferences.Preference;
import com.f2prateek.rx.preferences.RxSharedPreferences;
import com.github.lzyzsd.rxexamples.operators.TransformDemo;
import com.jakewharton.rxbinding.view.RxView;
import com.jakewharton.rxbinding.widget.RxCompoundButton;

import java.util.concurrent.TimeUnit;

import butterknife.ButterKnife;
import butterknife.OnClick;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

public class MainActivity extends Activity {

    @OnClick(R.id.btn_run_concat)
    public void onRunConcatClick(View view) {
        testConcat();
    }

    @OnClick(R.id.btn_run_merge)
    public void onRunMergeClick(View view) {
        testMerge();
    }

    RxSharedPreferences rxPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        rxPreferences = RxSharedPreferences.create(preferences);

        ButterKnife.bind(this);

        testThrottle();

        testCompoundButtonWithPreference();

        Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                printThread(0);
                subscriber.onNext("ccc");
                subscriber.onCompleted();
            }
        }).subscribeOn(Schedulers.newThread())
        .doOnNext(s -> printThread(1))
        .observeOn(Schedulers.io())
        .observeOn(Schedulers.newThread())
        .doOnNext(s -> {
            printThread(2);
        })
        .subscribeOn(Schedulers.io())
        .doOnNext(s -> {
            printThread(4);
        })
        .observeOn(Schedulers.io())
        .subscribe(s -> {
            printThread(3);
        });
    }

    private void printThread(int i) {
        System.out.println("------"+i+"----"+Thread.currentThread().getId()+Thread.currentThread().getName());
    }

    private void testCompoundButtonWithPreference() {
        Preference<Boolean> checked = rxPreferences.getBoolean("checked", true);

        CheckBox checkBox = (CheckBox) findViewById(R.id.cb_test);
        RxCompoundButton.checkedChanges(checkBox)
                .subscribe(checked.asAction());

        checked.asObservable().subscribe(aBoolean -> {
            System.out.println("----------------checked: " + aBoolean);
        });
    }

    //拼接两个Observable的输出，保证顺序，按照Observable在concat中的顺序，依次将每个Observable产生的事件传递给订阅者
    //只有当前面的Observable结束了，才会执行后面的
    private void testConcat() {
        Observable<String> observable1 = DemoUtils.createObservable1().subscribeOn(Schedulers.newThread());
        Observable<String> observable2 = DemoUtils.createObservable2().subscribeOn(Schedulers.newThread());

        Observable.concat(observable1, observable2)
                .subscribeOn(Schedulers.newThread())
                .subscribe(System.out::println);
    }

    //拼接两个Observable的输出，不保证顺序，按照事件产生的顺序发送给订阅者
    private void testMerge() {
        Observable<String> observable1 = DemoUtils.createObservable1().subscribeOn(Schedulers.newThread());
        Observable<String> observable2 = DemoUtils.createObservable2().subscribeOn(Schedulers.newThread());

        Observable.merge(observable1, observable2)
                .subscribeOn(Schedulers.newThread())
                .subscribe(System.out::println);
    }

    /**
     * 嵌套有依赖
     * 需要登陆之后，根据拿到的token去获取消息列表
     */
    @OnClick(R.id.btn_login)
    public void testNestedDependency(View view) {
        NetworkService.getToken("username", "password")
                .flatMap(s -> NetworkService.getMessage(s))
                .subscribe(s -> {
                    System.out.println("message: " + s);
                });
    }

    /**
     * 一秒内连续点击button，只会响应一次
     */
    private void testThrottle() {
        RxView.clicks(findViewById(R.id.btn_throttle))
                .throttleFirst(1, TimeUnit.SECONDS)
                .subscribe(aVoid -> {
                    System.out.println("click");
                });
    }

    private String memoryCache = null;

    /**
     *取数据，首先检查内存是否有缓存
     *然后检查文件缓存中是否有
     *最后才从网络中取
     *前面任何一个条件满足，就不会执行后面的
     */
    @OnClick(R.id.btn_fetch_data)
    public void testCache(View view) {
        final Observable<String> memory = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                if (memoryCache != null) {
                    subscriber.onNext(memoryCache);
                } else {
                    subscriber.onCompleted();
                }
            }
        });
        Observable<String> disk = Observable.create(new Observable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                String cachePref = rxPreferences.getString("cache").get();
                if (!TextUtils.isEmpty(cachePref)) {
                    subscriber.onNext(cachePref);
                } else {
                    subscriber.onCompleted();
                }
            }
        });

        Observable<String> network = Observable.just("network");

        Observable.concat(memory, disk, network)
        .first()
        .subscribeOn(Schedulers.newThread())
        .subscribe(s -> {
            memoryCache = "memory";
            System.out.println("--------------subscribe: " + s);
        });
    }

    @OnClick(R.id.btn_test_tranform)
    public void testTransform() {
        TransformDemo.complexTransform();
    }
}
