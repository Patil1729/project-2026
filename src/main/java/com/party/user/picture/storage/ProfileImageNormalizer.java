package com.party.user.picture.storage;

import com.party.registration.common.exception.RegistrationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Locale;

/** Decode bounded image pixels and re-encode; filenames, MIME declarations and EXIF are not trusted. */
@Component
public class ProfileImageNormalizer {
    public record Image(byte[] bytes, int width, int height) { }
    public Image normalize(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) throw failure(HttpStatus.BAD_REQUEST, "PROFILE_PICTURE_REQUIRED", "Select a JPG or PNG picture.");
        if (file.getSize() > maxBytes) throw failure(HttpStatus.CONTENT_TOO_LARGE, "PROFILE_PICTURE_TOO_LARGE", "The picture exceeds the allowed file size.");
        ImageReader reader = null;
        try {
            byte[] input = file.getBytes();
            if (input.length > maxBytes) throw failure(HttpStatus.CONTENT_TOO_LARGE, "PROFILE_PICTURE_TOO_LARGE", "The picture exceeds the allowed file size.");
            try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw invalid();
                reader = readers.next();
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("jpg") && !format.equals("png"))
                    throw failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PROFILE_PICTURE_TYPE", "Use a JPG or PNG picture.");
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 12000 || height > 12000 || (long) width * height > 40000000)
                    throw failure(HttpStatus.BAD_REQUEST, "PROFILE_PICTURE_DIMENSIONS", "Choose an image of at most 40 megapixels and 12000 pixels per side.");
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + 1023) / 1024);
                params.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage decoded = reader.read(0, params);
                if (decoded == null) throw invalid();
                double ratio = Math.min(1, 512.0 / Math.max(decoded.getWidth(), decoded.getHeight()));
                int w = Math.max(1, (int) Math.round(decoded.getWidth() * ratio));
                int h = Math.max(1, (int) Math.round(decoded.getHeight() * ratio));
                var result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = result.createGraphics();
                try { graphics.setComposite(AlphaComposite.Src); graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC); graphics.drawImage(decoded, 0, 0, w, h, null); }
                finally { graphics.dispose(); decoded.flush(); }
                try (var output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(result, "png", output)) throw invalid();
                    return new Image(output.toByteArray(), w, h);
                } finally { result.flush(); }
            }
        } catch (IOException | IllegalArgumentException exception) { throw invalid(); }
        finally { if (reader != null) reader.dispose(); }
    }
    private RegistrationException invalid() { return failure(HttpStatus.BAD_REQUEST, "INVALID_PROFILE_PICTURE", "The file is not a readable JPG or PNG picture."); }
    private RegistrationException failure(HttpStatus status, String code, String message) { return new RegistrationException(status, code, message); }
}
