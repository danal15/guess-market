package server.servlet;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import server.GmServlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Receives an events file and hands its contents to the engine. Whoever
 * uploaded it becomes the market maker of every event in it.
 *
 * The file is never written to disk. The exercise warns that the machine this
 * runs on may not be allowed to write at all, so the content is read from the
 * request straight into the parser and only the parsed events are kept. The
 * threshold below is far larger than any market file, which keeps the container
 * from spilling the upload into a temporary file of its own on the way in.
 */
@WebServlet("/upload")
@MultipartConfig(
        fileSizeThreshold = 20 * 1024 * 1024,
        maxFileSize = 10 * 1024 * 1024,
        maxRequestSize = 10 * 1024 * 1024)
public class UploadServlet extends GmServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(response, () -> {
            String user = currentUser(request);
            Part filePart = firstPart(request);
            requireXmlName(nameOf(filePart));
            try (InputStream content = filePart.getInputStream()) {
                List<String> added = engine().uploadMarketFile(content, user);
                return new Loaded(nameOf(filePart), added);
            }
        });
    }

    private Part firstPart(HttpServletRequest request) throws Exception {
        for (Part part : request.getParts()) {
            if (part.getSubmittedFileName() != null) {
                return part;
            }
        }
        throw new IllegalArgumentException("No file arrived with the request.");
    }

    /**
     * Checked here as well as in the client, because the exercise asks for the
     * exercise 1 file checks to happen when the content reaches the server, and
     * because the servlets are meant to stand up on their own to a tool like
     * Postman.
     */
    private void requireXmlName(String fileName) {
        if (!fileName.toLowerCase().endsWith(".xml")) {
            throw new IllegalArgumentException("An events file has to end in .xml, and '"
                    + fileName + "' does not.");
        }
    }

    private String nameOf(Part part) {
        String submitted = part.getSubmittedFileName();
        return submitted == null ? "the file" : submitted;
    }

    /** What the client needs to tell the user what happened. */
    private static final class Loaded {
        private final String fileName;
        private final List<String> eventNames;

        Loaded(String fileName, List<String> eventNames) {
            this.fileName = fileName;
            this.eventNames = eventNames;
        }
    }
}
