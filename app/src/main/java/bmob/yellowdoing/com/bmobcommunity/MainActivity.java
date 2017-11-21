package bmob.yellowdoing.com.bmobcommunity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.droi.sdk.DroiCallback;
import com.droi.sdk.DroiError;
import com.droi.sdk.core.Core;
import com.droi.sdk.core.DroiFile;
import com.droi.sdk.core.DroiObject;
import com.droi.sdk.core.DroiPermission;
import com.droi.sdk.core.DroiQuery;
import com.droi.sdk.core.DroiUser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import hg.yellowdoing.communityui.Community;
import hg.yellowdoing.communityui.CommunityFragment;
import hg.yellowdoing.communityui.CommunityInterface;
import hg.yellowdoing.communityui.PostActivity;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;


public class MainActivity extends AppCompatActivity implements CommunityInterface {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //初始化Droibaas SDK
        DroiObject.registerCustomClass(User.class);
        DroiObject.registerCustomClass(MyCommunity.class);
        Core.initialize(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        getSupportFragmentManager().beginTransaction()
                .add(R.id.container, new CommunityFragment().setCommunityInterface(this))
                .commit();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getSharedPreferences("user", MODE_PRIVATE).getBoolean("isLogin", false)) {
                    User user = DroiUser.getCurrentUser(User.class);
                    if (user.getAvatar() == null || user.getNickName() == null)
                        startActivity(new Intent(MainActivity.this, UserInfoActivity.class));
                    else
                        startActivity(new Intent(MainActivity.this, PostActivity.class));
                } else
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            DroiUser.getCurrentUser().logout();
            getSharedPreferences("user", MODE_PRIVATE).edit().putBoolean("isLogin", false).apply();
            startActivity(new Intent(this, LoginActivity.class));
            return true;
        } else if (id == R.id.user_info) {
            if (DroiUser.getCurrentUser(User.class) != null)
                startActivity(new Intent(this, UserInfoActivity.class));
            else
                startActivity(new Intent(this, LoginActivity.class));
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void loadCommunityList(CommunitySubsriber subsriber, int page) {
        DroiQuery query = DroiQuery.Builder.newBuilder()
                .query(MyCommunity.class)
                .limit(20)
                .offset((page - 1) * 20)
                .orderBy("_CreationTime", false)
                .build();
        DroiError error = new DroiError();
        List<MyCommunity> result = query.runQuery(error);
        List<Community> communities = new ArrayList<>();
        if (error.isOk()) {
            for (int i = 0; i < result.size(); i++) {
                MyCommunity myCommunity = result.get(i);
                communities.add(new Community()
                        .setId(myCommunity.getObjectId())
                        .setContent(myCommunity.getContent())
                        .setReplyNum(myCommunity.getReplyNum())
                        .setLike(DroiUser.getCurrentUser(User.class) != null && myCommunity.getLikePersons().contains(DroiUser.getCurrentUser(User.class).getObjectId()))
                        .setImagePaths(myCommunity.getImagePaths())
                        .setLikeNum(myCommunity.getLikePersons().size())
                        .setNickName(myCommunity.getAuthor().getNickName())
                        .setCreateTime(dateCompare(myCommunity.getCreationTime().getTime()))
                        .setAvatar(myCommunity.getAuthor().getAvatar().getUri().toString().replaceAll("\\\\", "")));
            }
            subsriber.onComplete(communities);
        }
    }

    @Override
    public void reply(Subsriber subsriber, String communityId, String content) {

    }

    @Override
    public void loadComments(CommentSubsriber subsriber, String communityId, int page) {

    }

    @Override
    public void like(Subsriber subsriber, final String communityId) {
        likeOrUnlike(subsriber, communityId, "Add");

    }

    @Override
    public void unLike(Subsriber subsriber, final String communityId) {
        likeOrUnlike(subsriber, communityId, "Remove");
    }

    private void likeOrUnlike(final Subsriber subsriber, final String communityId, final String action) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                OkHttpClient okHttpClient = new OkHttpClient();

                RequestBody body = new RequestBody() {
                    @Override
                    public MediaType contentType() {
                        return MediaType.parse("application/json");
                    }

                    @Override
                    public void writeTo(BufferedSink sink) throws IOException {
                        sink.write(("{\"likePersons\" :{\"__op\":\"" + action + "\",\"objects\":[\"" + DroiUser.getCurrentUser(User.class).getObjectId() + "\"]}}").getBytes());
                    }
                };
                Request request = new Request.Builder()
                        .url("https://api.droibaas.com/rest/objects/v2/Community/" + communityId)
                        .addHeader("X-Droi-AppID", "3gltmbzh_tAPpNFDH-LvwZAA5ngH31dHlQBkjYMm")
                        .addHeader("X-Droi-Api-Key", "ZtpNEPYAg5t8mqzZdyfz3UK9d26YBEqMzCYPji8SUMfWBqHqFthWVdiQrQtGtLvL")
                        .addHeader("Content-Type", "application/json").patch(body).build();

                okHttpClient.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, final IOException e) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onResponse(Call call, final Response response) throws IOException {
                        final String resp = response.body().string();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    JSONObject jsonObject = new JSONObject(resp);
                                    if (jsonObject.getInt("Code") == 0) subsriber.onComplete();
                                    else
                                        Toast.makeText(MainActivity.this, response.message(), Toast.LENGTH_SHORT).show();
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }
                });
            }
        }).start();
    }

    @Override
    public void post(final Subsriber subsriber, final ArrayList<String> imagePaths, String content) {
        final MyCommunity myCommunity = new MyCommunity();
        DroiPermission permission = new DroiPermission();
        permission.setPublicReadPermission(true);
        permission.setPublicWritePermission(true);
        myCommunity.setPermission(permission);
        myCommunity.setContent(content);
        myCommunity.setAuthor(User.getCurrentUser(User.class));
        myCommunity.setReplyNum(0);
        myCommunity.setLikePersons(new ArrayList<String>());
        final ArrayList<String> list = new ArrayList<>();
        if (imagePaths.size() == 0)
            saveCommunity(subsriber, myCommunity);
        else
            for (int i = 0; i < imagePaths.size(); i++) {
                final DroiFile file = new DroiFile(UserInfoActivity.Bitmap2Bytes(UserInfoActivity.decodeSampledBitmapFromPath(imagePaths.get(i), 120, 120)));
                file.saveInBackground(new DroiCallback<Boolean>() {
                    @Override
                    public void result(Boolean aBoolean, DroiError droiError) {
                        if (aBoolean) {
                            list.add(file.getUri().toString().replaceAll("\\\\", ""));
                            if (list.size() == imagePaths.size()) {
                                myCommunity.setImagePaths(list);
                                saveCommunity(subsriber, myCommunity);
                            }
                        } else
                            Toast.makeText(MainActivity.this, droiError.getTicket(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
    }

    private void saveCommunity(final Subsriber subsriber, MyCommunity myCommunity) {
        DroiError error = myCommunity.save();
        if (error.isOk()) {
            subsriber.onComplete();
            Toast.makeText(MainActivity.this, "发帖成功", Toast.LENGTH_SHORT).show();
        } else
            Toast.makeText(MainActivity.this, error.getAppendedMessage(), Toast.LENGTH_SHORT).show();

    }

    private String dateCompare(long createTime) {
        Date now = new Date();
        long day = (now.getTime() - createTime) / (86400000);
        if (day == 0) {
            long time = (now.getTime() - createTime) / 1000;
            if (time < 60)
                return "刚刚";
            if (60 <= time && time < 3600)
                return (time / 60) + "分钟前";
            else
                return (time / 3600) + "小时前";
        } else if (day == 1)
            return "昨天";
        else if (day > 1 && day < 31)
            return day + "天前";
        else if (day >= 31 && day < 365)
            return (day / 30) + "个月前";
        else
            return (day / 365) + "年前";

    }
}
