package com.example.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.example.musicplayer.Constants.CHANNEL_ID
import com.example.musicplayer.Constants.CHANNEL_NAME
import com.example.musicplayer.Constants.MUSIC_NOTIFICATION_ID


class PlayerService: Service() {

    private var mediaPlayer = MediaPlayer()
    private var musicRepository = MusicRepository()
    private val metadataBuilder = MediaMetadataCompat.Builder()
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var notificationManager: NotificationManager

    private val stateBuilder = PlaybackStateCompat.Builder().setActions(
        PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    )

    private val mediaSessionCallback = object : MediaSessionCompat.Callback(){

        var currentState = PlaybackStateCompat.STATE_STOPPED

        override fun onPlay() {
            mediaPlayer.start()
            mediaSession.setPlaybackState(stateBuilder.setState(
                PlaybackStateCompat.STATE_PLAYING,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1F
            ).build())
            currentState = PlaybackStateCompat.STATE_PLAYING

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onPause() {
            mediaPlayer.pause()
            mediaSession.setPlaybackState(stateBuilder.setState(
                PlaybackStateCompat.STATE_PAUSED,
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
                1F
            ).build())
            currentState = PlaybackStateCompat.STATE_PAUSED

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSkipToNext() {
            mediaPlayer.release()
            mediaPlayer = MediaPlayer.create(
                this@PlayerService,
                musicRepository.getNextSong().id
            )

            if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                mediaPlayer.start()

            updateMetaData(musicRepository.getCurrentSong())

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSkipToPrevious() {
            mediaPlayer.release()
            mediaPlayer = MediaPlayer.create(
                this@PlayerService,
                musicRepository.getPreviousSong().id
            )

            if (mediaSession.controller.playbackState.state == PlaybackStateCompat.STATE_PLAYING)
                mediaPlayer.start()

            updateMetaData((musicRepository.getCurrentSong()))

            refreshNotificationAndForegroundStatus(currentState)
        }

        override fun onSeekTo(pos: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                mediaPlayer.seekTo(pos, MediaPlayer.SEEK_CLOSEST_SYNC)
            else
                mediaPlayer.seekTo(pos.toInt())
        }
    }

    override fun onCreate() {
        super.onCreate()

        musicRepository.initPlayList()

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(notificationChannel)
        }

        mediaSession = MediaSessionCompat(this, "PlayerService")
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setCallback(mediaSessionCallback)

        val appContext = applicationContext

        val activityIntent = Intent(appContext, MainActivity::class.java)
        mediaSession.setSessionActivity(PendingIntent.getActivity(
            appContext,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE
        ))

        initMedia()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return PlayerServiceBinder()
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaSession.release()
        mediaPlayer.release()
    }

    private fun initMedia() {
        mediaPlayer = MediaPlayer.create(this, musicRepository.getCurrentSong().id)
        updateMetaData(musicRepository.getCurrentSong())
    }

    private fun getNotification(playbackState: Int): Notification {
        val intentPrev = Intent("prev_song")
        val intentNext = Intent("next_song")
        val intentPause = Intent("pause_song")
        val intentPlay = Intent("play_song")

        val flag = PendingIntent.FLAG_IMMUTABLE
        val context = applicationContext

        val pendingIntentPrev = PendingIntent.getBroadcast(context, 0, intentPrev, flag)
        val pendingIntentNext = PendingIntent.getBroadcast(context, 0, intentNext, flag)
        val pendingIntentPause = PendingIntent.getBroadcast(context, 0, intentPause, flag)
        val pendingIntentPlay = PendingIntent.getBroadcast(context, 0, intentPlay, flag)

        val builder =  NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(musicRepository.getCurrentSong().songName)
            .setContentText(musicRepository.getCurrentSong().songAuthor)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(mediaSession.controller.sessionActivity)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSilent(true)
            .addAction(R.drawable.previous_icon, "prev", pendingIntentPrev)

        if (playbackState == PlaybackStateCompat.STATE_PLAYING)
            builder.addAction(R.drawable.pause_icon, "pause", pendingIntentPause)
        else builder.addAction(R.drawable.play_icon, "play", pendingIntentPlay)

        builder.addAction(R.drawable.next_icon, "next", pendingIntentNext)
        builder.setChannelId(CHANNEL_ID)

        return builder.build()
    }

    private fun updateMetaData(song: MusicClass) {
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.songName)
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.songAuthor)
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.duration.toLong())
        mediaSession.setMetadata(metadataBuilder.build())

        sendMetadataIntent()
    }

    private fun sendMetadataIntent() {
        val intent = Intent("song_data")
        intent.putExtra("song_name", mediaSession.controller.metadata.getText(MediaMetadataCompat.METADATA_KEY_TITLE))
        intent.putExtra("song_author", mediaSession.controller.metadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST))
        intent.putExtra("song_duration", mediaSession.controller.metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
        sendBroadcast(intent)
    }

    private fun refreshNotificationAndForegroundStatus(playbackState: Int) {
        when (playbackState) {
            PlaybackStateCompat.STATE_PLAYING -> {
                startForeground(MUSIC_NOTIFICATION_ID, getNotification(playbackState))
            }

            PlaybackStateCompat.STATE_PAUSED -> {
                notificationManager.notify(MUSIC_NOTIFICATION_ID, getNotification(playbackState))
                this.stopForeground(STOP_FOREGROUND_DETACH)
            }

            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
        }
    }

    inner class PlayerServiceBinder : Binder() {
        val mediaSessionToken: MediaSessionCompat.Token = mediaSession.sessionToken
    }
}
