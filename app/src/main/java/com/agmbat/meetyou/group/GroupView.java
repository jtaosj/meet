package com.agmbat.meetyou.group;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.agmbat.android.image.ImageManager;
import com.agmbat.imsdk.search.group.GroupInfo;
import com.agmbat.meetyou.R;
import com.agmbat.meetyou.helper.AvatarHelper;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * 群itemview
 */
public class GroupView extends LinearLayout {

    /**
     * 群头像
     */
    @BindView(R.id.avatar)
    ImageView mAvatarView;

    /**
     * 群名称
     */
    @BindView(R.id.nickname)
    TextView mNickNameView;

    /**
     * 群描述
     */
    @BindView(R.id.description)
    TextView mDescriptionView;

    public GroupView(Context context) {
        super(context);
        View.inflate(context, R.layout.im_search_group_item, this);
        ButterKnife.bind(this, this);
    }

    public void update(GroupInfo groupInfo) {
        mNickNameView.setText(groupInfo.name);
        mDescriptionView.setText(groupInfo.description);
        String uri = groupInfo.cover;
        ImageManager.displayImage(uri, mAvatarView, AvatarHelper.getGroupOptions());
    }

}
