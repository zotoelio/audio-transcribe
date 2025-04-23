package com.audio.transcribe;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.model.ApiKey;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.IOException;

/**
 * REST controller for handling audio transcription requests using OpenAI's Whisper model.
 */
@RestController
@RequestMapping("/api/transcribe")
public class TranscriptionController {

    /**
     * Thread-safe model instance to perform the transcription calls.
     * Initialized in the constructor below.
     */
    private final OpenAiAudioTranscriptionModel transcriptionModel;

    /**
     * No-args constructor manually wires up the OpenAI client + model.
     * • Reads the API key from an env var
     * • Wraps it in the ApiKey functional interface
     * • Builds a fully-configured OpenAiAudioApi
     * • Instantiates the OpenAiAudioTranscriptionModel
     */
    public TranscriptionController() {
        // 1) Fetch the key; fail fast if missing
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null) {
            throw new IllegalStateException("OPENAI_API_KEY is not set");
        }

        // 2) Simple lambda to satisfy the ApiKey interface
        ApiKey apiKey = () -> key;

        // 3) Build the low-level client with all required bits
        OpenAiAudioApi openAiAudioApi = new OpenAiAudioApi(
                "https://api.openai.com",                           // baseUrl
                apiKey,                                                     // our ApiKey impl
                new LinkedMultiValueMap<>(),                                // headers
                RestClient.builder(),                                       // RestClient.Builder
                WebClient.builder(),                                        // WebClient.Builder
                new DefaultResponseErrorHandler()                           // Error handler
        );

        // 4) Finally, create the transcription model
        this.transcriptionModel = new OpenAiAudioTranscriptionModel(openAiAudioApi);
    }

    /**
     * POST /api/transcribe
     * • Accepts a multipart “file”
     * • Saves it to a temp WAV file
     * • Calls Whisper via the transcriptionModel
     * • Deletes the temp file and returns the text
     */
    @PostMapping
    public ResponseEntity<String> transcribeAudio(@RequestParam("file") MultipartFile file) throws IOException {
        // Create a temp file on disk
        File tempFile = File.createTempFile("audio", ".wav");
        file.transferTo(tempFile); // write upload → temp file

        // Build our transcription options
        OpenAiAudioTranscriptionOptions transcriptionOptions = OpenAiAudioTranscriptionOptions.builder()
                .language("en")
                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .temperature(0f)
                .build();

        // Wrap the file for the prompt
        FileSystemResource audioFile = new FileSystemResource(tempFile);
        AudioTranscriptionPrompt transcriptionRequest = new AudioTranscriptionPrompt(audioFile, transcriptionOptions);
        // Execute the call
        AudioTranscriptionResponse response = transcriptionModel.call(transcriptionRequest);

        // Clean up
        tempFile.delete();
        // Respond 200 OK with just the text
        return new ResponseEntity<>(response.getResult().getOutput(), HttpStatus.OK);
    }
}