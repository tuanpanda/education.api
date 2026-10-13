package com.education.base.service.lesson.impl;

import com.education.base.exception.OracleBusinessException;
import com.education.base.service.lesson.VideoEmbedResolver.VideoEmbed;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoEmbedResolverImplTest {

    final VideoEmbedResolverImpl resolver = new VideoEmbedResolverImpl();

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://youtube.com/watch?feature=share&v=dQw4w9WgXcQ|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "http://m.youtube.com/watch?v=dQw4w9WgXcQ&t=90s|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=90",
            "https://youtu.be/dQw4w9WgXcQ|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ?t=1m30s|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=90",
            "https://www.youtube.com/shorts/dQw4w9WgXcQ|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube.com/embed/dQw4w9WgXcQ?start=30|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ?start=30",
            "https://www.youtube.com/live/dQw4w9WgXcQ|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ|YOUTUBE|https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ",
            "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp_-q/view?usp=sharing|DRIVE|https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp_-q/preview",
            "https://drive.google.com/open?id=1AbCdEfGhIjKlMnOp_-q|DRIVE|https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp_-q/preview",
            "https://vimeo.com/76979871|VIMEO|https://player.vimeo.com/video/76979871",
            "https://www.vimeo.com/76979871/|VIMEO|https://player.vimeo.com/video/76979871",
            "https://player.vimeo.com/video/76979871?h=abc|VIMEO|https://player.vimeo.com/video/76979871",
    })
    void validUrls(String url, String provider, String embed) {
        VideoEmbed v = resolver.resolve("  " + url + " ");
        assertThat(v.provider()).isEqualTo(provider);
        assertThat(v.embedUrl()).isEqualTo(embed);
        assertThat(v.normalizedUrl()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "ftp://youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ",
            "https://evil.com/youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtube.com@evil.com/watch?v=dQw4w9WgXcQ",
            "https://notyoutube.com/watch?v=dQw4w9WgXcQ",
            "https://www.youtube.com/watch?v=short",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ\"onload=",
            "https://www.youtube.com/channel/UCabc",
            "https://youtu.be/",
            "https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOp",
            "https://docs.google.com/file/d/1AbCdEfGhIjKlMnOp/view",
            "https://vimeo.com/channels/staffpicks",
            "https://player.vimeo.com/video/abc",
            "//youtube.com/watch?v=dQw4w9WgXcQ",
            "youtube.com/watch?v=dQw4w9WgXcQ",
    })
    void invalidUrls(String url) {
        assertThatThrownBy(() -> resolver.resolve(url))
                .isInstanceOf(OracleBusinessException.class)
                .extracting("errorCode").isEqualTo("LESSON_VIDEO_URL_NOT_ALLOWED");
    }

    @ParameterizedTest
    @CsvSource({"90,90", "90s,90", "1m30s,90", "1h2m3s,3723", "abc,0", "'',0"})
    void startSeconds(String t, long expected) {
        assertThat(VideoEmbedResolverImpl.seconds(t)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"youtu.be,YOUTUBE", "drive.google.com,DRIVE", "player.vimeo.com,VIMEO", "example.com,OTHER",
            "youtube.com.evil.com,OTHER"})
    void providerOfHost(String host, String provider) {
        assertThat(VideoEmbedResolverImpl.providerOfHost(host)).isEqualTo(provider);
    }
}
