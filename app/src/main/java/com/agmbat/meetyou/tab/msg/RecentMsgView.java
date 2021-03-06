package com.agmbat.meetyou.tab.msg;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.agmbat.android.image.ImageManager;
import com.agmbat.imsdk.asmack.MessageManager;
import com.agmbat.imsdk.asmack.XMPPManager;
import com.agmbat.imsdk.asmack.roster.ContactInfo;
import com.agmbat.imsdk.chat.body.AudioBody;
import com.agmbat.imsdk.chat.body.Body;
import com.agmbat.imsdk.chat.body.BodyParser;
import com.agmbat.imsdk.chat.body.FileBody;
import com.agmbat.imsdk.chat.body.FireBody;
import com.agmbat.imsdk.chat.body.FriendBody;
import com.agmbat.imsdk.chat.body.ImageBody;
import com.agmbat.imsdk.chat.body.LocationBody;
import com.agmbat.imsdk.chat.body.TextBody;
import com.agmbat.imsdk.chat.body.UrlBody;
import com.agmbat.imsdk.group.CircleInfo;
import com.agmbat.imsdk.group.GroupManager;
import com.agmbat.meetyou.R;
import com.agmbat.meetyou.helper.AvatarHelper;
import com.agmbat.time.TimeUtils;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.message.MessageObject;
import org.jivesoftware.smackx.message.MessageObjectStatus;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;

public class RecentMsgView extends LinearLayout {

    @BindView(R.id.avatar)
    ImageView mAvatarView;

    @BindView(R.id.nickname)
    TextView mNickNameView;

    @BindView(R.id.last_message)
    TextView mMessageView;

    @BindView(R.id.unread_count)
    TextView mUnreadCountView;

    @BindView(R.id.last_msg_time)
    TextView mLastMsgTimeView;

    public RecentMsgView(Context context) {
        super(context);
        View.inflate(context, R.layout.recent_chat_item, this);
        ButterKnife.bind(this, this);
    }

    public void update(MessageObject messageObject) {
        if (messageObject.getChatType() == Message.Type.chat) {
            ContactInfo contactInfo = MessageManager.getTalkContactInfo(messageObject);
            if (contactInfo != null) {
                mNickNameView.setText(TextUtils.isEmpty(contactInfo.getRemark()) ? contactInfo.getNickName() : contactInfo.getRemark());
                mMessageView.setVisibility(View.VISIBLE);
                ImageManager.displayImage(contactInfo.getAvatar(), mAvatarView, AvatarHelper.getOptions());
            }
        } else if (messageObject.getChatType() == Message.Type.groupchat) {
            CircleInfo groupBean = GroupManager.getInstance().getMemCacheGroup(MessageManager.getTalkJid(messageObject));
            if (groupBean != null) {
                mNickNameView.setText(String.format("来自\"%s\"群", groupBean.getName()));
                mMessageView.setVisibility(VISIBLE);
                ImageManager.displayImage(groupBean.getAvatar(), mAvatarView, AvatarHelper.getOptions());
            }
        }
        setLastMessageBody(messageObject);
        mLastMsgTimeView.setText(TimeUtils.formatTime(messageObject.getDate()));
        mLastMsgTimeView.setVisibility(GONE);

        if (messageObject.getMsgStatus() == MessageObjectStatus.UNREAD) {
            mUnreadCountView.setVisibility(VISIBLE);
        } else {
            mUnreadCountView.setVisibility(GONE);
        }
    }


    private void setLastMessageBody(MessageObject msg) {
        Body body = BodyParser.parse(msg.getBody());
        if (body instanceof TextBody) {
            TextBody textBody = (TextBody) body;
            List<TextBody.AtUser> atUsers = textBody.getAtUsers();
            boolean atFlag = false;
            if (null != atUsers) {
                for (int i = 0; i < atUsers.size(); i++) {
                    TextBody.AtUser atUser = atUsers.get(i);
                    if (msg.getMsgStatus() == MessageObjectStatus.UNREAD
                            && atUser.getJid().equals(XMPPManager.getInstance().getXmppConnection().getBareJid())) {
                        atFlag = true;
                        break;
                    }
                }
            }
            if (atFlag) {
                mMessageView.setText("@" + msg.getSenderNickName() + " 在新消息中提到了你");
            } else {
                mMessageView.setText(textBody.getContent());
            }
        } else if (body instanceof AudioBody) {
            mMessageView.setText(R.string.recent_chat_type_voice);
        } else if (body instanceof FireBody) {
            mMessageView.setText(R.string.recent_chat_type_fire);
        } else if (body instanceof ImageBody) {
            mMessageView.setText(R.string.recent_chat_type_pic);
        } else if (body instanceof LocationBody) {
            mMessageView.setText(R.string.recent_chat_type_location);
        } else if (body instanceof FriendBody) {
            mMessageView.setText(R.string.recent_chat_type_recommend);
        } else if (body instanceof FileBody) {
            mMessageView.setText(R.string.recent_chat_type_file);
        } else if (body instanceof UrlBody) {
            mMessageView.setText(R.string.recent_chat_type_url);
        } else {
            mMessageView.setText(msg.getBody());
        }
    }
}
