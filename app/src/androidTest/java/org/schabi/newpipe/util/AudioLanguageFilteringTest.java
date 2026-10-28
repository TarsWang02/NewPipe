package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;


import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.AudioTrackType;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Focused experiment for language tracks without audio track IDs (issue #10720). */
@RunWith(AndroidJUnit4.class)
public class AudioLanguageFilteringTest {
    private Context context;
    private SharedPreferences preferences;
    private String oldLanguage;
    private boolean hadLanguage;
    private boolean oldPreferOriginal;
    private boolean hadPreferOriginal;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final String languageKey = context.getString(R.string.content_language_key);
        final String originalKey = context.getString(R.string.prefer_original_audio_key);
        hadLanguage = preferences.contains(languageKey);
        oldLanguage = preferences.getString(languageKey, "system");
        hadPreferOriginal = preferences.contains(originalKey);
        oldPreferOriginal = preferences.getBoolean(originalKey, true);
        preferences.edit().putString(languageKey, "de").putBoolean(originalKey, false).commit();
    }

    @After
    public void restorePreferences() {
        final String languageKey = context.getString(R.string.content_language_key);
        final String originalKey = context.getString(R.string.prefer_original_audio_key);
        final SharedPreferences.Editor editor = preferences.edit();
        if (hadLanguage) {
            editor.putString(languageKey, oldLanguage);
        } else {
            editor.remove(languageKey);
        }
        if (hadPreferOriginal) {
            editor.putBoolean(originalKey, oldPreferOriginal);
        } else {
            editor.remove(originalKey);
        }
        editor.commit();
    }

    private AudioStream stream(final String id, final Locale locale) {
        return stream(id, null, locale, MediaFormat.MP3);
    }

    private AudioStream stream(final String id, final String trackId, final Locale locale,
                               final MediaFormat format) {
        return new AudioStream.Builder().setId(id).setContent("", true)
                .setMediaFormat(format).setAverageBitrate(128)
                .setAudioTrackId(trackId).setAudioLocale(locale).build();
    }

    @Test
    public void filteringShouldKeepBothLanguagesWhenTrackIdsAreMissing() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("en", Locale.ENGLISH), stream("de", Locale.GERMAN)));
        assertEquals("Different languages should remain selectable", 2, filtered.size());
        assertTrue(filtered.stream().anyMatch(s -> Locale.GERMAN.equals(s.getAudioLocale())));
    }

    @Test
    public void selectionChoosesGermanWhenBothCandidatesAreAvailable() {
        final List<AudioStream> streams = List.of(stream("en", Locale.ENGLISH),
                stream("de", Locale.GERMAN));
        final int selected = ListHelper.getAudioFormatIndex(context, streams, null);
        assertEquals(Locale.GERMAN, streams.get(selected).getAudioLocale());
    }

    @Test
    public void selectionAfterFilteringShouldChooseGerman() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("en", Locale.ENGLISH), stream("de", Locale.GERMAN)));
        final int selected = ListHelper.getAudioFormatIndex(context, filtered, null);
        assertEquals(Locale.GERMAN, filtered.get(selected).getAudioLocale());
    }

    @Test
    public void filteringShouldKeepBothLanguagesInReverseOrder() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("de", Locale.GERMAN), stream("en", Locale.ENGLISH)));
        assertEquals("Both languages should survive regardless of order", 2, filtered.size());
    }

    @Test
    public void filteringKeepsTracksWithDistinctIds() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("en", "en-track", Locale.ENGLISH, MediaFormat.MP3),
                        stream("de", "de-track", Locale.GERMAN, MediaFormat.MP3)));
        assertEquals(2, filtered.size());
    }

    @Test
    public void filteringKeepsOneFormatPerKnownTrack() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("en-mp3", "en-track", Locale.ENGLISH, MediaFormat.MP3),
                        stream("en-m4a", "en-track", Locale.ENGLISH, MediaFormat.M4A)));
        assertEquals(1, filtered.size());
        assertEquals(Locale.ENGLISH, filtered.get(0).getAudioLocale());
    }

    @Test
    public void filteringGroupsFormatsForSameLanguageWhenIdsAreMissing() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("en-mp3", null, Locale.ENGLISH, MediaFormat.MP3),
                        stream("en-m4a", null, Locale.ENGLISH, MediaFormat.M4A),
                        stream("de-mp3", null, Locale.GERMAN, MediaFormat.MP3)));
        assertEquals(2, filtered.size());
    }

    @Test
    public void filteringRemovesUnknownTrackWhenKnownIdExists() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("unknown", Locale.GERMAN),
                        stream("known", "en-track", Locale.ENGLISH, MediaFormat.MP3)));
        assertEquals(1, filtered.size());
        assertEquals("en-track", filtered.get(0).getAudioTrackId());
    }

    @Test
    public void filteringHandlesStreamsWithNoIdOrLanguage() {
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(stream("unknown-a", null), stream("unknown-b", null)));
        assertEquals(1, filtered.size());
    }

    @Test
    public void filteringKeepsDifferentTypesInSameLanguageWhenIdsAreMissing() {
        final AudioStream original = new AudioStream.Builder().setId("original")
                .setContent("", true).setMediaFormat(MediaFormat.MP3).setAverageBitrate(128)
                .setAudioLocale(Locale.ENGLISH).setAudioTrackType(AudioTrackType.ORIGINAL).build();
        final AudioStream descriptive = new AudioStream.Builder().setId("descriptive")
                .setContent("", true).setMediaFormat(MediaFormat.MP3).setAverageBitrate(128)
                .setAudioLocale(Locale.ENGLISH).setAudioTrackType(AudioTrackType.DESCRIPTIVE)
                .build();
        final List<AudioStream> filtered = ListHelper.getFilteredAudioStreams(context,
                List.of(original, descriptive));
        assertEquals("Different types of the same language must remain available", 2,
                filtered.size());
    }

}
