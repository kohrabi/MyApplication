package com.example.myapplication

import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import com.example.myapplication.models.YouTubeClient
import com.example.myapplication.ui.theme.MyApplicationTheme
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import io.ktor.client.call.body
import kotlinx.coroutines.runBlocking
import java.io.File


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val ytMusic = Ytmusic();
        runBlocking  {
            val message = ytMusic.browse(YouTubeClient.WEB_REMIX, "UCo1DYcm1IZ9v3UPkpiAcgtg").body<String>();
            // val message = ytMusic.browse(YouTubeClient.WEB_REMIX, "UCoZ16I1S9TANAK--fAyUQCA").body<String>();
            val maxLogSize = 1000;
            for (i in 0..message.length / maxLogSize) {
                val start = i * maxLogSize;
                var end = (i + 1) * maxLogSize;
                end = if (end > message.length) message.length else end;
                println(message.substring(start, end));
            }
            println("here");
        }
        setContent {
            MyApplicationTheme {
                YoutubePlayer("0jRDKoF3Mp0", LocalLifecycleOwner.current)
            }
        }
    }
}



@Composable
fun YoutubePlayer(
    youtubeVideoId: String,
    lifecycleOwner: LifecycleOwner
) {
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
    AndroidView(
        factory = { context ->
            YouTubePlayerView(context = context).apply {

                lifecycleOwner.lifecycle.addObserver(this);

                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        ytPlayer = youTubePlayer;
                        ytPlayer?.addListener(ytVideoTracker);
                        youTubePlayer.loadVideo(youtubeVideoId, 0f);
                    }

                    override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
                        if (!onSlider)
                            sliderPosition = second / ytVideoTracker.videoDuration;
                    }
                });
            }
        },
        modifier = Modifier.size(0.dp)
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
                ytPlayer?.seekTo(ytVideoTracker.videoDuration * sliderPosition);
                sliderPosition = value;
                onSlider = true;
            },
            onValueChangeFinished = {
                ytPlayer?.seekTo(ytVideoTracker.videoDuration * sliderPosition);
                ytPlayer?.play();
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
}

@Preview(showBackground = true)
@Composable
fun Preview() {
    YoutubePlayer("0jRDKoF3Mp0", LocalLifecycleOwner.current);
}