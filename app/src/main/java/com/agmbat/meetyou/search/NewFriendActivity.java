package com.agmbat.meetyou.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;

import com.agmbat.android.SysResources;
import com.agmbat.android.utils.WindowUtils;
import com.agmbat.imsdk.asmack.XMPPManager;
import com.agmbat.imsdk.asmack.roster.ContactInfo;
import com.agmbat.imsdk.imevent.ContactOnAddEvent;
import com.agmbat.imsdk.imevent.PresenceSubscribeEvent;
import com.agmbat.meetyou.R;
import com.agmbat.meetyou.tab.contacts.ContactsView;
import com.agmbat.swipemenulist.SwipeMenu;
import com.agmbat.swipemenulist.SwipeMenuCreator;
import com.agmbat.swipemenulist.SwipeMenuItem;
import com.agmbat.swipemenulist.SwipeMenuListView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

/**
 * 新的朋友申请列表
 */
public class NewFriendActivity extends Activity implements AdapterView.OnItemClickListener {

    @BindView(android.R.id.list)
    SwipeMenuListView mListView;

    private FriendAdapter mAdapter;

    public static void launch(Context context) {
        Intent intent = new Intent(context, NewFriendActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowUtils.setStatusBarColor(this, getResources().getColor(R.color.bg_status_bar));
        setContentView(R.layout.activity_new_friend);
        ButterKnife.bind(this);
        mAdapter = new FriendAdapter(this, XMPPManager.getInstance().getRosterManager().getFriendRequestList());
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);
        // step 1. create a MenuCreator
        SwipeMenuCreator creator = new SwipeMenuCreator() {

            @Override
            public void create(SwipeMenu menu) {
                SwipeMenuItem deleteItem = new SwipeMenuItem(getApplicationContext());
                deleteItem.setBackground(new ColorDrawable(Color.rgb(0xF9, 0x3F, 0x25)));
                deleteItem.setWidth((int) SysResources.dipToPixel(90));
                deleteItem.setTitle("删除");
                deleteItem.setTitleColor(Color.WHITE);
                deleteItem.setTitleSize(16);
                menu.addMenuItem(deleteItem);
            }
        };
        // set creator
        mListView.setMenuCreator(creator);
        mListView.setOnMenuItemClickListener(new SwipeMenuListView.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(int position, SwipeMenu menu, int index) {
                ContactInfo contactInfo = (ContactInfo) mListView.getItemAtPosition(position);
                switch (index) {
                    case 0:
                        XMPPManager.getInstance().getRosterManager().removeFriendRequest(contactInfo);
                        mAdapter.notifyDataSetChanged();
                        break;
                }
                return false;
            }
        });
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.push_right_in, R.anim.push_right_out);
    }

    @OnClick(R.id.title_btn_back)
    void onClickBack() {
        finish();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        ContactInfo info = (ContactInfo) parent.getItemAtPosition(position);
        String jid = info.getBareJid();
        if (XMPPManager.getInstance().getRosterManager().isFriend(jid)) {
            info = XMPPManager.getInstance().getRosterManager().getContactFromMemCache(jid);
            ViewUserHelper.openContactDetail(this, info);
        } else {
            ViewUserHelper.openVerifyDetail(this, info);
        }
    }

    /**
     * 收到申请添加自己为好友的消息
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PresenceSubscribeEvent event) {
        FriendAdapter adapter = (FriendAdapter) mListView.getAdapter();
        adapter.notifyDataSetChanged();
    }

    /**
     * 收到通过验证好友
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ContactOnAddEvent event) {
        mAdapter.notifyDataSetChanged();
    }

    private static class FriendAdapter extends ArrayAdapter<ContactInfo> {

        public FriendAdapter(@NonNull Context context, List<ContactInfo> list) {
            super(context, 0, list);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = new ContactsView(getContext());
            }
            ContactsView view = (ContactsView) convertView;
            ContactInfo contactInfo = getItem(position);
            view.update(contactInfo);
            return convertView;
        }


    }
}