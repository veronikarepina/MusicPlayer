package com.example.musicplayer

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.os.RemoteException
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.example.musicplayer.databinding.ActivityMainBinding
import com.example.musicplayer.PlayerService.PlayerServiceBinder
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var timer: SongTimer? = null

    private var playerServiceBinder: PlayerService.PlayerServiceBinder? = null
    private var mediaController: MediaControllerCompat? = null
    private lateinit var callback: MediaControllerCompat.Callback
    private var serviceConnection: ServiceConnection? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaController?.transportControls?.seekTo(progress.toLong())
                    setCurrentTimeAndSeekBar(seek?.progress?.toLong() ?: 0)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {}

        })

        callback = object: MediaControllerCompat.Callback(){
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                if (state == null)
                    return
                val playing = state.state == PlaybackStateCompat.STATE_PLAYING

                if (playing){
                    binding.playButton.visibility = View.GONE
                    binding.pausePutton.visibility = View.VISIBLE
                }
                else {
                    binding.pausePutton.visibility = View.GONE
                    binding.playButton.visibility = View.VISIBLE
                }
            }
        }
        serviceConnection = object : ServiceConnection {

            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                playerServiceBinder = service as PlayerServiceBinder
                try {
                    mediaController = playerServiceBinder?.mediaSessionToken?.let {
                        MediaControllerCompat(this@MainActivity, it)
                    }
                    mediaController?.registerCallback(callback)
                    callback.onPlaybackStateChanged(mediaController?.playbackState)
                } catch (e: RemoteException) {
                    mediaController = null
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                playerServiceBinder = null
                if (mediaController != null) {
                    mediaController?.unregisterCallback(callback)
                    mediaController = null
                }
            }
        }

        bindService(Intent(this, PlayerService::class.java),
            serviceConnection as ServiceConnection, BIND_AUTO_CREATE)

        initBroadcastReceivers()

        binding.playButton.setOnClickListener {
            mediaController?.transportControls?.play()
            startTimer()
        }

        binding.pausePutton.setOnClickListener {
            mediaController?.transportControls?.pause()
            stopTimer()
        }

        binding.nextSongButton.setOnClickListener {
            mediaController?.transportControls?.skipToNext()
            setCurrentTimeAndSeekBar(0)
        }

        binding.previousSongButton.setOnClickListener {
            mediaController?.transportControls?.skipToPrevious()
            setCurrentTimeAndSeekBar(0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaController?.unregisterCallback(callback)
        mediaController = null
        serviceConnection?.let { unbindService(it) }
    }

    private fun initBroadcastReceivers() {
        val broadcastReceiverMetaData = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                val songName = intent?.extras?.getString("song_name")
                val songAuthor = intent?.extras?.getString("song_author")
                val duration = intent?.extras?.getLong("song_duration")
                initSongData(songName, songAuthor, duration)
            }
        }

        val broadcastReceiverPrevSong = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                mediaController?.transportControls?.skipToPrevious()
                setCurrentTimeAndSeekBar(0)
            }
        }

        val broadcastReceiverNextSong = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                mediaController?.transportControls?.skipToNext()
                setCurrentTimeAndSeekBar(0)
            }
        }

        val broadcastReceiverPauseSong = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                mediaController?.transportControls?.pause()
                stopTimer()
            }
        }

        val broadcastReceiverPlaySong = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                mediaController?.transportControls?.play()
                startTimer()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiverMetaData, IntentFilter("song_data"), RECEIVER_NOT_EXPORTED)
            registerReceiver(broadcastReceiverPrevSong, IntentFilter("prev_song"), RECEIVER_NOT_EXPORTED)
            registerReceiver(broadcastReceiverNextSong, IntentFilter("next_song"), RECEIVER_NOT_EXPORTED)
            registerReceiver(broadcastReceiverPauseSong, IntentFilter("pause_song"), RECEIVER_NOT_EXPORTED)
            registerReceiver(broadcastReceiverPlaySong, IntentFilter("play_song"), RECEIVER_NOT_EXPORTED)
        }
    }

    private fun initSongData(name: String?, author: String?, duration: Long?) = with(binding) {
        songName.text = name
        songAuthor.text = author
        currentTime.text = getString(R.string.default_time)
        maxTime.text = millisecondsToString(duration)
        seekBar.progress = 0
        seekBar.max = duration?.toInt() ?: 0

        timer?.cancel()
        timer = null
        timer = SongTimer(duration ?: 0)

        if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING)
            timer?.start()
    }

    private fun setCurrentTimeAndSeekBar(time: Long) = with(binding) {
        currentTime.text = millisecondsToString(time)
        timer?.cancel()
        timer = null

        val newDuration = seekBar.max.toLong() - time

        timer = SongTimer(duration = newDuration, time = time)
        if (mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING)
            timer?.start()
    }

    private fun millisecondsToString(ms: Long?): String {
        var seconds = TimeUnit.MILLISECONDS.toSeconds(ms ?: 0)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds)
        seconds %= 60
        return String.format("%01d : %02d", minutes, seconds)
    }

    private fun startTimer() {
        timer?.start()
    }

    private fun stopTimer() {
        timer?.cancel()
    }

    inner class SongTimer(duration: Long, time: Long = 0) : CountDownTimer(duration, ONE_SECOND_LONG) {

        private var startTime = time

        override fun onTick(millisUntilFinished: Long) = with(binding){
            startTime += ONE_SECOND_LONG
            Log.d("MyLog", "progress: ${seekBar.progress}")
            seekBar.progress = seekBar.progress + ONE_THOUSAND
            Log.d("MyLog", "progress: ${seekBar.progress}")
            currentTime.text = millisecondsToString(startTime)
        }

        override fun onFinish() {
            mediaController?.transportControls?.skipToNext()
            stopTimer()
        }
    }

    companion object {
        private const val ONE_SECOND_LONG: Long = 1000
        private const val ONE_THOUSAND = 1000
    }
}