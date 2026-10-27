package org.example;

import org.junit.jupiter.api.Test;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AudioTest {
    @Test
    void testAudioSystemAvailable() {
        assertNotNull(AudioSystem.getAudioFileTypes());
    }
}
