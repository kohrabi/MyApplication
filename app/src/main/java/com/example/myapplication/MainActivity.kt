package com.example.myapplication

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.example.myapplication.models.LRCLIBObject
import com.example.myapplication.models.Lyric
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import io.ktor.client.call.body
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update


class MainActivity : ComponentActivity() {
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "TEST"
            val descriptionText = "TESTSTS"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(Notification.CATEGORY_MESSAGE, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system.
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()

        val mediaSession = MediaSession(this, "MusicService");
        var builder = Notification.Builder(this, Notification.CATEGORY_MESSAGE)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("it's not litter if you bin it")
            .setContentText("Niko B - dog eats dog food world")
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken))
        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                println("nope");
                return@with
            }
            // notificationId is a unique int for each notification that you must define.
            notify(0, builder.build())
        }
        setContent {
            MyApplicationTheme {
                YoutubePlayer("Zgd1corMdnk", LocalLifecycleOwner.current, mediaSession)
            }
        }
    }
}

fun parseSyncedLyrics(syncedLyrics : String) : List<Lyric> {
    var lyrics : MutableList<Lyric> = mutableListOf();
    syncedLyrics.split('\n').forEach { it ->
        // retarded certified
        var i : Int = 1;
        var seconds : Float = 0f;
        var multiplierCount : Int = 0;
        val multipliers : Array<Float> = arrayOf(60f, 1f, 0.01f);
        while (i < it.length && multiplierCount < 3) // Khong biet su dung regex belike
        {
            var num : String = "";
            while (i < it.length &&
                (it[i] != ':' && it[i] != '.' && it[i] != ']'))
            {
                num = num + it[i];
                i++;
            }
            seconds += num.toFloatOrNull()?.times(multipliers[multiplierCount]) ?: 0f;
            multiplierCount++;
            i++;
        }
        lyrics.add(Lyric(startSeconds = seconds, words = it.substring(i + 1)))
    };

    return lyrics;
}

val availableActions : Long =
    (PlaybackState.ACTION_SEEK_TO
        or PlaybackState.ACTION_PAUSE
        or PlaybackState.ACTION_STOP
        or PlaybackState.ACTION_PLAY
        or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        or PlaybackState.ACTION_SKIP_TO_NEXT);

private fun getState(state : PlayerConstants.PlayerState) : Int {
    when(state) {
        PlayerConstants.PlayerState.PLAYING ->      return PlaybackState.STATE_PLAYING;
        PlayerConstants.PlayerState.ENDED ->        return PlaybackState.STATE_STOPPED;
        PlayerConstants.PlayerState.PAUSED ->       return PlaybackState.STATE_PAUSED;
        PlayerConstants.PlayerState.UNKNOWN ->      return PlaybackState.STATE_NONE;
        PlayerConstants.PlayerState.BUFFERING ->    return PlaybackState.STATE_BUFFERING;
        PlayerConstants.PlayerState.UNSTARTED ->    return PlaybackState.STATE_NONE;
        PlayerConstants.PlayerState.VIDEO_CUED ->   return PlaybackState.STATE_NONE;
    }
}

class YoutubePlayerHelper {
    var ytPlayer : YouTubePlayer? = null;
    var ytVideoTracker : YouTubePlayerTracker;
    var seekToTime : Float = 0.0f;

    init {
        ytVideoTracker = YouTubePlayerTracker();
    }

    public fun seekTo(time : Float) {
        if (ytPlayer != null) {
            seekToTime = time;
            ytPlayer!!.seekTo(seekToTime);
        }
    }

    public fun play() {
        ytPlayer?.play();
    }

    public fun pause() {
        ytPlayer?.pause();
    }
}

class YoutubeVideoPlayer(context : Context) : ViewModel() {
    private val _mediaSession = MutableStateFlow(MediaSession(context, "MusicService"));
    val mediaSession : StateFlow<MediaSession> = _mediaSession.asStateFlow();

    private val _ytHelper = MutableStateFlow(YoutubePlayerHelper());
    val ytHelper : StateFlow<YoutubePlayerHelper> = _ytHelper.asStateFlow();

    val NotificationID = 0;

    init {
        var builder = Notification.Builder(context, Notification.CATEGORY_MESSAGE)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("it's not litter if you bin it")
            .setContentText("Niko B - dog eats dog food world")
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.value.sessionToken))
        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                println("nope");
                return@with
            }
            // notificationId is a unique int for each notification that you must define.
            notify(NotificationID, builder.build())
        }

        _mediaSession.value.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                _ytHelper.update { current ->
                    current.play();
                    current;
                }
            }

            override fun onPause() {
                _ytHelper.update { current ->
                    current.pause();
                    current;
                }
            }

            override fun onSeekTo(pos: Long) {
                _ytHelper.update { current ->
                    current.seekTo(pos / 1000f);
                    current;
                }
//                currentSyncedIndex = syncedLyrics.indexOfFirst { it ->
//                    seekSecond < it.startSeconds;
//                } - 1;
            }
        })

    }
}

