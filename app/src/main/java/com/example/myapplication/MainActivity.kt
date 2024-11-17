package com.example.myapplication

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.models.LRCLIBObject
import com.example.myapplication.models.Line
import com.example.myapplication.models.Lyrics
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import io.ktor.client.call.body


class MainActivity : ComponentActivity() {
    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Nhaccuatiu"
            val descriptionText = "Music Player"
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

        setContent {
            MyApplicationTheme {
                YoutubePlayer("Zgd1corMdnk", YoutubeViewModel(LocalContext.current))
            }
        }
    }
}

fun parseSyncedLyrics(syncedLyrics : String) : List<Line> {
    var lines : MutableList<Line> = mutableListOf();
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
        lines.add(Line(startSeconds = seconds, words = it.substring(i + 1)))
    };

    return lines;
}

fun parsePlainLyrics(plainLyrics : String) : List<Line> {
    var lines : MutableList<Line> = mutableListOf();
    plainLyrics.split('\n').forEach{ it ->
        lines.add(Line(startSeconds = 0f, words = it));
    };
    return lines;
}

suspend fun getLyrics(ytMusic : Ytmusic, track : String, artist : String) : Lyrics {
    var lines : List<Line> = emptyList();
    var isSynced = false;
    val lyricsList = ytMusic.searchLrclibLyrics("it's not litter if you bin it", "Niko B").body<List<LRCLIBObject>>();
    if (lyricsList.isNotEmpty()) {
        val lrclibObj = lyricsList.first();
        if (lrclibObj.syncedLyrics != null) {
            lrclibObj.syncedLyrics.let { lines = parseSyncedLyrics(it) };
            isSynced = true;
        }
        else if (lrclibObj.plainLyrics != null) {
            lrclibObj.plainLyrics.let { lines = parsePlainLyrics(it) };
            isSynced = false;
        }
    }
    return Lyrics(lines = lines, isSynced = true);
}

@Composable
fun YoutubePlayer(
    youtubeVideoId: String,
    ytViewModel : YoutubeViewModel = YoutubeViewModel(LocalContext.current)
) {
    val ytMusic by remember {
        mutableStateOf(Ytmusic());
    }
    val mediaSession by ytViewModel.mediaSession.collectAsState()
    val ytHelper by ytViewModel.ytHelper.collectAsState()

    var sliderPosition by remember {
        mutableFloatStateOf(0.0f)
    }
    var onSlider by remember {
        mutableStateOf(false)
    }
    // Lyrics
    var lyrics : Lyrics? by remember {
        mutableStateOf(null);
    }
    var currentSyncedIndex : Int by remember {
        mutableStateOf(0);
    }
    var syncedLine : Line by remember {
        mutableStateOf(Line(0f, ""))
    }
    LaunchedEffect(Unit) { // Co the gay overload LaunchedEffect(youtubeVideoId)

        ytViewModel.addMediaNotificationSeekListener(object : MediaNotificationSeek {
            override fun onSeek(seekTime: Float) {
                if (lyrics != null){
                    currentSyncedIndex = lyrics!!.lines.indexOfFirst { it ->
                        ytHelper.seekToTime < it.startSeconds;
                    } - 1
                }
            }
        });

        // Do async here
        lyrics = getLyrics(ytMusic, "it's not litter if you bin it", "Niko B");
    }
    val syncedLyricsBuffer : Float = 0.2f;
    AndroidView(
        factory = { context ->
            YouTubePlayerView(context = context).apply {

                //lifecycleOwner.lifecycle.addObserver(this);
                enableBackgroundPlayback(true);

                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        ytViewModel.updateYoutubePlayer(youTubePlayer);
                        youTubePlayer.loadVideo(youtubeVideoId, 0f);
                    }

                    override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
                        if (!mediaSession.isActive)
                        {
                            ytViewModel.updateMediaMetadata(
                                "it's not litter if you bin it",
                                "test",
                                "it's not litter if you bin it",
                                "Niko B",
                                duration.toLong() * 1000L
                            );
                            ytViewModel.setMediaSessionActive(true);
                            // println("Playing");
                            ytViewModel.updatePlaybackState(
                                state = PlayerConstants.PlayerState.PLAYING,
                                position = 0,
                                playbackSpeed = 1.0f
                            );
                        }
                    }

                    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                        if (!onSlider)
                            sliderPosition = second / ytHelper.duration;
                        if (lyrics != null) {
                            if (currentSyncedIndex < lyrics!!.lines.size &&
                                second + syncedLyricsBuffer >= lyrics!!.lines[currentSyncedIndex].startSeconds) {
                                syncedLine = lyrics!!.lines[currentSyncedIndex];
                                currentSyncedIndex++;
                            }
                        }
                    }

                    override fun onStateChange(
                        youTubePlayer: YouTubePlayer,
                        state: PlayerConstants.PlayerState
                    ) {
                        if (state == PlayerConstants.PlayerState.UNSTARTED)
                            return;
                        // println(state);
                        ytViewModel.updatePlaybackState(
                            state = state,
                            position = ytHelper.seekToTime.toLong() * 1000L,
                            playbackSpeed = 1.0f
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
                ytHelper.play();
            }) {
            Text("Play");
        }
        Slider(
            value = sliderPosition,
            valueRange = 0f..1f,
            onValueChange = { value ->
                sliderPosition = value;
                onSlider = true;
            },
            onValueChangeFinished = {
                ytHelper.seekTo(ytHelper.duration * sliderPosition);
                onSlider = false;
            },
            modifier = Modifier.size(200.dp)
        )
        Button(onClick = {
            ytHelper.pause();
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
