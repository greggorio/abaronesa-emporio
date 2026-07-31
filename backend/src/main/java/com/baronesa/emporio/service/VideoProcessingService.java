package com.baronesa.emporio.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    @Value("${ffmpeg.path:/usr/bin/ffmpeg}")
    private String ffmpegPath;

    @Value("${ffprobe.path:/usr/bin/ffprobe}")
    private String ffprobePath;

    @Value("${video.encoder:libx264}")
    private String preferredVideoEncoder;

    /**
     * Comprime um vídeo com fallback automático de encoder H.264.
     * Ordem: preferido (video.encoder) -> libx264 -> libopenh264 -> h264_nvenc -> h264_vaapi -> h264_qsv -> h264_videotoolbox
     */
    public VideoInfo compressVideo(Path inputPath, Path outputPath) throws IOException {
        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFprobe ffprobe = new FFprobe(ffprobePath);

        // Probe do vídeo original
        FFmpegProbeResult probe = ffprobe.probe(inputPath.toString());
        double duration = probe.getFormat().duration;

        // Verificar se o vídeo tem streams de áudio
        boolean hasAudio = probe.getStreams().stream()
                .anyMatch(stream -> "audio".equals(stream.codec_type.toString()));

        log.info("Comprimindo vídeo: {} (duração: {}s, áudio: {})", inputPath.getFileName(), duration, hasAudio);

        List<String> tryEncoders = new ArrayList<>();
        if (preferredVideoEncoder != null && !preferredVideoEncoder.isBlank()) {
            tryEncoders.add(preferredVideoEncoder.trim());
        }
        // Default candidates (mantém ordem e remove duplicados)
        List<String> defaults = List.of("libx264", "libopenh264", "h264_nvenc", "h264_vaapi", "h264_qsv", "h264_videotoolbox");
        Set<String> unique = new LinkedHashSet<>(tryEncoders);
        unique.addAll(defaults);
        tryEncoders = new ArrayList<>(unique);

        IOException lastError = null;
        for (String encoder : tryEncoders) {
            try {
                log.info("Tentando encoder de vídeo: {}", encoder);
                FFmpegBuilder builder = buildCompression(inputPath, outputPath, encoder, hasAudio);
                FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
                executor.createJob(builder).run();

                // Sucesso
                long originalSize = Files.size(inputPath);
                long compressedSize = Files.size(outputPath);
                double compressionRatio = (1.0 - ((double) compressedSize / originalSize)) * 100.0;
                double rounded = Math.round(compressionRatio * 10.0) / 10.0;
                log.info("Encoder escolhido: {}. Vídeo comprimido: {} -> {} (redução de {}%)",
                        encoder, formatBytes(originalSize), formatBytes(compressedSize), String.format("%.1f", rounded));
                return new VideoInfo(duration, compressedSize);
            } catch (RuntimeException ex) {
                String msg = ex.getMessage();
                log.warn("Falha ao comprimir com {}: {}", encoder, msg);
                lastError = new IOException(msg, ex);
                // Remove eventual arquivo parcial gerado
                try { Files.deleteIfExists(outputPath); } catch (Exception ignored) {}
            }
        }

        // Nenhum encoder funcionou
        String errmsg = "Nenhum encoder H.264 disponível (tentados: " + String.join(", ", tryEncoders) +
                "). Instale o ffmpeg com suporte a libx264 ou configure 'video.encoder'.";
        log.error(errmsg);
        if (lastError != null) {
            throw new IOException(errmsg, lastError);
        } else {
            throw new IOException(errmsg);
        }
    }

    private FFmpegBuilder buildCompression(Path inputPath, Path outputPath, String encoder, boolean hasAudio) {
        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(inputPath.toString())
                .overrideOutputFiles(true);

        // Construir o output e adicionar extras antes do done()
        // Filtro de escala que garante dimensões pares (divisíveis por 2) para H.264
        String videoFilter = "scale='min(1280,iw)':'min(720,ih)':force_original_aspect_ratio=decrease," +
                             "scale='trunc(iw/2)*2':'trunc(ih/2)*2'";

        var output = builder.addOutput(outputPath.toString())
                .setVideoCodec(encoder)
                .setVideoFilter(videoFilter)
                .setVideoFrameRate(30)
                .setFormat("mp4");

        // Processar áudio apenas se existir
        if (hasAudio) {
            output.setAudioCodec("aac")
                  .setAudioBitRate(128_000)
                  .setAudioSampleRate(44100)
                  .setAudioChannels(2);
        }

        // Extra flags comuns
        List<String> extra = new ArrayList<>();
        extra.add("-maxrate"); extra.add("2M");
        extra.add("-bufsize"); extra.add("4M");
        extra.add("-movflags"); extra.add("+faststart");
        extra.add("-pix_fmt"); extra.add("yuv420p");

        // CRF e preset apenas para libx264 (os outros encoders podem não suportar)
        if ("libx264".equalsIgnoreCase(encoder)) {
            extra.add("-crf"); extra.add("23");
            extra.add("-preset"); extra.add("medium");
        }

        if (!extra.isEmpty()) {
            output.addExtraArgs(extra.toArray(new String[0]));
        }

        output.done();
        return builder;
    }

    /**
     * Gera um thumbnail (JPEG) do vídeo na posição informada (segundos).
     */
    public void generateThumbnail(Path inputPath, Path outputImagePath, double atSecond) throws IOException {
        FFmpeg ffmpeg = new FFmpeg(ffmpegPath);
        FFprobe ffprobe = new FFprobe(ffprobePath);

        log.info("Gerando thumbnail de vídeo: {} @ {}s", inputPath.getFileName(), atSecond);

        FFmpegBuilder builder = new FFmpegBuilder()
                .setInput(inputPath.toString())
                .overrideOutputFiles(true)
                .addExtraArgs("-ss", String.valueOf(atSecond))
                .addOutput(outputImagePath.toString())
                    .setFrames(1)
                    .setVideoFilter("scale='min(1280,iw)':'-2':force_original_aspect_ratio=decrease")
                    .setFormat("mjpeg")
                .done();

        FFmpegExecutor executor = new FFmpegExecutor(ffmpeg, ffprobe);
        executor.createJob(builder).run();
    }

    /**
     * Obtém informações de um vídeo
     */
    public VideoInfo getVideoInfo(Path videoPath) throws IOException {
        FFprobe ffprobe = new FFprobe(ffprobePath);
        FFmpegProbeResult probe = ffprobe.probe(videoPath.toString());

        double duration = probe.getFormat().duration;
        long size = Files.size(videoPath);

        return new VideoInfo(duration, size);
    }

    /**
     * Valida se um vídeo atende os requisitos
     * - Duração máxima: 90 segundos
     * - Tamanho máximo antes da compressão: 100 MB
     */
    public void validateVideo(Path videoPath) throws IOException {
        VideoInfo info = getVideoInfo(videoPath);

        // Validar duração
        if (info.durationSeconds() > 90) {
            throw new IllegalArgumentException(
                String.format("Vídeo muito longo: %.1fs (máximo 90s)", info.durationSeconds())
            );
        }

        // Validar tamanho
        long maxSize = 100 * 1024 * 1024; // 100 MB
        if (info.sizeBytes() > maxSize) {
            throw new IllegalArgumentException(
                String.format("Vídeo muito grande: %s (máximo 100 MB)", formatBytes(info.sizeBytes()))
            );
        }

        log.info("Vídeo validado: duração={}s, tamanho={}",
                String.format("%.1f", info.durationSeconds()),
                formatBytes(info.sizeBytes()));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    public record VideoInfo(double durationSeconds, long sizeBytes) {}
}