@Composable
fun YoutubePlayer(
    youtubeVideoId: String,
    lifecycleOwner: LifecycleOwner,
    mediaSession: MediaSession
) {
    val ytMusic by remember {
        mutableStateOf(Ytmusic());
    }
    var ytPlayer : YouTubePlayer? by remember {
        mutableStateOf(null);
    }
    var ytVideoTracker : YouTubePlayerTracker = remember {
        YouTubePlayerTracker();
    };
    var sliderPosition by remember {
        mutableStateOf(0.0f)
    }
    var onSlider by remember {
        mutableStateOf(false)
    }
    var lrclibObj : LRCLIBObject? by remember {
        mutableStateOf(null)
    }
    var syncedLyrics : List<Lyric> by remember {
        mutableStateOf(listOf());
    }
    var currentSyncedIndex : Int by remember {
        mutableStateOf(0);
    }
    var syncedLine : Lyric by remember {
        mutableStateOf(Lyric(0f, ""))
    }
    var seekSecond by remember {
        mutableStateOf(0f)
    }
    LaunchedEffect(Unit) { // Co the gay overload LaunchedEffect(youtubeVideoId)
        println("Oh no");
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() {
                ytPlayer?.play();
            }

            override fun onPause() {
                ytPlayer?.pause();
            }

            override fun onSeekTo(pos: Long) {
                seekSecond = pos / 1000f;
                ytPlayer?.seekTo(pos / 1000f);
                currentSyncedIndex = syncedLyrics.indexOfFirst { it ->
                    seekSecond < it.startSeconds;
                } - 1;
            }
        })

        val lyricsList = ytMusic.searchLrclibLyrics("it's not litter if you bin it", "Niko B").body<List<LRCLIBObject>>();
        if (lyricsList.isNotEmpty()) {
            lrclibObj = lyricsList.first();
            if (lrclibObj!!.syncedLyrics != null) {
                lrclibObj!!.syncedLyrics?.let { syncedLyrics = parseSyncedLyrics(it) };
            }
        }
    }
    val syncedLyricsBuffer : Float = 0.2f;
    AndroidView(
        factory = { context ->
            YouTubePlayerView(context = context).apply {

                //lifecycleOwner.lifecycle.addObserver(this);
                enableBackgroundPlayback(true);

                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        ytPlayer = youTubePlayer;
                        ytPlayer?.addListener(ytVideoTracker);
                        youTubePlayer.loadVideo(youtubeVideoId, 0f);
                    }

                    override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                        if (!mediaSession.isActive)
                        {
                            val metadataBuilder = MediaMetadata.Builder().apply {
                                // To provide most control over how an item is displayed set the
                                // display fields in the metadata
                                putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, "it's not litter if you bin it")
                                putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, "test")
                                // And at minimum the title and artist for legacy support
                                putString(MediaMetadata.METADATA_KEY_TITLE, "it's not litter if you bin it")
                                putString(MediaMetadata.METADATA_KEY_ARTIST, "Niko B - dog eats dog food world")
                                putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong() * 1000L)
                                // A small bitmap for the artwork is also recommended
                                // Add any other fields you have for your data as well
                            }
                            // println("wtf");
                            mediaSession.setPlaybackState(
                                PlaybackState.Builder()
                                    .setActions(availableActions)
                                    .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                                    .build()
                            )
                            mediaSession.setMetadata(metadataBuilder.build())
                            mediaSession.isActive = true;
                        }
                    }

                    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                        if (!onSlider)
                            sliderPosition = second / ytVideoTracker.videoDuration;
                        if (currentSyncedIndex < syncedLyrics.size &&
                            second + syncedLyricsBuffer >= syncedLyrics[currentSyncedIndex].startSeconds) {
                            syncedLine = syncedLyrics[currentSyncedIndex];
                            currentSyncedIndex++;
                        }
                    }

                    override fun onStateChange(
                        youTubePlayer: YouTubePlayer,
                        state: PlayerConstants.PlayerState
                    ) {
                        if (state == PlayerConstants.PlayerState.UNSTARTED)
                            return;
                        val position : Long =
                            seekSecond.toLong() * 1000L;
                        println("${state.toString()}. second: ${seekSecond}")
                        mediaSession.setPlaybackState(
                            PlaybackState.Builder()
                            .setActions(availableActions)
                            .setState(getState(state), position, 1.0f)
                            .build()
                        );
                    }
                });
            }
        }
    )
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize()
        ) {
        Button(onClick = {
                ytPlayer?.play();
            }) {
            Text("Play");
        }
        Slider(
            value = sliderPosition,
            valueRange = 0f..1f,
            onValueChange = { value ->
//                if (ytPlayer != null) {
//                    ytPlayer?.seekTo(ytVideoTracker.videoDuration * sliderPosition);
//                }
                sliderPosition = value;
                onSlider = true;
            },
            onValueChangeFinished = {
                if (ytPlayer != null) {
                    seekSecond = ytVideoTracker.videoDuration * sliderPosition;
                    ytPlayer?.seekTo(ytVideoTracker.videoDuration * sliderPosition);
                    ytPlayer?.play();
                    currentSyncedIndex = syncedLyrics.indexOfFirst { it ->
                        seekSecond < it.startSeconds;
                    } - 1;
                }
                onSlider = false;
            },
            modifier = Modifier.size(200.dp)
        )
        Button(onClick = {
            ytPlayer?.pause();
            }) {
            Text("Pause");
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(top = 300.dp)
    ) {

        Text(
            text = "Synced Lyrics: ",
            textAlign = TextAlign.Center,
            fontSize = 32.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = syncedLine.words,
            textAlign = TextAlign.Center,
            fontSize = 32.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
