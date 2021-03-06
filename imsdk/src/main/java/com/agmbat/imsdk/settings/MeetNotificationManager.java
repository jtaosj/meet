package com.agmbat.imsdk.settings;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.support.v4.app.NotificationCompat;
import android.text.Spannable;
import android.text.SpannableString;

import com.agmbat.imsdk.R;
import com.agmbat.imsdk.chat.body.AudioBody;
import com.agmbat.imsdk.chat.body.Body;
import com.agmbat.imsdk.chat.body.BodyParser;
import com.agmbat.imsdk.chat.body.EventsBody;
import com.agmbat.imsdk.chat.body.FileBody;
import com.agmbat.imsdk.chat.body.FireBody;
import com.agmbat.imsdk.chat.body.FriendBody;
import com.agmbat.imsdk.chat.body.ImageBody;
import com.agmbat.imsdk.chat.body.LocationBody;
import com.agmbat.imsdk.chat.body.TextBody;
import com.agmbat.imsdk.chat.body.UrlBody;
import com.agmbat.imsdk.util.AppConfigUtils;
import com.agmbat.imsdk.util.SystemUtil;

import org.jivesoftware.smackx.message.MessageObject;

import static com.agmbat.android.AppResources.getResources;

public class MeetNotificationManager {

    public static final String ACTION_RCV_NEW_MESSAGE = "ACTION_MEET_RCV_NEW_MESSAGE";
    public static final String EXTRA_NEW_MESSAGE = "new_message";
    private static MeetNotificationManager sInstance;
    private NotificationManager mNotificationManager;
    private Context mContext;

    private MeetNotificationManager(Context context) {
        mContext = context;
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public static MeetNotificationManager init(Context context) {
        if (null == sInstance) {
            sInstance = new MeetNotificationManager(context);
        }
        return sInstance;
    }

    public static MeetNotificationManager getInstance() {
        return sInstance;
    }

    public void notifyMessageReceived(MessageObject messageObject) {
        if (!AppConfigUtils.isNotificationEnable(mContext) || !SystemUtil.isAppBackground(mContext)) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
        Intent intent = new Intent(ACTION_RCV_NEW_MESSAGE);
        intent.putExtra(EXTRA_NEW_MESSAGE, messageObject);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);

        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        builder.setAutoCancel(true);
        if (messageObject.getFromJid().equals("support@yuan520.com")) {
            builder.setContentTitle("管理员");
        } else {
            builder.setContentTitle(messageObject.getSenderNickName());
        }
        builder.setContentText(getContentSpannable(messageObject.getBody()));
        builder.setWhen(System.currentTimeMillis());
        Notification notification = builder.build();
        if (AppConfigUtils.isNotificationSoundEnable(mContext)) {
            notification.sound = RingtoneManager.getActualDefaultRingtoneUri(mContext, RingtoneManager.TYPE_NOTIFICATION);
        }
        notification.icon = R.mipmap.ic_launcher;
        mNotificationManager.notify(messageObject.getSenderJid().hashCode(), notification);
    }


    private Spannable getContentSpannable(String rawBody) {
        Body body = BodyParser.parse(rawBody);
        Spannable spannable = null;
        if (body instanceof TextBody) {
            //TextBody textBody = (TextBody) body;
            //spannable = EmojiDisplay.update(textBody.getContent(), (int) SysResources.dipToPixel(14));
            spannable = new SpannableString("发来一条文字信息，请点开查看...");
        } else if (body instanceof UrlBody) {
            UrlBody urlBody = (UrlBody) body;
            //spannable = new SpannableString(urlBody.getContent());
            spannable = new SpannableString("发来一条链接，请点开查看...");
        } else if (body instanceof AudioBody) {
            spannable = new SpannableString("发来一条语音信息，请点开查看...");
        } else if (body instanceof FireBody) {
            spannable = new SpannableString("发来一条阅后即焚信息，请点开查看...");
        } else if (body instanceof ImageBody) {
            spannable = new SpannableString("发来一条图片信息，请点开查看...");
        } else if (body instanceof LocationBody) {
            spannable = new SpannableString("发来一条位置信息，请点开查看...");
        } else if (body instanceof FriendBody) {
            spannable = new SpannableString("发来一个推荐好友，请点开查看...");
        } else if (body instanceof FileBody) {
            spannable = new SpannableString("发来一个文件，请点开查看...");
        } else if (body instanceof EventsBody) {
            spannable = new SpannableString("发来一条活动通知，请点开查看...");
        }
        return spannable;
    }


}
